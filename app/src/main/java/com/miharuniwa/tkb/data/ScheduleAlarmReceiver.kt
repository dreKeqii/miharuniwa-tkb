package com.miharuniwa.tkb.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.miharuniwa.tkb.MainActivity
import com.miharuniwa.tkb.data.AppDatabase
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
        val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE)
        
        if (alarmId == null || alarmType == null) {
            // Check if it's BOOT_COMPLETED
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                rescheduleAlarms(context)
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            
            val alarm = db.alarmDao().getAlarmById(alarmId)
            if (alarm == null || !alarm.isEnabled) return@launch

            val scheduleJson = alarm.scheduleJson
            val className = alarm.className
            if (scheduleJson.isEmpty() || className.isEmpty()) return@launch

            val cleanClassName = ScheduleUtils.cleanClassName(className)
            val cal = Calendar.getInstance()
            val todayStr = ScheduleUtils.getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK))
            val todayDateStr = String.format("%02d/%02d/%04d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
            
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrowStr = ScheduleUtils.getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK))
            val tomorrowDateStr = String.format("%02d/%02d/%04d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
            
            val scheduleItems = ScheduleUtils.parseScheduleJson(scheduleJson)
            val classItems = scheduleItems.filter { ScheduleUtils.cleanClassName(it.className) == cleanClassName }
            
            val classesToday = classItems.filter { item -> 
                if (item.date.isNotEmpty()) {
                    try {
                        val parsedDate = java.time.LocalDate.parse(item.date, java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"))
                        parsedDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) == todayDateStr
                    } catch (e: Exception) {
                        item.dayOfWeek == todayStr
                    }
                } else {
                    item.dayOfWeek == todayStr
                }
            }
            
            val classesTomorrow = classItems.filter { item -> 
                if (item.date.isNotEmpty()) {
                    try {
                        val parsedDate = java.time.LocalDate.parse(item.date, java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"))
                        parsedDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) == tomorrowDateStr
                    } catch (e: Exception) {
                        item.dayOfWeek == tomorrowStr
                    }
                } else {
                    item.dayOfWeek == tomorrowStr
                }
            }

            val notifBaseId = Math.abs(alarmId.hashCode()) % 100000

            when (alarmType) {
                "morning" -> {
                    val morningClass = classesToday.find { it.session == "Sáng" }
                    if (morningClass != null) {
                        val subject = morningClass.subject
                        val room = morningClass.room
                        showNotification(context, notifBaseId + 1, "Lịch học Sáng - $cleanClassName", "Môn: $subject\n${ScheduleUtils.formatRoom(room)}")
                    }
                }
                "afternoon" -> {
                    val afternoonClass = classesToday.find { it.session == "Chiều" }
                    if (afternoonClass != null) {
                        val subject = afternoonClass.subject
                        val room = afternoonClass.room
                        showNotification(context, notifBaseId + 2, "Lịch học Chiều - $cleanClassName", "Môn: $subject\n${ScheduleUtils.formatRoom(room)}")
                    }
                }
                "evening" -> {
                    if (classesTomorrow.isNotEmpty()) {
                        val summary = classesTomorrow.joinToString("\n") { 
                            "Buổi ${it.session}: ${it.subject} (${ScheduleUtils.formatRoom(it.room)})"
                        }
                        showNotification(context, notifBaseId + 3, "Lịch ngày mai - $cleanClassName ($tomorrowStr)", summary)
                    } else {
                        showNotification(context, notifBaseId + 3, "Lịch ngày mai - $cleanClassName ($tomorrowStr)", "Ngày mai bạn được nghỉ ngơi!")
                    }
                }
            }
            
            // Reschedule for next day
            val scheduler = AlarmScheduler(context)
            scheduler.scheduleAlarmForEntity(alarm)
        }
    }

    // getDayOfWeekString -> Đã chuyển sang ScheduleUtils.kt

    private fun getTomorrowDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    private fun showNotification(context: Context, notificationId: Int, title: String, content: String) {
        val channelId = "schedule_alerts"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Thông báo lịch học",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun rescheduleAlarms(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            
            val allAlarms = db.alarmDao().getAllAlarms()
            val scheduler = AlarmScheduler(context)
            scheduler.scheduleAllAlarms(allAlarms)
        }
    }
}
