package com.example.goldnotifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.goldnotifier.GoldNotifierApplication

/*
模块名: GoldSyncWorker
功能概述: 为后续低频补偿刷新预留 WorkManager 入口。
对外接口: doWork
依赖关系: WorkManager、GoldRepository
输入输出: 输入后台任务请求，输出一次金价刷新结果。
异常与错误: 无网络且无缓存时返回 retry，便于系统后续重试。
维护说明: MVP 实时通知不依赖 WorkManager，前台服务负责 3 秒刷新。
*/
class GoldSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? GoldNotifierApplication ?: return Result.failure()
        val result = app.appContainer.goldRepository.refreshGoldPrice()
        return if (result.price != null) Result.success() else Result.retry()
    }
}
