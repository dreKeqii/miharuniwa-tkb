package com.miharuniwa.tkb.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WidgetDailyUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            ScheduleWidgetUpdater.updateAllWidgets(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Lập lịch cho ngày tiếp theo
        enqueueDailyUpdate(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "WidgetDailyUpdateWork"

        fun enqueueDailyUpdate(context: Context) {
            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis

            // Lập lịch chạy lúc 00:01 sáng hôm sau
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 1)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val delay = calendar.timeInMillis - now

            val workRequest = OneTimeWorkRequestBuilder<WidgetDailyUpdateWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
