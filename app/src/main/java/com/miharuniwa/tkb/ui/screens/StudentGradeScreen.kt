package com.miharuniwa.tkb.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miharuniwa.tkb.data.GradeSubjectEntity
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.miharuniwa.tkb.data.ParsedGradeSheet
import com.miharuniwa.tkb.data.ParsedMark
import com.miharuniwa.tkb.data.ParsedStudent
import com.miharuniwa.tkb.data.GradeJsonParser

class StudentGradeViewModel(
    private val repository: ScheduleRepository,
    private val fileId: String
) : ViewModel() {
    private val _sheetState = MutableStateFlow<ParsedGradeSheet?>(null)
    val sheetState: StateFlow<ParsedGradeSheet?> = _sheetState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val subject = repository.gradeDao.getSubjectByFileId(fileId)
                if (subject != null && !subject.jsonGrades.isNullOrEmpty()) {
                    _sheetState.value = parseJson(subject.jsonGrades)
                } else {
                    _errorState.value = "Không tìm thấy dữ liệu bảng điểm."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorState.value = "Không thể hiển thị bảng điểm: ${e.message}"
            }
        }
    }

    private fun parseJson(jsonStr: String): ParsedGradeSheet {
        return GradeJsonParser.parseJson(jsonStr) ?: throw Exception("Invalid JSON Format")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentGradeScreen(
    fileId: String,
    subjectName: String,
    repository: ScheduleRepository,
    onBack: () -> Unit
) {
    val viewModel: StudentGradeViewModel = remember(fileId) {
        StudentGradeViewModel(repository, fileId)
    }
    val sheet by viewModel.sheetState.collectAsState()
    val error by viewModel.errorState.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = sheet?.subjectName ?: subjectName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = sheet?.className ?: "Đang tải dữ liệu...",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BgDark)
        ) {
            val currentSheet = sheet
            if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = error ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
                return@Scaffold
            }
            if (currentSheet == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryDark)
                }
                return@Scaffold
            }

            // Header Meta-info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, BorderDark, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MetaInfoRow("Giáo viên", currentSheet.teacherName)
                    MetaInfoRow("Lớp", currentSheet.className)
                    MetaInfoRow("Ngày thi môn", currentSheet.examDate ?: "Chưa có dữ liệu")
                }
            }

            // Tab switch
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = SurfaceDark,
                contentColor = PrimaryDark,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = PrimaryDark
                    )
                }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Danh sách cả lớp", fontWeight = FontWeight.Bold) },
                    selectedContentColor = PrimaryDark,
                    unselectedContentColor = TextSecondary
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Tra cứu cá nhân", fontWeight = FontWeight.Bold) },
                    selectedContentColor = PrimaryDark,
                    unselectedContentColor = TextSecondary
                )
            }

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (pagerState.currentPage == 1) "Nhập tên hoặc số thứ tự..." else "Lọc tên học sinh...", color = TextSecondary.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedBorderColor = PrimaryDark,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // Content
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().weight(1f)) { page ->
                if (page == 1) {
                    // Chế độ tra cứu cá nhân
                    PersonalLookupView(students = currentSheet.students, query = searchQuery)
                } else {
                    // Chế độ xem cả lớp
                    val filteredStudents = remember(currentSheet.students, searchQuery) {
                        currentSheet.students.filter {
                            it.studentName.contains(searchQuery, ignoreCase = true) || it.sequenceNumber == searchQuery
                        }
                    }
                    ClassListView(students = filteredStudents)
                }
            }
        }
    }
}

@Composable
fun MetaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PersonalLookupView(students: List<ParsedStudent>, query: String) {
    if (query.trim().isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PersonSearch,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Vui lòng nhập tên của bạn hoặc Số thứ tự ở thanh tìm kiếm để tra cứu điểm số chi tiết.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val matchedStudents = remember(students, query) {
        students.filter {
            it.studentName.contains(query, ignoreCase = true) || it.sequenceNumber == query.trim()
        }
    }

    if (matchedStudents.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Không tìm thấy kết quả phù hợp", color = TextSecondary, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(matchedStudents) { student ->
                PersonalGradeCard(student = student)
            }
        }
    }
}

@Composable
fun PersonalGradeCard(student: ParsedStudent) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val avatarRepo = remember { com.miharuniwa.tkb.data.AvatarRepository(context) }
    var avatarUri by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }

    androidx.compose.runtime.LaunchedEffect(student.studentName, student.birthDate) {
        val uriStr = avatarRepo.getAvatarUri(student.studentName, student.birthDate)
        avatarUri = if (uriStr != null) android.net.Uri.parse(uriStr) else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PrimaryDark.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Họ tên & MSSV
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(PrimaryDark.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri != null) {
                        coil.compose.AsyncImage(
                            model = avatarUri,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryDark, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = student.studentName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "STT: ${student.sequenceNumber}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = student.birthDate,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = BorderDark
            )

            // Điểm thành phần
            Text(
                text = "ĐIỂM THÀNH PHẦN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryDark,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val txMarks = student.marks.filter { it.type == "TX" }
            val dkMarks = student.marks.filter { it.type == "DK" }
            val ktMark = student.marks.find { it.type == "KT" }

            // Điểm TX
            GradeComponentRow(
                title = "Thường xuyên (Hệ số 1)",
                scores = txMarks.map { "${it.score}" + if (it.date != null) " (${it.date})" else "" }
            )

            // Điểm DK
            GradeComponentRow(
                title = "Định kỳ (Hệ số 2)",
                scores = dkMarks.map { "${it.score}" + if (it.date != null) " (${it.date})" else "" }
            )

            // Điểm KT
            GradeComponentRow(
                title = "Thi kết thúc môn",
                scores = if (ktMark != null) listOf("${ktMark.score}") else emptyList()
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = BorderDark
            )

            // Điểm tổng kết
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ĐIỂM TỔNG KẾT ĐTK",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                val finalScore = student.finalScore
                Box(
                    modifier = Modifier
                        .background(
                            color = if (finalScore != null) {
                                if (finalScore >= 5.0) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFB71C1C).copy(alpha = 0.2f)
                            } else BorderDark,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (finalScore != null) "$finalScore" else "Chưa có",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (finalScore != null) {
                            if (finalScore >= 5.0) Color(0xFF81C784) else Color(0xFFE57373)
                        } else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun GradeComponentRow(title: String, scores: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextSecondary, fontSize = 13.sp)
        Text(
            text = if (scores.isNotEmpty()) scores.joinToString(", ") else "-",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun ClassListView(students: List<ParsedStudent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(students) { student ->
            ExpandableStudentItem(student = student)
        }
    }
}

@Composable
fun ExpandableStudentItem(student: ParsedStudent) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // STT
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(BorderDark, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.sequenceNumber,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Họ tên & Ngày sinh
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.studentName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = student.birthDate,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                // Điểm thi cuối kỳ hoặc tổng kết thu nhỏ
                val scoreToShow = student.finalScore ?: student.marks.find { it.type == "KT" }?.score
                if (scoreToShow != null) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (scoreToShow >= 5.0) Color(0xFF1B5E20).copy(alpha = 0.1f) else Color(0xFFB71C1C).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$scoreToShow",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (scoreToShow >= 5.0) Color(0xFF81C784) else Color(0xFFE57373)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 8.dp, end = 8.dp)
                ) {
                    HorizontalDivider(color = BorderDark, modifier = Modifier.padding(bottom = 8.dp))
                    
                    val txMarks = student.marks.filter { it.type == "TX" }
                    val dkMarks = student.marks.filter { it.type == "DK" }
                    val ktMark = student.marks.find { it.type == "KT" }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("TX (hệ số 1): ", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            if (txMarks.isNotEmpty()) txMarks.joinToString(", ") { "${it.score}" } else "-",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("Định kỳ (hệ số 2): ", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            if (dkMarks.isNotEmpty()) dkMarks.joinToString(", ") { "${it.score}" } else "-",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("Thi kết thúc môn: ", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            if (ktMark != null) "${ktMark.score}" else "-",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("Tổng kết ĐTK: ", color = TextSecondary, fontSize = 12.sp)
                        Text(
                            if (student.finalScore != null) "${student.finalScore}" else "Chưa có",
                            color = if (student.finalScore != null && student.finalScore >= 5.0) Color(0xFF81C784) else if (student.finalScore != null) Color(0xFFE57373) else TextSecondary,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
