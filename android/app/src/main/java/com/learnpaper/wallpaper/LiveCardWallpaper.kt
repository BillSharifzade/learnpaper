package com.learnpaper.wallpaper

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.learnpaper.Graph
import com.learnpaper.content.Word
import com.learnpaper.data.Settings
import com.learnpaper.i18n.AppLanguage
import com.learnpaper.i18n.AppLocale
import com.learnpaper.render.CardRenderer
import com.learnpaper.render.CardStyle
import com.learnpaper.render.Palette
import com.learnpaper.render.Palettes
import com.learnpaper.render.cardStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The wallpaper. LearnPaper draws the card on its own surface instead of handing Android a new picture.
 *
 * Setting a picture (WallpaperManager.setBitmap) makes the system compress and store it, extract colours
 * from it, re-theme itself and tell every app that the wallpaper changed; on many phones the home screen
 * reloads and old ones heat up. Redrawing our own surface does none of that (docs/DESIGN.md §3). To keep
 * it that way the engine:
 *  - reports the colours of the palette the user picked, answers at once when asked and speaks up only when
 *    that choice changes, so a new word (or a rotating colour) never re-themes the phone;
 *  - draws straight onto the surface with the GPU canvas and keeps nothing screen-sized in memory: the
 *    surface holds the picture, the engine only a laid-out card (text layouts and one small illustration);
 *  - runs no timer while it is not visible: words change while the wallpaper is on screen
 *    ([WallpaperChanger.followSchedule]), so a phone in a pocket does no work at all.
 */
class LiveCardWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = CardEngine()

    private inner class CardEngine : Engine() {
        private val graph = Graph.get(applicationContext)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        /** WallpaperManager.FLAG_* this engine draws for; 0 when the system does not say (before API 34). */
        private val flags = MutableStateFlow(0)
        private var scene: Scene? = null
        private var card: CardRenderer.Card? = null
        private var cardScene: Scene? = null
        /** What the surface shows now; null when it has to be drawn again. */
        private var onScreen: Scene? = null
        private var width = 0
        private var height = 0
        private var software = false
        private var reported: Palette? = null
        private var colorsUnknownToSystem = false
        private var layoutJob: Job? = null
        private var scheduleJob: Job? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) flags.value = wallpaperFlags
            scenes()
                .onEach { s ->
                    scene = s
                    report(s.reported)
                    relayout()
                }
                .launchIn(scope)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width == this.width && height == this.height) return
            this.width = width
            this.height = height
            onScreen = null
            relayout()
        }

        /**
         * The system shows the surface as soon as this returns, so the card is drawn before. It is normally
         * laid out already (that happens whenever the word changes); only the first frame of a new engine may
         * have to wait for the words to load.
         */
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            onScreen = null
            if (!cardIsCurrent()) layoutNow()
            if (!draw()) drawPlain()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            onScreen = null
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                // A card laid out while the screen was off is drawn now; one still being laid out draws itself.
                if (onScreen != scene && cardIsCurrent()) draw()
                if (!isPreview && scheduleJob?.isActive != true) {
                    scheduleJob = scope.launch(Dispatchers.Default) { graph.changer.followSchedule() }
                }
            } else {
                scheduleJob?.cancel()
                scheduleJob = null
            }
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            flags.value = which
        }

        override fun onComputeColors(): WallpaperColors? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
            // Asked right after the engine attaches, usually before our flows have delivered anything.
            val p = reported
                ?: runBlocking { withTimeoutOrNull(COLORS_TIMEOUT_MS) { Palettes.reported(graph.settings.current()) } }?.also { reported = it }
            if (p == null) {
                colorsUnknownToSystem = true
                return null
            }
            colorsUnknownToSystem = false
            val primary = Color.valueOf(p.accent)
            val secondary = Color.valueOf(p.bg)
            val tertiary = Color.valueOf(p.text)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hints = if (p.isDark) WallpaperColors.HINT_SUPPORTS_DARK_THEME else WallpaperColors.HINT_SUPPORTS_DARK_TEXT
                WallpaperColors(primary, secondary, tertiary, hints)
            } else {
                WallpaperColors(primary, secondary, tertiary)
            }
        }

        override fun onDestroy() {
            scope.cancel()
            card?.release()
            card = null
            super.onDestroy()
        }

        /** Tells the system about new colours only when the user picked another palette. */
        private fun report(palette: Palette) {
            val before = reported
            reported = palette
            val changed = before != null && before.id != palette.id
            if ((changed || colorsUnknownToSystem) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
        }

        private fun scenes(): Flow<Scene> = combine(
            graph.settings.flow,
            graph.progress.flow.map { it.currentId to it.paletteIndex }.distinctUntilChanged(),
            graph.content.generation,
            AppLocale.language,
            flags,
        ) { settings, (wordId, paletteIndex), _, lang, which -> scene(settings, wordId, paletteIndex, lang, which) }
            .filterNotNull()
            .distinctUntilChanged()
            // Loading words happens off the main thread, so a blocked first frame can never wait on it.
            .flowOn(Dispatchers.Default)

        private suspend fun scene(settings: Settings, wordId: String?, paletteIndex: Int, lang: AppLanguage, which: Int): Scene? {
            val word = wordId?.let { graph.content.word(it) }
                ?: graph.content.words().let { all -> all.firstOrNull { it.level in settings.levels } ?: all.firstOrNull() }
                ?: return null
            // The lock screen keeps the clock zone clear. Before API 34 one engine may serve both screens
            // without telling us, so the safe layout is used there too.
            val lock = which == 0 || which and WallpaperManager.FLAG_LOCK != 0
            val style = settings.cardStyle(if (lock) settings.lockLayout() else settings.layout)
            return Scene(word, style, Palettes.forSettings(settings, paletteIndex), Palettes.reported(settings), lang)
        }

        private fun cardIsCurrent(): Boolean {
            val c = card ?: return false
            return cardScene == scene && c.width == width && c.height == height
        }

        /** Lays the card out in the background whenever what it shows (or the surface size) changes. */
        private fun relayout() {
            val s = scene ?: return
            if (width <= 0 || height <= 0) return
            if (cardIsCurrent()) {
                if (isVisible && onScreen != s) draw()
                return
            }
            layoutJob?.cancel()
            val w = width
            val h = height
            layoutJob = scope.launch {
                val next = withContext(Dispatchers.Default) { graph.renderer.layout(s.word, s.style, s.palette, w, h) }
                if (s != scene || w != width || h != height) {
                    next.release()
                    return@launch
                }
                card?.release()
                card = next
                cardScene = s
                if (isVisible) draw()
            }
        }

        /** The first frame: lays out on this thread, waiting for settings and words if they are not loaded yet. */
        private fun layoutNow() {
            if (width <= 0 || height <= 0) return
            val s = scene
                ?: runBlocking {
                    withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) {
                        val progress = graph.progress.current()
                        scene(graph.settings.current(), progress.currentId, progress.paletteIndex, AppLocale.current(applicationContext), flags.value)
                    }
                }
                ?: return
            layoutJob?.cancel()
            card?.release()
            card = graph.renderer.layout(s.word, s.style, s.palette, width, height)
            cardScene = s
            scene = s
        }

        /** Draws the laid-out card; false when there is none for this size or the surface is gone. */
        private fun draw(): Boolean {
            val c = card ?: return false
            if (c.width != width || c.height != height) return false
            val holder = surfaceHolder
            val canvas = lockCanvas(holder) ?: return false
            try {
                c.draw(canvas)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
            onScreen = cardScene
            return true
        }

        /** Background only, for the rare first frame whose card could not be prepared in time. */
        private fun drawPlain() {
            val holder = surfaceHolder
            val canvas = lockCanvas(holder) ?: return
            try {
                canvas.drawColor(reported?.bg ?: Palettes.all.first().bg)
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }

        /**
         * The GPU canvas where it works (the platform's own image wallpaper draws the same way); a software
         * canvas on the rare driver that refuses it. Either way the card is drawn once per change.
         */
        private fun lockCanvas(holder: SurfaceHolder): Canvas? {
            if (!holder.surface.isValid) return null
            if (!software) {
                val hw = runCatching { holder.lockHardwareCanvas() }.getOrNull()
                if (hw != null) return hw
                software = true
            }
            return runCatching { holder.lockCanvas() }.getOrNull()
        }
    }

    private data class Scene(
        val word: Word,
        val style: CardStyle,
        val palette: Palette,
        /** The palette whose colours the system is told about; see [Palettes.reported]. */
        val reported: Palette,
        val lang: AppLanguage,
    )

    companion object {
        private const val FIRST_FRAME_TIMEOUT_MS = 2_500L
        private const val COLORS_TIMEOUT_MS = 1_000L

        fun component(context: Context) = ComponentName(context, LiveCardWallpaper::class.java)

        /** True when our service is the current home or lock wallpaper. Binder calls: keep off the main thread. */
        fun isActive(context: Context): Boolean {
            val wm = WallpaperManager.getInstance(context)
            val ours = component(context)
            val home = runCatching { wm.wallpaperInfo?.component }.getOrNull()
            val lock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching { wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.component }.getOrNull()
            } else {
                null
            }
            return home == ours || lock == ours
        }

        /** The system screen where the user confirms our wallpaper. */
        fun pickerIntent(context: Context): Intent =
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** The list of live wallpapers, for launchers that do not offer the direct screen. */
        fun chooserIntent(): Intent =
            Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
