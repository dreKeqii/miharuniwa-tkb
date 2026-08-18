package com.miharuniwa.tkb.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAllAlarms(alarms: List<AlarmEntity>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return
        }

        for (alarm in alarms) {
            if (alarm.isEnabled) {
                scheduleAlarmForEntity(alarm)
            } else {
                cancelAlarm(alarm.id)
            }
        }
    }

    fun scheduleAlarmForEntity(alarm: AlarmEntity) {
        val baseCode = Math.abs(alarm.id.hashCode()) % 100000 * 10
        scheduleDailyAlarm(alarm.morningTime, baseCode + 1, alarm.id, "morning")
        scheduleDailyAlarm(alarm.afternoonTime, baseCode + 2, alarm.id, "afternoon")
        scheduleDailyAlarm("21:00", baseCode + 3, alarm.id, "evening")
    }

    private fun scheduleDailyAlarm(timeStr: String, requestCode: Int, alarmId: String, alarmType: String) {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_TYPE, alarmType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelAlarm(alarmId: String) {
        val baseCode = Math.abs(alarmId.hashCode()) % 100000 * 10
        val requestCodes = listOf(baseCode + 1, baseCode + 2, baseCode + 3)
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
        for (code in requestCodes) {
            val pendingIntent = PendingIntent.getBroadcast(
                context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
    }
}
