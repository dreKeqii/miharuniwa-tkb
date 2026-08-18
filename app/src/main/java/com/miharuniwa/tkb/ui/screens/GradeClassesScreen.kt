package com.miharuniwa.tkb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miharuniwa.tkb.data.ClassGradeEntity
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.ui.theme.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GradeClassesViewModel(private val repository: ScheduleRepository, private val rootUrl: String) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                repository.gradeDao.clearClassGrades()
            }

            val cached = repository.getCachedClassGrades()
            val cachedStudents = repository.getAggregatedStudents()
            if (cached.isNotEmpty() && !forceRefresh) {
                _uiState.value = UiState.Success(cached, cachedStudents)
            } else {
                _uiState.value = UiState.Loading
                try {
                    // Gọi tuần tự để tránh lỗi crash coroutine do unhandled exception khi mất mạng
                    repository.fetchAndCacheAllClassGrades("https://dkc.edu.vn/bang-diem-cao-dang/", shouldClear = false)
                    repository.fetchAndCacheAllClassGrades("https://dkc.edu.vn/bang-diem-trung-cap/", shouldClear = false)
                    _uiState.value = UiState.Success(
                        classes = repository.getCachedClassGrades(),
                        students = repository.getAggregatedStudents()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    val fallbackCached = repository.getCachedClassGrades()
                    val fallbackStudents = repository.getAggregatedStudents()
                    if (fallbackCached.isNotEmpty() || fallbackStudents.isNotEmpty()) {
                        _uiState.value = UiState.Success(fallbackCached, fallbackStudents, isOffline = true)
                    } else {
                        _uiState.value = UiState.Error(e.message ?: "Không thể kết nối đến máy chủ")
                    }
                }
            }
        }
    }

    sealed class UiState {
        object Loading : UiState()
        data class Success(
            val classes: List<ClassGradeEntity>, 
            val students: List<com.miharuniwa.tkb.data.AggregatedStudent> = emptyList(),
            val isOffline: Boolean = false
        ) : UiState()
        data class Error(val message: String) : UiState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeClassesScreen(
    repository: ScheduleRepository,
    rootUrl: String,
    onNavigateToClassDetail: (String, String, String) -> Unit,
    onNavigateToStudentDetail: (String, String) -> Unit
) {
    val viewModel: GradeClassesViewModel = remember(rootUrl) { GradeClassesViewModel(repository, rootUrl) }
    val uiState by viewModel.uiState.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val appPreferences = remember { com.miharuniwa.tkb.data.AppPreferences(context) }
    val debugMode by appPreferences.debugMode.collectAsState(initial = false)
    
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var selectedSystemFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("Tất cả") }
    val systemFilters = listOf("Tất cả", "Cao đẳng", "Liên thông", "Trung cấp")

    val filteredClasses = remember(uiState, searchQuery, selectedSystemFilter) {
        val state = uiState
        if (state is GradeClassesViewModel.UiState.Success) {
            val items = state.classes
            items.filter { item ->
                val cleanName = item.className
                val matchesSearch = cleanName.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (selectedSystemFilter) {
                    "Tất cả" -> true
                    "Cao đẳng" -> cleanName.contains("Cao đẳng", ignoreCase = true) || cleanName.contains("CĐ", ignoreCase = true)
                    "Liên thông" -> cleanName.contains("Liên thông", ignoreCase = true) || cleanName.contains("LT", ignoreCase = true)
                    "Trung cấp" -> cleanName.contains("Trung cấp", ignoreCase = true) || cleanName.contains("TC", ignoreCase = true)
                    else -> true
                }
                matchesSearch && matchesFilter
            }
        } else emptyList()
    }

    val filteredStudents = remember(uiState, searchQuery) {
        val state = uiState
        if (state is GradeClassesViewModel.UiState.Success && searchQuery.isNotBlank()) {
            state.students.filter {
                it.studentName.contains(searchQuery, ignoreCase = true) || 
                it.birthDate.contains(searchQuery, ignoreCase = true)
            }
        } else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Tiêu đề & Làm mới
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TRA CỨU KẾT QUẢ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryDark,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Bảng Điểm Theo Lớp",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                    .clickable { viewModel.loadData(forceRefresh = true) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Làm mới",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Tìm tên lớp...", color = TextSecondary.copy(alpha = 0.5f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = PrimaryDark,
                unfocusedBorderColor = BorderDark,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        )

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                systemFilters.forEach { filter ->
                    val isSelected = selectedSystemFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSystemFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryDark.copy(alpha = 0.15f),
                            selectedLabelColor = PrimaryDark,
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) PrimaryDark else BorderDark,
                            selectedBorderColor = PrimaryDark,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.dp
                        )
                    )
                }
            }
            if (debugMode && uiState is GradeClassesViewModel.UiState.Success) {
                Text(
                    text = "${filteredClasses.size} lớp",
                    color = PrimaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Main List Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is GradeClassesViewModel.UiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryDark
                    )
                }
                is GradeClassesViewModel.UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Lỗi: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadData(forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark, contentColor = Color(0xFF003258))
                        ) {
                            Text("Thử lại", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                is GradeClassesViewModel.UiState.Success -> {
                    if (filteredClasses.isEmpty() && filteredStudents.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Không tìm thấy kết quả nào",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (filteredStudents.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "SINH VIÊN (${filteredStudents.size})",
                                        color = PrimaryDark,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }
                                items(filteredStudents) { student ->
                                    StudentResultCard(student = student) {
                                        onNavigateToStudentDetail(student.studentName, student.birthDate)
                                    }
                                }
                            }

                            if (filteredClasses.isNotEmpty()) {
                                if (searchQuery.isNotBlank() || filteredStudents.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "LỚP HỌC (${filteredClasses.size})",
                                            color = PrimaryDark,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                        )
                                    }
                                }
                                items(filteredClasses) { item ->
                                    ClassCard(item = item) {
                                        onNavigateToClassDetail(item.id, item.link, item.className)
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

@Composable
fun ClassCard(
    item: ClassGradeEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PrimaryDark.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Class,
                    contentDescription = null,
                    tint = PrimaryDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.className,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Nhấp để xem các môn học",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun StudentResultCard(
    student: com.miharuniwa.tkb.data.AggregatedStudent,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(1.dp, PrimaryDark.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PrimaryDark.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = PrimaryDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.studentName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Ngày sinh: ${student.birthDate}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
