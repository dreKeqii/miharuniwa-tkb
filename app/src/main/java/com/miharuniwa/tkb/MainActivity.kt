package com.miharuniwa.tkb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.room.Room
import com.miharuniwa.tkb.data.AppDatabase
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.data.PdfDownloader
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.ui.theme.MyApplicationTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.miharuniwa.tkb.widget.WidgetDailyUpdateWorker
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    lateinit var appPreferences: AppPreferences
    lateinit var repository: ScheduleRepository
    lateinit var pdfDownloader: PdfDownloader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appPreferences = AppPreferences(applicationContext)
        
        val db = AppDatabase.getInstance(applicationContext)
        

        repository = ScheduleRepository(db.scheduleDao(), db.alarmDao(), db.gradeDao(), db.formDao())
        
        val client = OkHttpClient()
        pdfDownloader = PdfDownloader(applicationContext, client)

        lifecycleScope.launch {
            try {
                val isEnabled = appPreferences.notificationEnabled.first()
                if (isEnabled) {
                    com.miharuniwa.tkb.data.ScheduleUpdateWorker.enqueuePeriodicWork(applicationContext)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            try {
                val alarms = db.alarmDao().getAllAlarms()
                if (alarms.isEmpty()) {
                    if (appPreferences.alarmEnabled.first()) {
                        appPreferences.saveAlarmConfig(
                            enabled = false,
                            className = "",
                            systemKey = "",
                            morningTime = "06:30",
                            afternoonTime = "12:30",
                            scheduleJson = ""
                        )
                    }
                    if (appPreferences.followedSystems.first().isNotEmpty()) {
                        appPreferences.resetFollowedSystems()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                WidgetDailyUpdateWorker.enqueueDailyUpdate(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            MyApplicationTheme(darkTheme = true) { // Priorities dark mode
                TkbApp(appPreferences, repository, pdfDownloader)
            }
        }
    }
}
