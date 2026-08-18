package com.miharuniwa.tkb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.data.GradeSubjectEntity
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class ClassGradeDetailViewModel(
    private val repository: ScheduleRepository,
    private val appPreferences: AppPreferences,
    private val classId: String,
    private val classUrl: String
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val parsingState: StateFlow<Map<String, Boolean>> = repository.parsingStates.asStateFlow()
    val isParsingAll: StateFlow<Boolean> = repository.isParsingAll.asStateFlow()

    init {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val cached = repository.getCachedGradeSubjects(classId)
            
            if (cached.isNotEmpty()) {
                _uiState.value = UiState.Success(cached, isRefreshing = true)
            } else {
                _uiState.value = UiState.Loading
            }

            try {
                val fetched = repository.fetchAndCacheGradeSubjects(classId, classUrl)
                val cachedFileIds = cached.map { it.fileId }.toSet()
                
                // Chỉ sắp xếp đưa môn học mới lên đầu khi cached không rỗng (tránh xáo trộn lần đầu)
                val sortedFetched = if (cached.isNotEmpty()) {
                    val newFileIds = fetched.map { it.fileId }.toSet() - cachedFileIds
                    val newItems = fetched.filter { it.fileId in newFileIds }
                    val oldItems = fetched.filter { it.fileId !in newFileIds }
                    newItems + oldItems
                } else {
                    fetched
                }

                _uiState.value = UiState.Success(
                    items = sortedFetched,
                    isRefreshing = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
                if (cached.isNotEmpty()) {
                    _uiState.value = UiState.Success(cached, isOffline = true, isRefreshing = false)
                } else {
                    _uiState.value = UiState.Error(e.message ?: "Không thể lấy thông tin môn học")
                }
            }
        }
    }

    fun preDownloadAllPdfs(context: android.content.Context, subjects: List<GradeSubjectEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            subjects.forEach { subject ->
                try {
                    repository.downloadPdf(subject.fileId, context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun parseAllSubjects(
        context: android.content.Context,
        subjects: List<GradeSubjectEntity>,
        onComplete: (Int, Int) -> Unit
    ) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            repository.isParsingAll.value = true
            val apiKey = appPreferences.geminiApiKey.first() ?: ""
            val modelName = appPreferences.geminiModel.first() ?: "gemini-3.5-flash"
            
            if (apiKey.isBlank()) {
                withContext(Dispatchers.Main) { onComplete(0, 0) }
                repository.isParsingAll.value = false
                return@launch
            }

            val unparsed = subjects.filter { it.jsonGrades.isNullOrEmpty() }
            var successCount = 0
            
            unparsed.forEachIndexed { index, subject ->
                if (repository.parsingStates.value[subject.fileId] == true) return@forEachIndexed
                repository.parsingStates.value = repository.parsingStates.value + (subject.fileId to true)
                try {
                    val result = repository.parseGradesWithGemini(context, subject.fileId, apiKey, modelName)
                    if (result != null) {
                        successCount++
                        _uiState.value = UiState.Success(repository.getCachedGradeSubjects(classId))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    repository.parsingStates.value = repository.parsingStates.value + (subject.fileId to false)
                }
                
                if (index < unparsed.size - 1) {
                    kotlinx.coroutines.delay(7000) // Nghỉ 7 giây giữa các môn để tránh rate limit RPM
                }
            }
            repository.isParsingAll.value = false
            withContext(Dispatchers.Main) { onComplete(successCount, unparsed.size) }
        }
    }

    fun parseSubjectWithGemini(
        context: android.content.Context,
        subject: GradeSubjectEntity,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (repository.parsingStates.value[subject.fileId] == true) {
            onComplete(false, "Môn học này đang được phân tích, vui lòng đợi.")
            return
        }
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            repository.parsingStates.value = repository.parsingStates.value + (subject.fileId to true)
            
            try {
                val apiKey = appPreferences.geminiApiKey.first() ?: ""
                val modelName = appPreferences.geminiModel.first() ?: "gemini-3.5-flash"
                
                if (apiKey.isBlank()) {
                    withContext(Dispatchers.Main) { onComplete(false, "Vui lòng cấu hình Gemini API Key trong phần Cài đặt trước.") }
                    return@launch
                }

                val result = repository.parseGradesWithGemini(context, subject.fileId, apiKey, modelName)
                if (result != null) {
                    withContext(Dispatchers.Main) { onComplete(true, null) }
                    _uiState.value = UiState.Success(repository.getCachedGradeSubjects(classId))
                } else {
                    withContext(Dispatchers.Main) { onComplete(false, "Không thể phân tích tệp bảng điểm. Vui lòng thử lại.") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onComplete(false, e.message ?: "Lỗi ngoại lệ khi phân tích") }
            } finally {
                repository.parsingStates.value = repository.parsingStates.value + (subject.fileId to false)
            }
        }
    }

    sealed class UiState {
        object Loading : UiState()
        data class Success(
            val items: List<GradeSubjectEntity>,
            val isOffline: Boolean = false,
            val isRefreshing: Boolean = false
        ) : UiState()
        data class Error(val message: String) : UiState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassGradeDetailScreen(
    classId: String,
    classUrl: String,
    className: String,
    repository: ScheduleRepository,
    appPreferences: AppPreferences,
    onNavigateToGrades: (String, String) -> Unit, // fileId, subjectName
    onPdfClick: (String, String, String) -> Unit, // fileId, fileName, title
    onBack: () -> Unit
) {
    val viewModel: ClassGradeDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = classId,
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ClassGradeDetailViewModel(repository, appPreferences, classId, classUrl) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val parsingState by viewModel.parsingState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tự động tải trước PDF của toàn bộ môn học một lần duy nhất khi danh sách được tải thành công
    var hasPreDownloaded by remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is ClassGradeDetailViewModel.UiState.Success && !state.isRefreshing && !hasPreDownloaded) {
            hasPreDownloaded = true
            viewModel.preDownloadAllPdfs(context, state.items)
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = className,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Danh sách môn học",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
                },
                actions = {
                    val isParsingAll by viewModel.isParsingAll.collectAsState()
                    val successState = uiState as? ClassGradeDetailViewModel.UiState.Success
                    val subjects = successState?.items ?: emptyList()
                    val isRefreshing = successState?.isRefreshing == true
                    val hasUnparsed = subjects.any { it.jsonGrades.isNullOrEmpty() }
                    
                    if (hasUnparsed) {
                        if (isParsingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            TextButton(onClick = {
                                viewModel.parseAllSubjects(context, subjects) { successCount, totalCount ->
                                    android.widget.Toast.makeText(
                                        context,
                                        "Đã phân tích thành công $successCount/$totalCount môn học!",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }) {
                                Text("Phân tích tất cả", color = PrimaryDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    if (isRefreshing) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.loadData(forceRefresh = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgDark)
        ) {
            when (val state = uiState) {
                is ClassGradeDetailViewModel.UiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = PrimaryDark
                    )
                }
                is ClassGradeDetailViewModel.UiState.Error -> {
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
                is ClassGradeDetailViewModel.UiState.Success -> {
                    if (state.items.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Lớp học này chưa có bảng điểm nào được đăng tải",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.items) { subject ->
                                val isParsing = parsingState[subject.fileId] ?: false
                                val isParsed = !subject.jsonGrades.isNullOrEmpty()
                                val isDebugMode by appPreferences.debugMode.collectAsState(initial = false)
                                SubjectGradeCard(
                                    subject = subject,
                                    isParsing = isParsing,
                                    isParsed = isParsed,
                                    buttonText = if (isParsed) "Xem PDF Gốc" else "Phân Tích Bằng AI",
                                    isDebugMode = isDebugMode,
                                    onCardClick = {
                                        if (isParsed) {
                                            // Nhấp vào thẻ môn đã phân tích -> Xem bảng điểm tự render
                                            onNavigateToGrades(subject.fileId, subject.subjectName)
                                        } else {
                                            // Nhấn vào thẻ môn chưa phân tích -> Mở xem PDF gốc và tự phân tích chạy ngầm
                                            if (!isParsing) {
                                                viewModel.parseSubjectWithGemini(context, subject) { _, _ -> }
                                            }
                                            val safeSubject = subject.subjectName.replace(Regex("[^a-zA-Z0-9]"), "_")
                                            val fileName = "grade_${subject.classId}_${subject.fileId}_$safeSubject.pdf"
                                            onPdfClick(subject.fileId, fileName, subject.subjectName)
                                        }
                                    },
                                    onButtonClick = {
                                        if (isParsed) {
                                            // Đã phân tích -> Nút thực hiện xem PDF gốc
                                            val safeSubject = subject.subjectName.replace(Regex("[^a-zA-Z0-9]"), "_")
                                            val fileName = "grade_${subject.classId}_${subject.fileId}_$safeSubject.pdf"
                                            onPdfClick(subject.fileId, fileName, subject.subjectName)
                                        } else {
                                            // Chưa phân tích -> Nút thực hiện Phân tích bằng AI thủ công và tự động chuyển hướng
                                            viewModel.parseSubjectWithGemini(context, subject) { success, errMsg ->
                                                if (success) {
                                                    onNavigateToGrades(subject.fileId, subject.subjectName)
                                                } else {
                                                    android.widget.Toast.makeText(context, errMsg ?: "Đã xảy ra lỗi", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    },
                                    onDownloadClick = {
                                        repository.savePdfToDownloads(context, subject.fileId, subject.subjectName)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectGradeCard(
    subject: GradeSubjectEntity,
    isParsing: Boolean,
    isParsed: Boolean,
    buttonText: String,
    isDebugMode: Boolean = false,
    onCardClick: () -> Unit,
    onButtonClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onCardClick)
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isParsed) Color(0xFF1B5E20).copy(alpha = 0.1f) else PrimaryDark.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isParsed) Icons.Default.CheckCircle else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (isParsed) Color(0xFF4CAF50) else PrimaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subject.subjectName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isParsed) "Bảng điểm đã được phân tích" else "Bảng điểm chưa phân tích (Drive ID: ${subject.fileId})",
                        fontSize = 11.sp,
                        color = if (isParsed) Color(0xFF81C784) else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showDebugDialog by remember { mutableStateOf(false) }
                if (showDebugDialog) {
                    AlertDialog(
                        onDismissRequest = { showDebugDialog = false },
                        title = { Text("Raw JSON Response", color = Color.White) },
                        text = {
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                                item { 
                                    Text(
                                        text = subject.jsonGrades ?: "Rỗng", 
                                        fontSize = 12.sp, 
                                        color = TextSecondary,
                                        style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                    ) 
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { showDebugDialog = false }) { Text("Đóng", color = PrimaryDark) } },
                        containerColor = BgDark,
                        titleContentColor = Color.White
                    )
                }

                if (isParsing) {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryDark
                        )
                    }
                } else {
                    if (isParsed && isDebugMode) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceDark)
                                .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                                .clickable { showDebugDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("{}", color = PrimaryDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                            .clickable(onClick = onDownloadClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Tải PDF", tint = PrimaryDark, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onButtonClick,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark, contentColor = Color(0xFF003258))
                    ) {
                        if (isParsed) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Grade, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(buttonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
