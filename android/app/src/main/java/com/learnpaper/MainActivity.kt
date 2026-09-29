package com.learnpaper

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.learnpaper.i18n.AppLocale
import com.learnpaper.ui.AppViewModel
import com.learnpaper.ui.LearnPaperApp
import com.learnpaper.ui.LocalizedContent

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrapForActivity(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The branded splash stays until settings and words are loaded, so the first frame is final.
        installSplashScreen().setKeepOnScreenCondition { vm.state.value.loading }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LocalizedContent { LearnPaperApp(vm) } }
    }
}
