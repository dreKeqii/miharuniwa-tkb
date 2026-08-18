package com.miharuniwa.tkb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.data.WeekItemEntity
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Close
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.detectTransformGestures
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.io.File
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

class MainViewModel(private val repository: ScheduleRepository, private val url: String) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val cached = repository.getCachedWeeks()
            if (cached.isNotEmpty() && !forceRefresh) {
                _uiState.value = UiState.Success(cached)
            } else {
                _uiState.value = UiState.Loading
            }

            try {
                val newItems = repository.fetchAndCacheWeeks(url)
                _uiState.value = UiState.Success(newItems)
            } catch (e: Exception) {
                if (cached.isNotEmpty()) {
                    _uiState.value = UiState.Success(cached, isOffline = true)
                } else {
                    _uiState.value = UiState.Error(e.message ?: "Unknown Error")
                }
            }
        }
    }

    sealed class UiState {
        object Loading : UiState()
        data class Success(val items: List<WeekItemEntity>, val isOffline: Boolean = false) : UiState()
        data class Error(val message: String) : UiState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    repository: ScheduleRepository,
    rootUrl: String,
    appPreferences: AppPreferences,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToPdf: (String, String, String) -> Unit
) {
    val viewModel: MainViewModel = remember(rootUrl) { MainViewModel(repository, rootUrl) }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val pinnedTimestamp by appPreferences.pinnedImageTimestamp.collectAsState(initial = 0L)
    val followedSystems by appPreferences.followedSystems.collectAsState(initial = emptySet())
    val context = LocalContext.current
    val defaultClass by appPreferences.defaultClass.collectAsState(initial = "")

    // Dashboard: Lấy danh sách báo thức từ Room DB
    var dashboardAlarms by remember { mutableStateOf<List<com.miharuniwa.tkb.data.AlarmEntity>>(emptyList()) }
    val dashboardSelectedClasses by appPreferences.dashboardClasses.collectAsState(initial = emptySet())
    // Làm sạch tên lớp từ DataStore (xử lý tương thích ngược khi DataStore lưu tên cũ có sĩ số)
    val cleanDashboardClasses = remember(dashboardSelectedClasses) {
        dashboardSelectedClasses.map { com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(it) }.toSet()
    }
    var showClassPicker by remember { mutableStateOf(false) }
    var showFullSchedule by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            dashboardAlarms = repository.alarmDao.getAllAlarms().filter { it.isEnabled }
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showAlarmList by remember { mutableStateOf(false) }
    var hasPromptedSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(rootUrl) {
        if (rootUrl.isEmpty() && !hasPromptedSettings) {
            showSettings = true
            hasPromptedSettings = true
        }
    }

    if (showSettings) {
        SettingsBottomSheet(appPreferences, repository) { showSettings = false }
    }
    
    if (showAlarmList) {
        AlarmListDialog(repository, appPreferences) { showAlarmList = false }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp, start = 24.dp, end = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val debugMode by appPreferences.debugMode.collectAsState(initial = false)
                    var tapCount by remember { mutableIntStateOf(0) }
                    
                    Column(
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    tapCount++
                                    if (tapCount >= 7) {
                                        scope.launch {
                                            val newState = !debugMode
                                            appPreferences.setDebugMode(newState)
                                            android.widget.Toast.makeText(context, if (newState) "Chế độ Nhà phát triển đã BẬT" else "Chế độ Nhà phát triển đã TẮT", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        tapCount = 0
                                    }
                                }
                            )
                        }
                    ) {
                        Text(
                            text = "STUDENT ASSISTANT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryDark,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "MiharuNiwa - TKB" + if (debugMode) " [DEBUG]" else "",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                    Row {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(SurfaceDark, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                                .clickable { showAlarmList = true }
                                .testTag("alarm_list_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = "Báo thức", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(SurfaceDark, RoundedCornerShape(16.dp))
                                .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                                .clickable { showSettings = true }
                                .testTag("settings_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                
                // Link Input Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark, RoundedCornerShape(16.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = TextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = rootUrl.ifEmpty { "Chưa có URL..." },
                            color = LinkText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(BorderDark, RoundedCornerShape(8.dp))
                                .clickable { viewModel.loadData(true) }
                                .testTag("refresh_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(PrimaryDark, CircleShape))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is MainViewModel.UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryDark)
                }
                is MainViewModel.UiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Lỗi: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadData(forceRefresh = true) }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark, contentColor = Color(0xFF003258))) {
                            Text("Thử lại", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                is MainViewModel.UiState.Success -> {
                    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "THỜI KHÓA BIỂU TUẦN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2E3135), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (state.isOffline) "Đã lưu Cache (Offline)" else "Trực tuyến",
                                    fontSize = 10.sp,
                                    color = if (state.isOffline) Color(0xFFFFA8A8) else PrimaryDark
                                )
                            }
                        }
                        
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.items) { item ->
                                WeekCard(
                                    item = item,
                                    followedSystems = followedSystems,
                                    repository = repository,
                                    onNavigateToPdf = onNavigateToPdf
                                ) {
                                    onNavigateToDetail(item.id, item.link, item.title)
                                }
                            }

                            // === DASHBOARD THÔNG MINH ===
                            if (dashboardAlarms.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth().testTag("dashboard_card"),
                                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                                        shape = RoundedCornerShape(16.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "LỊCH HỌC CỦA TÔI",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryDark,
                                                    letterSpacing = 1.sp
                                                )
                                                TextButton(
                                                    onClick = { showClassPicker = true },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Chọn lớp", fontSize = 11.sp, color = PrimaryDark)
                                                }
                                            }

                                            val cal = java.util.Calendar.getInstance()
                                            val todayStr = com.miharuniwa.tkb.data.ScheduleUtils.getDayOfWeekString(cal.get(java.util.Calendar.DAY_OF_WEEK))
                                            val todayDateStr = String.format("%02d/%02d/%04d", cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR))
                                            
                                            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                            val tomorrowStr = com.miharuniwa.tkb.data.ScheduleUtils.getDayOfWeekString(cal.get(java.util.Calendar.DAY_OF_WEEK))
                                            val tomorrowDateStr = String.format("%02d/%02d/%04d", cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR))

                                            val alarmsToShow = if (cleanDashboardClasses.isNotEmpty()) {
                                                dashboardAlarms.filter { cleanDashboardClasses.contains(com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(it.className)) }
                                            } else if (!defaultClass.isNullOrEmpty()) {
                                                dashboardAlarms.filter { com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(it.className) == com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(defaultClass!!) }
                                            } else {
                                                dashboardAlarms
                                            }

                                            if (alarmsToShow.isEmpty()) {
                                                Text(
                                                    "Chưa chọn lớp nào. Hãy nhấn \"Chọn lớp\" để chọn lớp hiển thị.",
                                                    fontSize = 12.sp,
                                                    color = TextSecondary
                                                )
                                            }

                                            alarmsToShow.forEach { alarm ->
                                                val items = com.miharuniwa.tkb.data.ScheduleUtils.parseScheduleJson(alarm.scheduleJson)
                                                
                                                val todayItems = items.filter { item ->
                                                    if (com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(item.className) != com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(alarm.className)) return@filter false
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
                                                }.sortedBy { com.miharuniwa.tkb.data.ScheduleUtils.getSessionOrder(it.session) }

                                                val tomorrowItems = items.filter { item ->
                                                    if (com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(item.className) != com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(alarm.className)) return@filter false
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
                                                }.sortedBy { com.miharuniwa.tkb.data.ScheduleUtils.getSessionOrder(it.session) }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(alarm.className),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                    TextButton(
                                                        onClick = { showFullSchedule = alarm.className },
                                                        contentPadding = PaddingValues(0.dp),
                                                        modifier = Modifier.height(24.dp)
                                                    ) {
                                                        Text("Xem tuần", fontSize = 11.sp, color = PrimaryDark)
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Card "Hôm nay"
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .background(Color(0xFF1A2332), RoundedCornerShape(12.dp))
                                                            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                                                            .padding(12.dp)
                                                    ) {
                                                        Text("Hôm nay ($todayStr)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryDark)
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        if (todayItems.isEmpty()) {
                                                            Text("Nghỉ học ✨", fontSize = 12.sp, color = TextSecondary)
                                                        } else {
                                                            todayItems.forEach { item ->
                                                                Text("${item.session}: ${item.subject}", fontSize = 12.sp, color = Color.White)
                                                                Text(com.miharuniwa.tkb.data.ScheduleUtils.formatRoom(item.room), fontSize = 11.sp, color = TextSecondary)
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                            }
                                                        }
                                                    }
                                                    // Card "Ngày mai"
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .background(Color(0xFF1A2332), RoundedCornerShape(12.dp))
                                                            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                                                            .padding(12.dp)
                                                    ) {
                                                        Text("Ngày mai ($tomorrowStr)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D))
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        if (tomorrowItems.isEmpty()) {
                                                            Text("Nghỉ học ✨", fontSize = 12.sp, color = TextSecondary)
                                                        } else {
                                                            tomorrowItems.forEach { item ->
                                                                Text("${item.session}: ${item.subject}", fontSize = 12.sp, color = Color.White)
                                                                Text(com.miharuniwa.tkb.data.ScheduleUtils.formatRoom(item.room), fontSize = 11.sp, color = TextSecondary)
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (showFullSchedule != null) {
            val cName = showFullSchedule!!
            val alarm = dashboardAlarms.find { it.className == cName }
            val items = if (alarm != null) com.miharuniwa.tkb.data.ScheduleUtils.sortScheduleItems(
                com.miharuniwa.tkb.data.ScheduleUtils.parseScheduleJson(alarm.scheduleJson).filter { 
                    com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(it.className) == com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(cName) 
                }
            ) else emptyList()

            val fetchedWeeks = (uiState as? MainViewModel.UiState.Success)?.items ?: emptyList()
            val validRanges = fetchedWeeks.mapNotNull { com.miharuniwa.tkb.data.ScheduleUtils.parseWeekRange(it.title)?.let { range -> it to range } }
            val now = java.time.LocalDate.now()

            val itemsByTab = items.groupBy { item ->
                var tabName = "Không xác định"
                if (item.date.isNotEmpty()) {
                    try {
                        val itemDate = java.time.LocalDate.parse(item.date, java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"))
                        val match = validRanges.find { !itemDate.isBefore(it.second.startDate) && !itemDate.isAfter(it.second.endDate) }
                        if (match != null) {
                            val range = match.second
                            val status = when {
                                now.isBefore(range.startDate) -> "Tuần tiếp"
                                now.isAfter(range.endDate) -> "Tuần trước"
                                else -> "Tuần hiện tại"
                            }
                            val startStr = range.startDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
                            val endStr = range.endDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
                            tabName = "$status\n($startStr - $endStr)"
                        } else {
                            val monday = itemDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                            val sunday = monday.plusDays(6)
                            val status = when {
                                now.isBefore(monday) -> "Tuần tiếp"
                                now.isAfter(sunday) -> "Tuần trước"
                                else -> "Tuần hiện tại"
                            }
                            val startStr = monday.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
                            val endStr = sunday.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
                            tabName = "$status\n($startStr - $endStr)"
                        }
                    } catch (e: Exception) {}
                }
                tabName
            }

            val sortedTabs = itemsByTab.keys.sortedBy { key ->
                try {
                    val dateStr = key.substringAfter("(").substringBefore(" -").trim() + "/${now.year}"
                    java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                } catch(e: Exception) {
                    java.time.LocalDate.MAX
                }
            }

            var selectedTabIndex by remember(sortedTabs) {
                val currentIdx = sortedTabs.indexOfFirst { it.startsWith("Tuần hiện tại") }
                mutableIntStateOf(if (currentIdx >= 0) currentIdx else 0)
            }

            androidx.compose.ui.window.Dialog(onDismissRequest = { showFullSchedule = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight().padding(vertical = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Lịch học: $cName", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryDark)
                            IconButton(onClick = { showFullSchedule = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (sortedTabs.isNotEmpty()) {
                            androidx.compose.material3.ScrollableTabRow(
                                selectedTabIndex = selectedTabIndex,
                                containerColor = Color.Transparent,
                                contentColor = PrimaryDark,
                                edgePadding = 0.dp,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                sortedTabs.forEachIndexed { index, title ->
                                    androidx.compose.material3.Tab(
                                        selected = selectedTabIndex == index,
                                        onClick = { selectedTabIndex = index },
                                        text = { 
                                            Text(
                                                text = title, 
                                                fontSize = 12.sp, 
                                                textAlign = TextAlign.Center,
                                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium
                                            ) 
                                        }
                                    )
                                }
                            }
                            
                            val currentTab = sortedTabs.getOrNull(selectedTabIndex)
                            val tabItems = if (currentTab != null) itemsByTab[currentTab] ?: emptyList() else emptyList()

                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val itemsByDay = tabItems.groupBy { it.dayOfWeek }
                                val orderedDays = listOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật")
                                val sortedDays = itemsByDay.keys.sortedBy { 
                                    val idx = orderedDays.indexOf(it)
                                    if (idx != -1) idx else 99
                                }

                                items(sortedDays) { day ->
                                    val dayItems = itemsByDay[day] ?: emptyList()
                                    val morning = dayItems.filter { it.session.lowercase().contains("sáng") || it.session.lowercase().contains("sang") }
                                    val afternoon = dayItems.filter { it.session.lowercase().contains("chiều") || it.session.lowercase().contains("chieu") || it.session.lowercase().contains("tối") || it.session.lowercase().contains("toi") }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(8.dp)).border(1.dp, BorderDark, RoundedCornerShape(8.dp)).padding(8.dp)
                                    ) {
                                        // Cột 1: Thứ
                                        Column(modifier = Modifier.weight(0.2f).align(Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(day, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB74D), textAlign = TextAlign.Center)
                                        }
                                        
                                        // Cột 2: Sáng
                                        Column(modifier = Modifier.weight(0.4f).padding(horizontal = 4.dp)) {
                                            Text("Sáng", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                                            if (morning.isEmpty()) {
                                                Text("-", color = TextSecondary, fontSize = 12.sp)
                                            } else {
                                                morning.forEach { item ->
                                                    Text(item.subject, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    Text(com.miharuniwa.tkb.data.ScheduleUtils.formatRoom(item.room), fontSize = 10.sp, color = TextSecondary)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                            }
                                        }
                                        
                                        // Cột 3: Chiều
                                        Column(modifier = Modifier.weight(0.4f).padding(horizontal = 4.dp)) {
                                            Text("Chiều", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                                            if (afternoon.isEmpty()) {
                                                Text("-", color = TextSecondary, fontSize = 12.sp)
                                            } else {
                                                afternoon.forEach { item ->
                                                    Text(item.subject, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    Text(com.miharuniwa.tkb.data.ScheduleUtils.formatRoom(item.room), fontSize = 10.sp, color = TextSecondary)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("Không có dữ liệu", modifier = Modifier.padding(16.dp), color = TextSecondary)
                        }
                    }
                }
            }
        }

        if (showClassPicker) {
            var tempSelection by remember(cleanDashboardClasses) { mutableStateOf(cleanDashboardClasses) }
            val uniqueClasses = remember(dashboardAlarms) { dashboardAlarms.map { com.miharuniwa.tkb.data.ScheduleUtils.cleanClassName(it.className) }.distinct() }

            androidx.compose.ui.window.Dialog(onDismissRequest = { showClassPicker = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Chọn lớp hiển thị", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryDark)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (uniqueClasses.isEmpty()) {
                            Text("Chưa có dữ liệu lớp nào. Hãy vào chi tiết tuần học và nhấn 'Theo dõi lớp' để thêm vào Dashboard.", color = TextSecondary)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(uniqueClasses) { cName ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            tempSelection = if (tempSelection.contains(cName)) {
                                                tempSelection - cName
                                            } else {
                                                tempSelection + cName
                                            }
                                        }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.Checkbox(
                                            checked = tempSelection.contains(cName),
                                            onCheckedChange = null,
                                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                                                checkedColor = PrimaryDark,
                                                uncheckedColor = BorderDark
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(cName, color = Color.White)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showClassPicker = false }) {
                                Text("Hủy", color = TextSecondary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch { appPreferences.updateDashboardClasses(tempSelection) }
                                    showClassPicker = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark, contentColor = Color(0xFF003258))
                            ) {
                                Text("Lưu")
                            }
                        }
                    }
                }
            }
        }
    }
    
@Composable
fun WeekCard(
    item: WeekItemEntity,
    followedSystems: Set<String>,
    repository: ScheduleRepository,
    onNavigateToPdf: (String, String, String) -> Unit,
    onClick: () -> Unit
) {
    val isCurrent = item.labelText.contains("Tuần hiện tại", ignoreCase = true)
    
    val weekDetails by produceState<List<com.miharuniwa.tkb.data.ScheduleDetailEntity>>(initialValue = emptyList(), item.id) {
        value = try {
            repository.getCachedDetails(item.id)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    val followedInThisWeek = remember(weekDetails, followedSystems) {
        weekDetails.filter { detail ->
            followedSystems.contains("${detail.base}|${detail.systemType}")
        }
    }
    
    val isSatHach = item.isSatHach
    
    val modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onClick)
        .testTag("week_card_${item.id}")

    if (isSatHach) {
        Box(
            modifier = modifier
                .background(Brush.linearGradient(listOf(SpecialGradientStart, SpecialGradientEnd)))
                .border(1.dp, SpecialBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LỊCH ĐẶC BIỆT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpecialText,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                Box(
                    modifier = Modifier.size(40.dp).background(SpecialText.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = SpecialText, modifier = Modifier.size(24.dp))
                }
            }
        }
    } else {
        val bgColor = if (isCurrent) CurrentWeekBg else SurfaceDark
        
        Box(
            modifier = modifier
                .background(bgColor)
                .then(
                    if (isCurrent) Modifier.border(width = 1.dp, color = Color.Transparent, shape = RoundedCornerShape(16.dp)) // trick or keep it simple
                    else Modifier.border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                )
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isCurrent) {
                    Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(PrimaryDark))
                }
                
                Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.labelText.contains("|")) item.labelText.substringBefore('|').trim() else item.labelText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCurrent) TextPrimary else TextPrimary.copy(alpha = 0.6f)
                        )
                        
                        if (isCurrent) {
                            val currentDayOfWeek = remember {
                                val calendar = java.util.Calendar.getInstance()
                                val day = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                                if (day == java.util.Calendar.SUNDAY) 7 else day - 1
                            }
                            val days = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                days.forEachIndexed { index, dayName ->
                                    val isToday = (index + 1) == currentDayOfWeek
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isToday) PrimaryDark else Color.Transparent,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isToday) PrimaryDark else BorderDark,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = dayName,
                                            fontSize = 9.sp,
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isToday) Color(0xFF003258) else TextPrimary.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (item.labelText.contains("|")) item.labelText.substringAfter('|').trim() else item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isCurrent) Color(0xFFD0E4FF) else LinkText
                    )
                    
                    if (followedInThisWeek.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "LỚP ĐẠI DIỆN ĐANG THEO DÕI (NHẤP ĐỂ MỞ PDF):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) PrimaryDark else Color(0xFFFFB300),
                            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            followedInThisWeek.forEach { detail ->
                                val baseFormatted = if (detail.base == "CS1") "Cơ sở 1" else "Cơ sở 2"
                                val displayText = "${detail.systemType} - $baseFormatted"
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrent) Color.White.copy(alpha = 0.08f) else SurfaceDark.copy(alpha = 0.5f))
                                        .border(
                                            width = 1.dp,
                                            color = if (isCurrent) PrimaryDark.copy(alpha = 0.3f) else BorderDark,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            val safeType = detail.systemType.replace(Regex("[^a-zA-Z0-9]"), "_")
                                            val fileName = "${detail.weekId}_${detail.fileId}_${detail.base}_$safeType.pdf"
                                            onNavigateToPdf(detail.fileId, fileName, item.title)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NotificationsActive,
                                                contentDescription = "Lớp theo dõi",
                                                tint = if (isCurrent) PrimaryDark else Color(0xFFFFB300),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = displayText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = if (isCurrent) PrimaryDark.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "PDF",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) PrimaryDark else Color(0xFFE57373)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
