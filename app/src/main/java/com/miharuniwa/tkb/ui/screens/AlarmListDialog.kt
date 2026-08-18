package com.miharuniwa.tkb.ui.screens

import android.content.Intent
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miharuniwa.tkb.data.AlarmEntity
import com.miharuniwa.tkb.data.AlarmScheduler
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.data.ScheduleAlarmReceiver
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListDialog(
    repository: ScheduleRepository,
    appPreferences: AppPreferences,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alarms by remember { mutableStateOf<List<AlarmEntity>>(emptyList()) }
    val debugMode by appPreferences.debugMode.collectAsState(initial = false)
    
    var showTestDialog by remember { mutableStateOf<AlarmEntity?>(null) }
    var alarmToEdit by remember { mutableStateOf<AlarmEntity?>(null) }

    fun loadAlarms() {
        scope.launch {
            alarms = withContext(Dispatchers.IO) { repository.alarmDao.getAllAlarms() }
        }
    }

    LaunchedEffect(Unit) {
        loadAlarms()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Danh sách Báo thức") },
        text = {
            if (alarms.isEmpty()) {
                Text(
                    text = "Chưa có báo thức nào. Hãy vào chi tiết một tuần học, chọn báo thức để tạo mới.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(alarms) { alarm ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(alarm.className),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = alarm.systemKey,
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Sáng: ${alarm.morningTime} | Chiều: ${alarm.afternoonTime}",
                                            fontSize = 12.sp,
                                            color = PrimaryDark
                                        )
                                    }
                                    
                                    Switch(
                                        checked = alarm.isEnabled,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                val updated = alarm.copy(isEnabled = checked)
                                                withContext(Dispatchers.IO) { repository.alarmDao.insertAlarm(updated) }
                                                
                                                val scheduler = AlarmScheduler(context)
                                                if (checked) {
                                                    scheduler.scheduleAlarmForEntity(updated)
                                                } else {
                                                    scheduler.cancelAlarm(updated.id)
                                                }
                                                loadAlarms()
                                                com.miharuniwa.tkb.widget.ScheduleWidgetUpdater.updateAllWidgets(context)
                                            }
                                        }
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (debugMode) {
                                        TextButton(
                                            onClick = { showTestDialog = alarm },
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("Test", fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    
                                    IconButton(
                                        onClick = { alarmToEdit = alarm },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Sửa", tint = PrimaryDark, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) { repository.alarmDao.deleteAlarmById(alarm.id) }
                                                AlarmScheduler(context).cancelAlarm(alarm.id)
                                                loadAlarms()
                                                com.miharuniwa.tkb.widget.ScheduleWidgetUpdater.updateAllWidgets(context)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Đóng")
            }
        }
    )
    
    if (showTestDialog != null) {
        val alarmToTest = showTestDialog!!
        AlertDialog(
            onDismissRequest = { showTestDialog = null },
            title = { Text("Test Báo thức") },
            text = {
                Column {
                    Text("Chọn khung giờ để giả lập nhận báo thức ngay lập tức cho lớp ${com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(alarmToTest.className)}:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmToTest.id)
                                putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, "morning")
                            }
                            context.sendBroadcast(intent)
                            showTestDialog = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Giả lập Sáng")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmToTest.id)
                                putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, "afternoon")
                            }
                            context.sendBroadcast(intent)
                            showTestDialog = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Giả lập Chiều")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmToTest.id)
                                putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, "evening")
                            }
                            context.sendBroadcast(intent)
                            showTestDialog = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Giả lập Tối (21:00)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTestDialog = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (alarmToEdit != null) {
        val alarm = alarmToEdit!!
        var morningTime by remember { mutableStateOf(alarm.morningTime) }
        var afternoonTime by remember { mutableStateOf(alarm.afternoonTime) }

        AlertDialog(
            onDismissRequest = { alarmToEdit = null },
            title = { Text("Sửa giờ báo thức") },
            text = {
                Column {
                    Text("Lớp: ${com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(alarm.className)}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = morningTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Buổi sáng") },
                        trailingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable {
                            val parts = morningTime.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 6
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
                            TimePickerDialog(context, { _, hour, minute ->
                                morningTime = String.format("%02d:%02d", hour, minute)
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = afternoonTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Buổi chiều") },
                        trailingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable {
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
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val updated = alarm.copy(morningTime = morningTime, afternoonTime = afternoonTime)
                        withContext(Dispatchers.IO) { repository.alarmDao.insertAlarm(updated) }
                        if (updated.isEnabled) {
                            AlarmScheduler(context).scheduleAlarmForEntity(updated)
                        }
                        loadAlarms()
                        com.miharuniwa.tkb.widget.ScheduleWidgetUpdater.updateAllWidgets(context)
                        alarmToEdit = null
                    }
                }) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { alarmToEdit = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}
