package com.example.fn_app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import com.example.fn_app.di.AppContainer
import com.example.fn_app.work.ShopRefreshWorker

/**
 * 显式实现 Configuration.Provider：WorkManager 2.6+ 走"按需初始化"，
 * 光靠默认 provider 在部分环境（如 JVM 侧 Robolectric）不会自动完成初始化。
 */
open class FnDogApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = buildContainer(this)
        ShopRefreshWorker.schedule(this)
    }

    /** Robolectric 冒烟测试通过子类替换依赖图（见 app/src/test/.../TestFnDogApp.kt） */
    open fun buildContainer(context: Context): AppContainer = AppContainer(context)
}
