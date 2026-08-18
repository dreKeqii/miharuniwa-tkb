package com.miharuniwa.tkb.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.miharuniwa.tkb.data.AppDatabase
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_CANCELED, resultValue)

        val db = AppDatabase.getInstance(applicationContext)
        
        val appPreferences = AppPreferences(applicationContext)
        val sharedPrefs = applicationContext.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        setContent {
            MyApplicationTheme {
                var classNames by remember { mutableStateOf<List<String>>(emptyList()) }
                var selectedClass by remember { mutableStateOf("") }
                var opacity by remember { mutableStateOf(65f) }
                val debugMode by appPreferences.debugMode.collectAsState(initial = false)

                LaunchedEffect(Unit) {
                    val savedClass = sharedPrefs.getString("class_$appWidgetId", "") ?: ""
                    val savedOpacity = sharedPrefs.getInt("opacity_$appWidgetId", 65)
                    
                    opacity = savedOpacity.toFloat()

                    withContext(Dispatchers.IO) {
                        val alarms = db.alarmDao().getAllAlarms().filter { it.isEnabled }
                        classNames = alarms.map { it.className }.distinct()
                        if (classNames.isNotEmpty()) {
                            selectedClass = if (classNames.contains(savedClass)) savedClass else classNames.first()
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cấu hình Widget Thời khóa biểu", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))

                        if (classNames.isEmpty()) {
                            Text("Chưa có lớp nào bật thông báo. Vui lòng vào app bật thông báo lớp trước.")
                        } else {
                            Text("Chọn lớp học:")
                            classNames.forEach { cls ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selectedClass == cls,
                                        onClick = { selectedClass = cls }
                                    )
                                    Text(cls)
                                }
                            }

                            if (debugMode) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Độ trong suốt nền (Debug): ${opacity.toInt()}%")
                                Slider(
                                    value = opacity,
                                    onValueChange = { opacity = it },
                                    valueRange = 0f..100f
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { saveAndFinish(sharedPrefs, selectedClass, opacity.toInt()) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Lưu")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveAndFinish(sharedPrefs: android.content.SharedPreferences, className: String, opacity: Int) {
        sharedPrefs.edit()
            .putString("class_$appWidgetId", className)
            .putInt("opacity_$appWidgetId", opacity)
            .apply()

        lifecycleScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(applicationContext, glanceId) { prefs ->
                    prefs[stringPreferencesKey("update_trigger")] = System.currentTimeMillis().toString()
                }
                ScheduleWidget().update(applicationContext, glanceId)

                WidgetDailyUpdateWorker.enqueueDailyUpdate(applicationContext)
            }

            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}
