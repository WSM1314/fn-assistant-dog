package com.example.fn_app

import android.os.Looper
import androidx.compose.ui.test.ComposeTimeoutException
import org.robolectric.Shadows.shadowOf

/**
 * Robolectric 的主线程 looper 是 PAUSED 的：后台线程（OkHttp / Room / DataStore / 协程 Main 派发）
 * 回到主线程后要显式 idle() 才会执行。Compose 的 waitUntil 不保证泵它，所以这里自己泵。
 *
 * 超时时把"已收到的请求列表"塞进异常信息，方便一次定位是网络没到 Mock 服务器还是渲染没跟上。
 */
inline fun pumpUntil(
    timeoutMillis: Long = 30_000,
    diagnose: () -> String = { "" },
    condition: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        if (System.currentTimeMillis() > deadline) {
            throw ComposeTimeoutException(
                "条件在 ${timeoutMillis}ms 内未满足。诊断：${diagnose()}",
            )
        }
        Thread.sleep(10)
    }
}
