package com.learnpaper.wallpaper

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.learnpaper.Graph
import com.learnpaper.content.Word
import com.learnpaper.data.LayoutPreset
import com.learnpaper.data.Settings
import com.learnpaper.render.Palette
import com.learnpaper.render.Palettes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

/**
 * Draws the current card as a live wallpaper. A new word is just a redraw of our surface: there is
 * no wallpaper-changed broadcast and no colour extraction, so the launcher and the system theme are
 * left alone. [onComputeColors] reports the palette colours, which only change with the palette.
 */
class LiveCardWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = CardEngine()

    private inner class CardEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val graph = Graph.get(applicationContext)
        private val size = MutableStateFlow(0 to 0)
        /** WallpaperManager.FLAG_* this engine draws for; 0 until the system tells us (API 34+). */
        private val flags = MutableStateFlow(0)
        private var bitmap: Bitmap? = null
        private var palette: Palette? = null
        private var dirty = false

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            combine(graph.settings.flow, graph.progress.flow, graph.content.generation, size, flags) { s, p, _, sz, f ->
                Frame(s, p.currentId, Palettes.forSettings(s, p.paletteIndex), sz.first, sz.second, f)
            }
                .distinctUntilChanged()
                .mapNotNull { f -> if (f.width > 0 && f.height > 0) f else null }
                .map { f -> f to withContext(Dispatchers.Default) { render(f) } }
                .onEach { (f, rendered) ->
                    bitmap?.recycle()
                    bitmap = rendered
                    if (palette?.id != f.palette.id) {
                        palette = f.palette
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
                    }
                    dirty = true
                    if (isVisible) draw()
                }
                .launchIn(scope)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            size.value = width to height
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible && dirty) draw()
        }

        override fun onWallpaperFlagsChanged(which: Int) {
            flags.value = which
        }

        override fun onComputeColors(): WallpaperColors? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
            val p = palette ?: return null
            val primary = Color.valueOf(p.accent)
            val secondary = Color.valueOf(p.bg)
            val tertiary = Color.valueOf(p.text)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                WallpaperColors(primary, secondary, tertiary, WallpaperColors.HINT_SUPPORTS_DARK_TEXT)
            } else {
                WallpaperColors(primary, secondary, tertiary)
            }
        }

        override fun onDestroy() {
            scope.cancel()
            bitmap?.recycle()
            bitmap = null
            super.onDestroy()
        }

        private suspend fun render(f: Frame): Bitmap? {
            val words = graph.content.words()
            val word: Word = f.wordId?.let { id -> words.firstOrNull { it.id == id } }
                ?: words.firstOrNull { it.level in f.settings.levels }
                ?: words.firstOrNull()
                ?: return null
            // The lock screen keeps the clock zone clear. Before API 34 one engine may serve both
            // screens without telling us, so the safe layout is used there too.
            val lock = f.flags == 0 || f.flags and WallpaperManager.FLAG_LOCK != 0
            val settings = if (lock) f.settings.copy(layout = LayoutPreset.LOCK) else f.settings
            return graph.renderer.render(word, settings, f.palette, f.width, f.height)
        }

        private fun draw() {
            val b = bitmap ?: return
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                canvas.drawColor(palette?.bg ?: Color.WHITE)
                canvas.drawBitmap(b, 0f, 0f, null)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            dirty = false
        }
    }

    private data class Frame(
        val settings: Settings,
        val wordId: String?,
        val palette: Palette,
        val width: Int,
        val height: Int,
        val flags: Int,
    )

    companion object {
        fun component(context: Context) = ComponentName(context, LiveCardWallpaper::class.java)

        /** True when our service is the current home or lock wallpaper. */
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

        /** Intent for the system screen where the user confirms our live wallpaper. */
        fun pickerIntent(context: Context): Intent =
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
