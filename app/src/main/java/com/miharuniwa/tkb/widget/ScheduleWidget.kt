package com.miharuniwa.tkb.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import com.miharuniwa.tkb.data.AppDatabase
import com.miharuniwa.tkb.data.AlarmEntity
import com.miharuniwa.tkb.data.ScheduleUtils
import com.miharuniwa.tkb.data.ScheduleItem
import org.json.JSONObject
import java.util.Calendar
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.currentState
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduleWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)

        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val sharedPrefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

        provideContent {
            val trigger = currentState(stringPreferencesKey("update_trigger")) ?: ""
            var alarms by remember { mutableStateOf<List<AlarmEntity>>(emptyList()) }
            
            LaunchedEffect(trigger) {
                withContext(Dispatchers.IO) {
                    alarms = db.alarmDao().getAllAlarms().filter { it.isEnabled }
                }
            }

            val className = sharedPrefs.getString("class_$appWidgetId", "") ?: ""
            val opacity = sharedPrefs.getInt("opacity_$appWidgetId", 65)

            val alarm = alarms.find { ScheduleUtils.cleanClassName(it.className) == ScheduleUtils.cleanClassName(className) }
            val scheduleItems = if (alarm != null && alarm.scheduleJson.isNotEmpty()) {
                ScheduleUtils.parseScheduleJson(alarm.scheduleJson)
            } else {
                emptyList()
            }

            val cal = Calendar.getInstance()
            val todayStr = ScheduleUtils.getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK))
            val todayDateStr = String.format("%02d/%02d/%04d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
            
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val tomorrowStr = ScheduleUtils.getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK))
            val tomorrowDateStr = String.format("%02d/%02d/%04d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))

            val classesToday = scheduleItems.filter { item ->
                if (ScheduleUtils.cleanClassName(item.className) != ScheduleUtils.cleanClassName(className)) return@filter false
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
            val classesTomorrow = scheduleItems.filter { item ->
                if (ScheduleUtils.cleanClassName(item.className) != ScheduleUtils.cleanClassName(className)) return@filter false
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

            GlanceTheme {
                WidgetUI(
                    appWidgetId = appWidgetId,
                    className = className,
                    opacity = opacity,
                    classesToday = classesToday,
                    classesTomorrow = classesTomorrow,
                    todayStr = "Hôm nay ($todayStr)",
                    tomorrowStr = "Ngày mai ($tomorrowStr)"
                )
            }
        }
    }

    @Composable
    private fun WidgetUI(
        appWidgetId: Int,
        className: String,
        opacity: Int,
        classesToday: List<ScheduleItem>,
        classesTomorrow: List<ScheduleItem>,
        todayStr: String,
        tomorrowStr: String
    ) {
        val alphaFloat = (opacity.toFloat() / 100f).coerceIn(0f, 1f)
        
        // Define transparent color provider using alphaFloat
        val backgroundColor = ColorProvider(
            day = Color.White.copy(alpha = alphaFloat),
            night = Color.Black.copy(alpha = alphaFloat)
        )

        val context = LocalContext.current
        val intent = Intent(context, ScheduleWidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(4.dp)
                .background(backgroundColor)
                .clickable(actionStartActivity(intent))
        ) {
            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                if (className.isEmpty()) {
                    Text("Vui lòng cấu hình Widget", style = TextStyle(color = GlanceTheme.colors.onSurface))
                    return@Column
                }
                
                Text(className, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = GlanceTheme.colors.primary, textAlign = TextAlign.Center), modifier = GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(8.dp))
                
                Row(modifier = GlanceModifier.fillMaxSize()) {
                    Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                        Text(todayStr, style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), modifier = GlanceModifier.fillMaxWidth())
                        Spacer(GlanceModifier.height(8.dp))
                        SessionBox(classesToday.find { it.session == "Sáng" }, "Sáng", GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.height(4.dp))
                        SessionBox(classesToday.find { it.session == "Chiều" }, "Chiều", GlanceModifier.defaultWeight())
                    }
                    Spacer(GlanceModifier.width(16.dp))
                    Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                        Text(tomorrowStr, style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), modifier = GlanceModifier.fillMaxWidth())
                        Spacer(GlanceModifier.height(8.dp))
                        SessionBox(classesTomorrow.find { it.session == "Sáng" }, "Sáng", GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.height(4.dp))
                        SessionBox(classesTomorrow.find { it.session == "Chiều" }, "Chiều", GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    @Composable
    private fun SessionBox(item: ScheduleItem?, sessionName: String, modifier: GlanceModifier) {
        Column(modifier = modifier.fillMaxWidth()) {
            Text(sessionName, style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(Color.White.copy(alpha = 0.1f))
                    .cornerRadius(8.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    if (item != null) {
                        Text(
                            text = item.subject, 
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface, textAlign = TextAlign.Center),
                            maxLines = 2
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(ScheduleUtils.formatRoom(item.room), style = TextStyle(fontSize = 16.sp, color = GlanceTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center))
                    } else {
                        Text("Trống", style = TextStyle(fontSize = 18.sp, color = GlanceTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center))
                    }
                }
            }
        }
    }

    // parseSchedule, getDayOfWeekString, formatRoom, ScheduleItem -> Đã chuyển sang ScheduleUtils.kt
}
