package com.example.fn_app.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.fn_app.FnDogApp
import com.example.fn_app.di.AppContainer
import com.fnassistantdog.core.error.EpicException
import java.util.concurrent.TimeUnit

/**
 * 每日商城后台下载。失败分类：网络/服务端类可重试（WorkManager 退避），其余直接失败。
 * UI 侧的"打开 App 补偿刷新"由 TodayShopViewModel 的首帧 load 负责（缓存缺失即打网络）。
 */
class ShopRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        container().shopRepository.download()
        Result.success()
    } catch (failure: EpicException) {
        Log.w(TAG, "商城后台下载失败（第 ${runAttemptCount + 1} 次）：${failure.message}")
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    } catch (failure: Exception) {
        Log.e(TAG, "商城后台下载异常", failure)
        Result.failure()
    }

    private fun container(): AppContainer = (applicationContext as FnDogApp).container

    companion object {
        const val UNIQUE_NAME = "fn-shop-daily"
        const val TAG = "ShopRefresh"
        const val MAX_ATTEMPTS = 2

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ShopRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
