package com.example.fn_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.example.fn_app.di.LocalContainer
import com.example.fn_app.ui.nav.FnNavHost
import com.example.fn_app.ui.theme.FnDogTheme

/** 单 Activity 架构（复刻原 App）：全部页面为 Compose 路由。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FnDogApp).container
        enableEdgeToEdge()
        setContent {
            FnDogTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    FnNavHost()
                }
            }
        }
    }
}
