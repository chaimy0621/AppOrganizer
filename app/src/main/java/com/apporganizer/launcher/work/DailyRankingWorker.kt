package com.apporganizer.launcher.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apporganizer.launcher.App
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** 每天一次：同步应用 → 写入使用记录 → 重新计算常用排序（若开启自动调整）。 */
class DailyRankingWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val repo = (applicationContext as App).container.repository
        repo.syncInstalledApps()
        repo.ingestUsage()
        if (repo.settings.current().autoAdjust) repo.recomputeScores()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}

object DailyWorkScheduler {
    private const val NAME = "daily_ranking"

    /** 首次在本地时间凌晨 4 点左右运行，之后每 24 小时一次。 */
    fun schedule(context: Context) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(4, 0), zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()

        val request = PeriodicWorkRequestBuilder<DailyRankingWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
