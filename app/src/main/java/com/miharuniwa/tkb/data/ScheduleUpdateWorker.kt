package com.miharuniwa.tkb.data

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.miharuniwa.tkb.MainActivity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ScheduleUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appPreferences = AppPreferences(applicationContext)
        val isEnabled = appPreferences.notificationEnabled.first()
        val rootUrl = appPreferences.rootUrl.first()

        if (!isEnabled || rootUrl.isNullOrEmpty()) {
            return Result.success()
        }

        // (Đã xóa đoạn chặn Thứ 6, Thứ 7 để Worker có thể check mỗi ngày)

        val db = AppDatabase.getInstance(applicationContext)

        try {
            val dao = db.scheduleDao()
            val alarmDao = db.alarmDao()
            val gradeDao = db.gradeDao()
            val repository = ScheduleRepository(db.scheduleDao(), db.alarmDao(), db.gradeDao(), db.formDao())

            val cachedWeeks = repository.getCachedWeeks()
            val cachedIds = cachedWeeks.map { it.id }.toSet()

            val fetchedWeeks = repository.fetchAndCacheWeeks(rootUrl)
            
            val allCachedWeeks = repository.getCachedWeeks()
            val unnotifiedWeeks = allCachedWeeks.filter { !it.isNotified }
            val followedKeys = appPreferences.followedSystems.first()

            // 1. Thông báo nếu có tuần mới hoàn toàn (thông báo cập nhật chung)
            if (unnotifiedWeeks.isNotEmpty()) {
                unnotifiedWeeks.forEach { week ->
                    try {
                        repository.fetchAndCacheDetails(week.id, week.link)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    showNotification(
                        context = applicationContext,
                        title = "Có thời khóa biểu mới! 📅",
                        labelText = week.title,
                        notificationId = week.title.hashCode()
                    )
                    
                    try {
                        repository.markWeekAsNotified(week.id)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 2. Đồng bộ chi tiết cho tất cả fetchedWeeks (để đảm bảo không bị thiếu details cho tuần tiếp theo)
            fetchedWeeks.forEach { week ->
                if (week.id in unnotifiedWeeks.map { it.id }) return@forEach
                try {
                    repository.fetchAndCacheDetails(week.id, week.link)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Tự động cập nhật tuần mới cho các báo thức cũ phiên bản tuần
            val targetWeek = ScheduleUtils.getTargetWeek(fetchedWeeks)
            val targetWeekId = targetWeek?.id ?: ""
            if (targetWeekId.isNotEmpty()) {
                val allAlarms = alarmDao.getAllAlarms()
                val alarmsToUpdate = allAlarms.filter { it.isEnabled && it.weekId != targetWeekId }
                
                if (alarmsToUpdate.isNotEmpty()) {
                    val apiKey = appPreferences.geminiApiKey.first()
                    val modelName = appPreferences.geminiModel.first()
                    
                    if (!apiKey.isNullOrEmpty() && !modelName.isNullOrEmpty()) {
                        val groupedAlarms = alarmsToUpdate.groupBy { it.systemKey }
                        val targetDetails = repository.getCachedDetails(targetWeekId)
                        val detailsMap = targetDetails.associateBy { "${it.base}|${it.systemType}" }
                        
                        var updatedAny = false
                        
                        for ((systemKey, alarmsInGroup) in groupedAlarms) {
                            val detail = detailsMap[systemKey]
                            if (detail != null) {
                                try {
                                    val trackedClasses = alarmsInGroup.map { it.className }.distinct()
                                    val jsonResult = repository.parsePdfWithGemini(
                                        context = applicationContext,
                                        fileId = detail.fileId,
                                        apiKey = apiKey,
                                        modelName = modelName,
                                        trackedClasses = trackedClasses
                                    )
                                    
                                    if (!jsonResult.isNullOrEmpty()) {
                                        for (alarm in alarmsInGroup) {
                                            val newItems = if (!jsonResult.isNullOrEmpty()) ScheduleUtils.parseScheduleJson(jsonResult) else emptyList()
                                            val oldJson = alarm.scheduleJson
                                            val mergedJson = if (oldJson.isNotEmpty() && targetWeek != null) {
                                                val merged = ScheduleUtils.mergeSchedules(oldJson, newItems, fetchedWeeks, targetWeek)
                                                if (merged == "[]" || merged.isEmpty()) jsonResult ?: "" else merged
                                            } else {
                                                jsonResult ?: ""
                                            }
                                            
                                            alarmDao.insertAlarm(alarm.copy(
                                                scheduleJson = mergedJson,
                                                weekId = targetWeekId
                                            ))
                                        }
                                        updatedAny = true
                                        
                                        val classesStr = trackedClasses.joinToString(", ")
                                        showNotification(
                                            context = applicationContext,
                                            title = "Đã cập nhật TKB tuần mới! 📅",
                                            labelText = "Lớp: $classesStr (${targetWeek?.title ?: ""})",
                                            notificationId = systemKey.hashCode() + targetWeekId.hashCode()
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        
                        if (updatedAny) {
                            val updatedAlarms = alarmDao.getAllAlarms()
                            com.miharuniwa.tkb.data.AlarmScheduler(applicationContext).scheduleAllAlarms(updatedAlarms)
                            com.miharuniwa.tkb.widget.ScheduleWidgetUpdater.updateAllWidgets(applicationContext)
                        }
                    }
                }
            }

            // 4. Kiểm tra thay đổi nội bộ của các tuần hiện tại (bảo trì / sửa đổi của giáo viên)
            fetchedWeeks.forEach { week ->
                try {
                    val dbDetails = repository.getCachedDetails(week.id)
                    val dbDetailsMap = dbDetails.associateBy { "${it.base}|${it.systemType}" }

                    if (dbDetailsMap.isNotEmpty()) {
                        val liveDetails = repository.fetchAndCacheDetails(week.id, week.link)
                        liveDetails.forEach { liveItem ->
                            val key = "${liveItem.base}|${liveItem.systemType}"
                            val cachedItem = dbDetailsMap[key]
                            if (cachedItem != null) {
                                // Nếu thay đổi File ID hoặc Drive Link -> Lịch học hệ này vừa được giáo viên cập nhật lại
                                if (liveItem.fileId != cachedItem.fileId || liveItem.driveLink != cachedItem.driveLink) {
                                    if (followedKeys.contains(key)) {
                                        val subLabel = if (liveItem.base == "CS1") "Cơ sở 1" else "Cơ sở 2"
                                        showNotification(
                                            context = applicationContext,
                                            title = "Thay đổi lịch hệ ${liveItem.systemType} - $subLabel 📅",
                                            labelText = "${week.title} vừa được giáo viên cập nhật/sửa đổi!",
                                            notificationId = key.hashCode() + week.id.hashCode()
                                        )
                                        
                                        val allAlarms = alarmDao.getAllAlarms()
                                        val affectedAlarms = allAlarms.filter { it.systemKey == key && it.isEnabled }
                                        
                                        if (affectedAlarms.isNotEmpty()) {
                                            val apiKey = appPreferences.geminiApiKey.first()
                                            val modelName = appPreferences.geminiModel.first()
                                            if (!apiKey.isNullOrEmpty() && !modelName.isNullOrEmpty()) {
                                                try {
                                                    val trackedClasses = affectedAlarms.map { it.className }.distinct()
                                                    val jsonResult = repository.parsePdfWithGemini(
                                                        context = applicationContext,
                                                        fileId = liveItem.fileId,
                                                        apiKey = apiKey,
                                                        modelName = modelName,
                                                        trackedClasses = trackedClasses
                                                    )
                                                    
                                                    if (!jsonResult.isNullOrEmpty()) {
                                                        for (alarm in affectedAlarms) {
                                                            val newItems = if (!jsonResult.isNullOrEmpty()) ScheduleUtils.parseScheduleJson(jsonResult) else emptyList()
                                                            val oldJson = alarm.scheduleJson
                                                            val targetWeekObj = fetchedWeeks.find { it.id == week.id }
                                                            val mergedJson = if (oldJson.isNotEmpty() && targetWeekObj != null) {
                                                                val merged = ScheduleUtils.mergeSchedules(oldJson, newItems, fetchedWeeks, targetWeekObj)
                                                                if (merged == "[]" || merged.isEmpty()) jsonResult ?: "" else merged
                                                            } else {
                                                                jsonResult ?: ""
                                                            }
                                                            
                                                            alarmDao.insertAlarm(alarm.copy(
                                                                scheduleJson = mergedJson,
                                                                weekId = week.id
                                                            ))
                                                        }
                                                        
                                                        val updatedAlarms = alarmDao.getAllAlarms()
                                                        com.miharuniwa.tkb.data.AlarmScheduler(applicationContext).scheduleAllAlarms(updatedAlarms)
                                                        com.miharuniwa.tkb.widget.ScheduleWidgetUpdater.updateAllWidgets(applicationContext)
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }

        return Result.success()
    }

    private fun showNotification(context: Context, title: String, labelText: String, notificationId: Int) {
        val channelId = "tkb_updates_channel"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Thông báo thời khóa biểu mới",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo khi có thời khóa biểu tuần mới hoặc thay đổi hệ học"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(labelText)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }

    companion object {
        private const val WORK_NAME = "ScheduleUpdateWork"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<ScheduleUpdateWorker>(
                20, TimeUnit.MINUTES // Chạy mỗi 20 phút để phát hiện cập nhật nhanh nhất có thể
            )
            .setConstraints(constraints)
            .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun enqueueOneTimeWork(context: Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<ScheduleUpdateWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }

        fun cancelPeriodicWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
