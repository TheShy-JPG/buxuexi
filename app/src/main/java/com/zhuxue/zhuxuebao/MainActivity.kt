package com.zhuxue.zhuxuebao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhuxue.zhuxuebao.data.AppSettings
import com.zhuxue.zhuxuebao.ui.ZhuxueApp
import com.zhuxue.zhuxuebao.ui.theme.ZhuxueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ZhuxueApplication).container
        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            ZhuxueTheme(
                themeMode = settings.themeMode,
                customBackground = settings.customBackground,
                customFont = settings.customFont
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ZhuxueApp(container)
                }
            }
        }
    }
}