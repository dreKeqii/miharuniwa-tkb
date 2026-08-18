package com.miharuniwa.tkb.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.data.ScheduleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import com.miharuniwa.tkb.data.GeminiClient
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.font.FontWeight

private val modelCache = mutableMapOf<String, List<com.miharuniwa.tkb.data.GeminiModel>>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    appPreferences: AppPreferences,
    repository: ScheduleRepository,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedUriStr by appPreferences.saveLocationUri.collectAsState(initial = null)
    val savedGradeUriStr by appPreferences.saveGradeLocationUri.collectAsState(initial = null)
    val savedApiKey by appPreferences.geminiApiKey.collectAsState(initial = null)
    val savedModel by appPreferences.geminiModel.collectAsState(initial = null)
    val savedDefaultClass by appPreferences.defaultClass.collectAsState(initial = null)
    val allAlarms by repository.alarmDao.getAllAlarmsFlow().collectAsState(initial = emptyList())
    
    var url by remember(appPreferences) { mutableStateOf("") }
    var geminiKey by remember { mutableStateOf("") }
    var geminiModel by remember { mutableStateOf("") }
    var defaultClassState by remember { mutableStateOf("") }
    
    var hasLoadedApiKey by remember { mutableStateOf(false) }
    var hasLoadedModel by remember { mutableStateOf(false) }
    var hasLoadedDefaultClass by remember { mutableStateOf(false) }

    LaunchedEffect(savedApiKey) {
        if (savedApiKey != null && !hasLoadedApiKey) {
            geminiKey = savedApiKey ?: ""
            hasLoadedApiKey = true
        }
    }

    LaunchedEffect(savedModel) {
        if (savedModel != null && !hasLoadedModel) {
            geminiModel = savedModel ?: ""
            hasLoadedModel = true
        }
    }

    LaunchedEffect(savedDefaultClass) {
        if (savedDefaultClass != null && !hasLoadedDefaultClass) {
            defaultClassState = savedDefaultClass ?: ""
            hasLoadedDefaultClass = true
        }
    }

    var modelList by remember { mutableStateOf<List<com.miharuniwa.tkb.data.GeminiModel>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var isClassDropdownExpanded by remember { mutableStateOf(false) }
    
    val activeClasses = remember(allAlarms) { 
        allAlarms.filter { it.isEnabled }.map { it.className }.distinct() 
    }

    LaunchedEffect(Unit) {
        val root = appPreferences.rootUrl.first()
        url = if (root.isNullOrEmpty()) "https://dkc.edu.vn/tkb/" else root
    }

    LaunchedEffect(geminiKey) {
        if (geminiKey.isNotBlank() && geminiKey.length > 20) {
            if (modelCache.containsKey(geminiKey)) {
                modelList = modelCache[geminiKey]!!
                return@LaunchedEffect
            }
            isLoadingModels = true
            try {
                val models = GeminiClient(OkHttpClient()).fetchModels(geminiKey)
                modelCache[geminiKey] = models
                modelList = models
                // Only auto-select if current selection is invalid
                if (geminiModel.isEmpty() || !models.any { it.name == geminiModel }) {
                    val defaultModel = models.firstOrNull { it.displayName.contains("gemma", ignoreCase = true) }
                                       ?: models.firstOrNull { it.displayName.contains("flash", ignoreCase = true) } 
                                       ?: models.firstOrNull()
                    if (defaultModel != null) {
                        geminiModel = defaultModel.name
                    }
                }
            } catch (e: Exception) {
                // Ignore API error in UI, just leave list empty
            } finally {
                isLoadingModels = false
            }
        }
    }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            scope.launch { appPreferences.saveLocationUri(uri.toString()) }
        }
    }

    val gradeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            scope.launch { appPreferences.saveGradeLocationUri(uri.toString()) }
        }
    }

    ModalBottomSheet(onDismissRequest = {
        CoroutineScope(Dispatchers.IO).launch { 
            appPreferences.saveRootUrl(url) 
            appPreferences.saveGeminiApiKey(geminiKey)
            appPreferences.saveGeminiModel(geminiModel)
            appPreferences.saveDefaultClass(defaultClassState)
        }
        onDismissRequest()
    }) {
        Column(Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Cài đặt", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { 
                    CoroutineScope(Dispatchers.IO).launch { 
                        appPreferences.saveRootUrl(url) 
                        appPreferences.saveGeminiApiKey(geminiKey)
                        appPreferences.saveGeminiModel(geminiModel)
                        appPreferences.saveDefaultClass(defaultClassState)
                    }
                    onDismissRequest()
                }) {
                    Text("Lưu", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Link thời khóa biểu gốc") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            ExposedDropdownMenuBox(
                expanded = isModelDropdownExpanded,
                onExpandedChange = { isModelDropdownExpanded = !isModelDropdownExpanded }
            ) {
                val displayModelName = modelList.find { it.name == geminiModel }?.displayName ?: geminiModel
                OutlinedTextField(
                    value = if (isLoadingModels) "Đang tải..." else displayModelName.ifEmpty { "Chưa chọn Model" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Mô hình AI") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = isModelDropdownExpanded,
                    onDismissRequest = { isModelDropdownExpanded = false }
                ) {
                    if (modelList.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Chưa có danh sách (Kiểm tra API Key)") },
                            onClick = { isModelDropdownExpanded = false }
                        )
                    } else {
                        modelList.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName) },
                                onClick = {
                                    geminiModel = model.name
                                    isModelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            ExposedDropdownMenuBox(
                expanded = isClassDropdownExpanded,
                onExpandedChange = { isClassDropdownExpanded = !isClassDropdownExpanded }
            ) {
                OutlinedTextField(
                    value = if (defaultClassState.isEmpty()) "Chưa chọn (AI sẽ báo lỗi)" else defaultClassState,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Lớp chính (Dành cho AI Voice)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isClassDropdownExpanded) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = isClassDropdownExpanded,
                    onDismissRequest = { isClassDropdownExpanded = false }
                ) {
                    if (activeClasses.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Chưa có lớp nào bật báo thức") },
                            onClick = { isClassDropdownExpanded = false }
                        )
                    } else {
                        activeClasses.forEach { className ->
                            DropdownMenuItem(
                                text = { Text(className) },
                                onClick = {
                                    defaultClassState = className
                                    isClassDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Vị trí lưu ảnh Thời khóa biểu:", style = MaterialTheme.typography.labelMedium)
            
            val locationName = if (!savedUriStr.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(savedUriStr!!)
                    var displayPath = uri.lastPathSegment ?: ""
                    if (displayPath.contains(":")) {
                        displayPath = displayPath.substringAfter(":")
                    }
                    if (displayPath.isNotEmpty()) {
                        displayPath
                    } else {
                        val docFile = DocumentFile.fromTreeUri(context, uri)
                        docFile?.name ?: "Đã chọn thư mục"
                    }
                } catch (e: Exception) {
                    "Thư mục không hợp lệ"
                }
            } else {
                "DCIM/TKB (Mặc định)"
            }
            
            OutlinedButton(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(locationName)
            }
            
            if (!savedUriStr.isNullOrEmpty()) {
                TextButton(onClick = { scope.launch { appPreferences.clearLocationUri() } }) {
                    Text("Dùng mặc định (DCIM/TKB)")
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text("Vị trí lưu ảnh Bảng điểm:", style = MaterialTheme.typography.labelMedium)
            
            val gradeLocationName = if (!savedGradeUriStr.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(savedGradeUriStr!!)
                    var displayPath = uri.lastPathSegment ?: ""
                    if (displayPath.contains(":")) {
                        displayPath = displayPath.substringAfter(":")
                    }
                    if (displayPath.isNotEmpty()) {
                        displayPath
                    } else {
                        val docFile = DocumentFile.fromTreeUri(context, uri)
                        docFile?.name ?: "Đã chọn thư mục"
                    }
                } catch (e: Exception) {
                    "Thư mục không hợp lệ"
                }
            } else {
                "DCIM/Diem (Mặc định)"
            }
            
            OutlinedButton(onClick = { gradeLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(gradeLocationName)
            }
            
            if (!savedGradeUriStr.isNullOrEmpty()) {
                TextButton(onClick = { scope.launch { appPreferences.clearGradeLocationUri() } }) {
                    Text("Dùng mặc định (DCIM/Diem)")
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            val notificationEnabled by appPreferences.notificationEnabled.collectAsState(initial = false)
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    scope.launch {
                        appPreferences.saveNotificationEnabled(true)
                        com.miharuniwa.tkb.data.ScheduleUpdateWorker.enqueuePeriodicWork(context)
                    }
                    android.widget.Toast.makeText(context, "Đã bật tự động kiểm tra thời khóa biểu!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Không thể kích hoạt nếu thiếu quyền thông báo", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Tự động kiểm tra TKB mới",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tự động kiểm tra định kỳ để cập nhật thời khóa biểu mới nhanh nhất",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val permission = android.Manifest.permission.POST_NOTIFICATIONS
                                    val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, permission
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    
                                    if (hasPermission) {
                                        scope.launch {
                                            appPreferences.saveNotificationEnabled(true)
                                            com.miharuniwa.tkb.data.ScheduleUpdateWorker.enqueuePeriodicWork(context)
                                        }
                                        android.widget.Toast.makeText(context, "Đã bật kiểm tra ngầm thời khóa biểu mới!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        permissionLauncher.launch(permission)
                                    }
                                } else {
                                    scope.launch {
                                        appPreferences.saveNotificationEnabled(true)
                                        com.miharuniwa.tkb.data.ScheduleUpdateWorker.enqueuePeriodicWork(context)
                                    }
                                    android.widget.Toast.makeText(context, "Đã bật kiểm tra ngầm thời khóa biểu mới!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                scope.launch {
                                    appPreferences.saveNotificationEnabled(false)
                                    com.miharuniwa.tkb.data.ScheduleUpdateWorker.cancelPeriodicWork(context)
                                }
                                android.widget.Toast.makeText(context, "Đã tắt tự động kiểm tra!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { 
                    com.miharuniwa.tkb.data.ScheduleUpdateWorker.enqueueOneTimeWork(context)
                    android.widget.Toast.makeText(context, "Đã yêu cầu cập nhật ngay lập tức", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cập nhật TKB thủ công (Ngay bây giờ)")
            }

            val debugMode by appPreferences.debugMode.collectAsState(initial = false)
            if (debugMode) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val db = com.miharuniwa.tkb.data.AppDatabase.getInstance(context)
                                db.clearAllTables()
                            }
                            appPreferences.clearAllPreferences()
                            context.cacheDir.listFiles()?.forEach { it.delete() }
                            android.widget.Toast.makeText(context, "Đã xóa toàn bộ dữ liệu và Reset App!", android.widget.Toast.LENGTH_LONG).show()
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Xóa toàn bộ dữ liệu & Reset App")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
