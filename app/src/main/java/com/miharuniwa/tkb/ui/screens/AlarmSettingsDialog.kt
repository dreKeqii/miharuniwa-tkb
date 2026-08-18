package com.miharuniwa.tkb.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.data.AlarmScheduler
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.data.GeminiClient
import com.miharuniwa.tkb.data.ScheduleUtils
import com.miharuniwa.tkb.data.ScheduleItem
import com.miharuniwa.tkb.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.util.Calendar

// ScheduleItem, getDayOrder, getSessionOrder -> Đã chuyển sang ScheduleUtils.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsDialog(
    weekId: String,
    systemType: String,
    base: String,
    fileId: String,
    repository: ScheduleRepository,
    appPreferences: AppPreferences,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val debugMode by appPreferences.debugMode.collectAsState(initial = false)
    
    var existingAlarm by remember { mutableStateOf<com.miharuniwa.tkb.data.AlarmEntity?>(null) }

    var alarmEnabled by remember { mutableStateOf(false) }
    var isLoadingAi by remember { mutableStateOf(false) }
    var aiResultJson by remember { mutableStateOf<String?>(null) }
    var justParsed by remember { mutableStateOf(false) }
    
    var classes by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedClass by remember { mutableStateOf("") }
    var isClassDropdownExpanded by remember { mutableStateOf(false) }
    
    var morningTime by remember { mutableStateOf("06:30") }
    var afternoonTime by remember { mutableStateOf("12:30") }
    
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(base, systemType) {
        val alarm = withContext(Dispatchers.IO) {
            repository.alarmDao.getAlarmBySystemKey("$base|$systemType")
        }
        if (alarm != null) {
            aiResultJson = alarm.scheduleJson
            try {
                val classNamesList = com.miharuniwa.tkb.data.ScheduleUtils.extractClassNames(alarm.scheduleJson)
                classes = classNamesList
                if (classNamesList.contains(alarm.className)) {
                    selectedClass = alarm.className
                } else if (classNamesList.isNotEmpty()) {
                    selectedClass = classNamesList[0]
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(selectedClass) {
        if (selectedClass.isNotEmpty()) {
            val cleanedClass = com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(selectedClass)
            val alarm = withContext(Dispatchers.IO) {
                val exact = repository.alarmDao.getAlarmById("$base|$systemType|$cleanedClass")
                if (exact != null) {
                    exact
                } else {
                    repository.alarmDao.getAllAlarms().find {
                        it.systemKey == "$base|$systemType" &&
                        com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(it.className) == cleanedClass
                    }
                }
            }
            existingAlarm = alarm
            if (alarm != null) {
                alarmEnabled = alarm.isEnabled
                morningTime = alarm.morningTime
                afternoonTime = alarm.afternoonTime
                justParsed = false
            } else {
                if (justParsed) {
                    alarmEnabled = true
                    justParsed = false
                } else {
                    alarmEnabled = false
                }
                morningTime = "06:30"
                afternoonTime = "12:30"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Cài đặt thông báo lịch học") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Hệ: $systemType - $base")
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Bật báo thức lịch học")
                    Switch(
                        checked = alarmEnabled,
                        onCheckedChange = { checked ->
                            alarmEnabled = checked
                            if (checked && aiResultJson.isNullOrEmpty()) {
                                isLoadingAi = true
                                justParsed = true
                                scope.launch {
                                    val currentApiKey = appPreferences.geminiApiKey.first()
                                    val currentModel = appPreferences.geminiModel.first()
                                    
                                    if (currentApiKey.isNullOrEmpty() || currentModel.isNullOrEmpty()) {
                                        Toast.makeText(context, "Vui lòng vào Cài đặt để cấu hình Gemini API Key trước!", Toast.LENGTH_LONG).show()
                                        alarmEnabled = false
                                        isLoadingAi = false
                                        return@launch
                                    }
                                    
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            val db = com.miharuniwa.tkb.data.AppDatabase.getInstance(context)
                                            val latestDetail = db.scheduleDao().getDetailsForWeek(weekId).find { it.base == base && it.systemType == systemType }
                                            val actualFileId = latestDetail?.fileId ?: fileId
                                            repository.parsePdfWithGemini(context, actualFileId, currentApiKey, currentModel)
                                        }
                                        
                                        if (result != null) {
                                            aiResultJson = result
                                            
                                            val classNamesList = mutableListOf<String>()
                                            val jsonArray = JSONArray(result)
                                            for (i in 0 until jsonArray.length()) {
                                                val obj = jsonArray.getJSONObject(i)
                                                val cName = obj.optString("className", "")
                                                if (cName.isNotEmpty() && !classNamesList.contains(cName)) {
                                                    classNamesList.add(cName)
                                                }
                                            }
                                            classes = classNamesList
                                            if (classes.isNotEmpty()) {
                                                selectedClass = classes[0]
                                            }
                                            Toast.makeText(context, "Phân tích thành công!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Lỗi tải PDF", Toast.LENGTH_SHORT).show()
                                            alarmEnabled = false
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Lỗi phân tích: ${e.message}", Toast.LENGTH_LONG).show()
                                        alarmEnabled = false
                                    } finally {
                                        isLoadingAi = false
                                    }
                                }
                            }
                        }
                    )
                }

                if (isLoadingAi) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text("Đang phân tích TKB bằng AI...", modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                if (alarmEnabled && !isLoadingAi && classes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = isClassDropdownExpanded,
                                onExpandedChange = { isClassDropdownExpanded = !isClassDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedClass,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Lớp của bạn") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isClassDropdownExpanded) },
                                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = isClassDropdownExpanded,
                                    onDismissRequest = { isClassDropdownExpanded = false }
                                ) {
                                    classes.forEach { className ->
                                        DropdownMenuItem(
                                            text = { Text(className) },
                                            onClick = {
                                                selectedClass = className
                                                isClassDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(
                            onClick = {
                                isLoadingAi = true
                                justParsed = true
                                scope.launch {
                                    val currentApiKey = appPreferences.geminiApiKey.first()
                                    val currentModel = appPreferences.geminiModel.first()
                                    
                                    if (currentApiKey.isNullOrEmpty() || currentModel.isNullOrEmpty()) {
                                        Toast.makeText(context, "Vui lòng vào Cài đặt để cấu hình Gemini API Key trước!", Toast.LENGTH_LONG).show()
                                        alarmEnabled = false
                                        isLoadingAi = false
                                        return@launch
                                    }
                                    
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            val db = com.miharuniwa.tkb.data.AppDatabase.getInstance(context)
                                            val latestDetail = db.scheduleDao().getDetailsForWeek(weekId).find { it.base == base && it.systemType == systemType }
                                            val actualFileId = latestDetail?.fileId ?: fileId
                                            repository.parsePdfWithGemini(context, actualFileId, currentApiKey, currentModel)
                                        }
                                        
                                        if (result != null) {
                                            aiResultJson = result
                                            
                                            val classNamesList = mutableListOf<String>()
                                            val jsonArray = JSONArray(result)
                                            for (i in 0 until jsonArray.length()) {
                                                val obj = jsonArray.getJSONObject(i)
                                                val cName = obj.optString("className", "")
                                                if (cName.isNotEmpty() && !classNamesList.contains(cName)) {
                                                    classNamesList.add(cName)
                                                }
                                            }
                                            classes = classNamesList
                                            if (classes.isNotEmpty()) {
                                                if (!classes.contains(selectedClass)) {
                                                    selectedClass = classes[0]
                                                }
                                            }
                                            Toast.makeText(context, "Phân tích lại thành công!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Lỗi tải PDF", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Lỗi phân tích: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isLoadingAi = false
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Phân tích lại",
                                tint = PrimaryDark
                            )
                        }
                    }

                    // Xem trước lịch học của lớp
                    val parsedItems = remember(aiResultJson, selectedClass) {
                        if (!aiResultJson.isNullOrEmpty() && selectedClass.isNotEmpty()) {
                            val items = ScheduleUtils.parseScheduleJson(aiResultJson!!).filter { it.className == selectedClass }
                            
                            val itemDates = items.mapNotNull {
                                try {
                                    if (it.date.isNotEmpty()) java.time.LocalDate.parse(it.date, java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy")) else null
                                } catch (e: Exception) { null }
                            }
                            
                            val filteredItems = if (itemDates.isNotEmpty()) {
                                val today = java.time.LocalDate.now()
                                val currentWeekMonday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                                val currentWeekSunday = today.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
                                
                                val hasCurrentWeek = itemDates.any { !it.isBefore(currentWeekMonday) && !it.isAfter(currentWeekSunday) }
                                
                                val targetWeekMonday: java.time.LocalDate
                                val targetWeekSunday: java.time.LocalDate
                                
                                if (hasCurrentWeek) {
                                    targetWeekMonday = currentWeekMonday
                                    targetWeekSunday = currentWeekSunday
                                } else {
                                    val maxDate = itemDates.maxOrNull()!!
                                    targetWeekMonday = maxDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                                    targetWeekSunday = maxDate.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
                                }
                                
                                items.filter {
                                    try {
                                        if (it.date.isNotEmpty()) {
                                            val d = java.time.LocalDate.parse(it.date, java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"))
                                            !d.isBefore(targetWeekMonday) && !d.isAfter(targetWeekSunday)
                                        } else true
                                    } catch (e: Exception) { true }
                                }
                            } else {
                                items
                            }
                            
                            ScheduleUtils.sortScheduleItems(filteredItems)
                        } else {
                            emptyList()
                        }
                    }

                    if (parsedItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Xem trước lịch học nhận diện được:",
                            style = MaterialTheme.typography.labelMedium,
                            color = PrimaryDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            parsedItems.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val displayDate = if (item.date.isNotEmpty()) " - ${item.date}" else ""
                                        Text(
                                            text = "${item.dayOfWeek}$displayDate (${item.session})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = item.subject,
                                            fontSize = 13.sp,
                                            color = TextSecondary
                                        )
                                        if (item.teacher.isNotEmpty()) {
                                            Text(
                                                text = "GV: ${item.teacher}",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                    if (item.room.isNotEmpty()) {
                                        Text(
                                            text = ScheduleUtils.formatRoom(item.room),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = PrimaryDark,
                                            modifier = Modifier
                                                .background(ActiveSurface, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (item != parsedItems.last()) {
                                    HorizontalDivider(
                                        color = BorderDark,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    } else if (!aiResultJson.isNullOrEmpty() && selectedClass.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Không tìm thấy lịch học cho lớp này trong dữ liệu phân tích.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Thời gian thông báo (nếu có học):", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = morningTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Buổi sáng") },
                            trailingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                            modifier = Modifier.weight(1f).clickable {
                                val parts = morningTime.split(":")
                                val h = parts.getOrNull(0)?.toIntOrNull() ?: 6
                                val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
                                TimePickerDialog(context, { _, hour, minute ->
                                    morningTime = String.format("%02d:%02d", hour, minute)
                                }, h, m, true).show()
                            },
                            enabled = false, // Disabled so clickable works on the wrapper
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        
                        OutlinedTextField(
                            value = afternoonTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Buổi chiều") },
                            trailingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                            modifier = Modifier.weight(1f).clickable {
                                val parts = afternoonTime.split(":")
                                val h = parts.getOrNull(0)?.toIntOrNull() ?: 12
                                val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
                                TimePickerDialog(context, { _, hour, minute ->
                                    afternoonTime = String.format("%02d:%02d", hour, minute)
                                }, h, m, true).show()
                            },
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Lưu ý: Ứng dụng sẽ gửi một thông báo lịch học tổng hợp cho ngày hôm sau vào lúc 21:00 mỗi tối.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (debugMode && aiResultJson != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("JSON thô (Debug Mode):", style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(
                        value = aiResultJson ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    showPermissionDialog = true
                    return@Button
                }
                
                scope.launch {
                    val cleanedClassName = com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(selectedClass)
                    val alarmId = "$base|$systemType|$cleanedClassName"
                    
                    val db = com.miharuniwa.tkb.data.AppDatabase.getInstance(context)
                    val detail = withContext(Dispatchers.IO) {
                        db.scheduleDao().getDetailByFileId(fileId)
                    }
                    val targetWeekId = detail?.weekId ?: ""
                    val allWeeks = withContext(Dispatchers.IO) { db.scheduleDao().getAllWeeks() }
                    val targetWeek = allWeeks.find { it.id == targetWeekId }
                    
                    if (alarmEnabled) {
                        val newItems = if (!aiResultJson.isNullOrEmpty()) ScheduleUtils.parseScheduleJson(aiResultJson!!) else emptyList()
                        val oldJson = existingAlarm?.scheduleJson ?: ""
                        
                        val mergedJson = if (targetWeek != null && oldJson.isNotEmpty() && justParsed) {
                            val merged = ScheduleUtils.mergeSchedules(oldJson, newItems, allWeeks, targetWeek)
                            if (merged == "[]" || merged.isEmpty()) {
                                aiResultJson ?: ""
                            } else {
                                merged
                            }
                        } else if (!justParsed) {
                            oldJson // Nếu không phân tích lại, giữ nguyên JSON cũ (đã gộp)
                        } else {
                            aiResultJson ?: ""
                        }

                        val finalScheduleJson = if (mergedJson.isEmpty() && newItems.isNotEmpty()) {
                            aiResultJson ?: ""
                        } else {
                            mergedJson
                        }

                        val newAlarm = com.miharuniwa.tkb.data.AlarmEntity(
                            id = alarmId,
                            systemKey = "$base|$systemType",
                            className = cleanedClassName,
                            morningTime = morningTime,
                            afternoonTime = afternoonTime,
                            isEnabled = true,
                            scheduleJson = finalScheduleJson,
                            weekId = targetWeekId
                        )
                        withContext(Dispatchers.IO) {
                            repository.alarmDao.insertAlarm(newAlarm)
                        }
                    } else {
                        withContext(Dispatchers.IO) {
                            repository.alarmDao.deleteAlarmById(alarmId)
                        }
                    }
                    
                    val allAlarms = withContext(Dispatchers.IO) { repository.alarmDao.getAllAlarms() }
                    AlarmScheduler(context).scheduleAllAlarms(allAlarms)
                    
                    // Update widgets
                    try {
                        com.miharuniwa.tkb.widget.ScheduleWidgetUpdater.updateAllWidgets(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    Toast.makeText(context, "Đã lưu cài đặt báo thức", Toast.LENGTH_SHORT).show()
                    onDismissRequest()
                }
            }) {
                Text("Lưu & Đóng")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Hủy")
            }
        }
    )

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Yêu cầu Quyền Báo Thức") },
            text = {
                Text("Ứng dụng cần quyền 'Báo thức và Lời nhắc' (Alarms & Reminders) để có thể thông báo lịch học chính xác giờ cho bạn.\n\nVui lòng cấp quyền này trong Cài đặt hệ thống.")
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }) {
                    Text("Đến Cài đặt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}
