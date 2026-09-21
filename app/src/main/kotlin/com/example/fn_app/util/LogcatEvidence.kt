package com.example.fn_app.util

import android.util.Log
import com.fnassistantdog.core.error.Redactor
import com.fnassistantdog.core.http.HttpEvidence

/**
 * HTTP 证据写 logcat（仅 debug 构建装配，见 AppContainer）。
 * 落日志前一律过 Redactor，与原 App 的承诺一致。
 */
class LogcatEvidence : HttpEvidence {
    override fun log(line: String) {
        Log.d(TAG, Redactor.redact(line).take(MAX_LINE))
    }

    private companion object {
        const val TAG = "FnDogHttp"
        const val MAX_LINE = 3_000
    }
}
