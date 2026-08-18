package com.miharuniwa.tkb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miharuniwa.tkb.data.ScheduleDetailEntity
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(private val repository: ScheduleRepository, private val weekId: String, private val url: String) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val cached = repository.getCachedDetails(weekId)
            if (cached.isNotEmpty()) {
                _uiState.value = UiState.Success(cached)
            } else {
                _uiState.value = UiState.Loading
            }

            try {
                val newItems = repository.fetchAndCacheDetails(weekId, url)
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
        data class Success(val items: List<ScheduleDetailEntity>, val isOffline: Boolean = false) : UiState()
        data class Error(val message: String) : UiState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    weekId: String,
    url: String,
    repository: ScheduleRepository,
    appPreferences: AppPreferences,
    onPdfClick: (fileId: String, fileName: String) -> Unit,
    onBack: () -> Unit = {}
) {
    val viewModel = remember(weekId) { DetailViewModel(repository, weekId, url) }
    val uiState by viewModel.uiState.collectAsState()

    val followedSystems by appPreferences.followedSystems.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Cơ sở 1", "Cơ sở 2")
    
    var showAlarmDialogFor by remember { mutableStateOf<ScheduleDetailEntity?>(null) }
    
    if (showAlarmDialogFor != null) {
        val item = showAlarmDialogFor!!
        AlarmSettingsDialog(
            weekId = weekId,
            systemType = item.systemType,
            base = item.base,
            fileId = item.fileId,
            repository = repository,
            appPreferences = appPreferences,
            onDismissRequest = { showAlarmDialogFor = null }
        )
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại", tint = PrimaryDark)
                    }
                    Text(
                        text = "CHI TIẾT THỜI KHÓA BIỂU",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = SurfaceDark,
                contentColor = PrimaryDark,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = PrimaryDark
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { 
                            Text(
                                title, 
                                color = if (selectedTabIndex == index) PrimaryDark else TextSecondary,
                                fontWeight = if (selectedTabIndex == index) FontWeight.SemiBold else FontWeight.Medium
                            ) 
                        }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is DetailViewModel.UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryDark)
                    }
                    is DetailViewModel.UiState.Error -> {
                        Text(
                            "Lỗi: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                    }
                    is DetailViewModel.UiState.Success -> {
                        val baseFilter = if (selectedTabIndex == 0) "CS1" else "CS2"
                        val filteredItems = state.items.filter { it.base == baseFilter }

                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            Text(
                                text = "HỆ ĐÀO TẠO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
                            )
                            if (filteredItems.isEmpty()) {
                                Text(
                                    "Không có dữ liệu cho cơ sở này.",
                                    color = TextSecondary,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp)
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(filteredItems) { item ->
                                        val isFollowed = followedSystems.contains("${item.base}|${item.systemType}")
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                                                .clickable { 
                                                    val fileName = "schedule_${item.fileId}.pdf"
                                                    onPdfClick(item.fileId, fileName)
                                                }
                                                .testTag("type_card_${item.id}"),
                                            color = SurfaceDark
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                    Text(
                                                        text = item.systemType,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = TextPrimary
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = "Nhấp để xem PDF thời khóa biểu",
                                                        fontSize = 11.sp,
                                                        color = TextSecondary
                                                    )
                                                }
                                                
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (isFollowed) {
                                                        IconButton(
                                                            onClick = { showAlarmDialogFor = item },
                                                            modifier = Modifier.size(48.dp)
                                                        ) {
                                                            Icon(Icons.Default.Alarm, contentDescription = "Cài đặt báo thức", tint = PrimaryDark)
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                appPreferences.toggleFollowSystem(item.base, item.systemType)
                                                            }
                                                        },
                                                        modifier = Modifier.size(48.dp).testTag("follow_btn_${item.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isFollowed) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                                            contentDescription = if (isFollowed) "Hủy theo dõi" else "Theo dõi hệ học này",
                                                            tint = if (isFollowed) PrimaryDark else TextSecondary.copy(alpha = 0.5f)
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
        }
    }
}
