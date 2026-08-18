package com.miharuniwa.tkb.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animate
import coil.compose.AsyncImage
import com.miharuniwa.tkb.ui.theme.*

import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentOverviewScreen(
    studentName: String = "NGUYỄN VĂN AN",
    birthDate: String = "15/05/2004",
    repository: com.miharuniwa.tkb.data.ScheduleRepository? = null,
    onBack: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidth = configuration.screenWidthDp.dp
    val maxHeaderHeight = screenWidth 
    val minHeaderHeight = 152.dp // 64dp TopAppBar + 88dp hàng chứa Avatar
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val avatarRepo = remember { com.miharuniwa.tkb.data.AvatarRepository(context) }
    
    var studentData by remember { mutableStateOf<com.miharuniwa.tkb.data.AggregatedStudent?>(null) }
    
    LaunchedEffect(studentName, birthDate) {
        val allStudents = repository?.getAggregatedStudents() ?: emptyList()
        studentData = allStudents.find { it.studentName == studentName && it.birthDate == birthDate }
    }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(studentName, birthDate) {
        val uriStr = avatarRepo.getAvatarUri(studentName, birthDate)
        selectedImageUri = if (uriStr != null) Uri.parse(uriStr) else null
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = CropImageContract(),
        onResult = { result ->
            if (result.isSuccessful) {
                val uriContent = result.uriContent
                if (uriContent != null) {
                    selectedImageUri = uriContent
                    avatarRepo.saveAvatarUri(studentName, birthDate, uriContent.toString())
                }
            }
        }
    )
    
    val maxHeaderHeightPx = with(density) { maxHeaderHeight.toPx() }
    val minHeaderHeightPx = with(density) { minHeaderHeight.toPx() }
    val scrollRangePx = maxHeaderHeightPx - minHeaderHeightPx
    
    // Quản lý chiều cao Header độc lập với LazyColumn
    val headerHeightPx = remember { mutableFloatStateOf(maxHeaderHeightPx) }
    
    val scrollFraction = 1f - ((headerHeightPx.floatValue - minHeaderHeightPx) / scrollRangePx).coerceIn(0f, 1f)
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Cuộn lên: Thu nhỏ Header trước, dư mới cho LazyColumn cuộn
                if (available.y < 0) {
                    val currentHeight = headerHeightPx.floatValue
                    val newHeight = (currentHeight + available.y).coerceIn(minHeaderHeightPx, maxHeaderHeightPx)
                    val consumed = newHeight - currentHeight
                    headerHeightPx.floatValue = newHeight
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Cuộn xuống: Nếu LazyColumn đã cuộn chạm đỉnh (không cuộn xuống được nữa), thì Phóng to Header
                if (available.y > 0) {
                    val currentHeight = headerHeightPx.floatValue
                    val newHeight = (currentHeight + available.y).coerceIn(minHeaderHeightPx, maxHeaderHeightPx)
                    val consumedOffset = newHeight - currentHeight
                    headerHeightPx.floatValue = newHeight
                    return Offset(0f, consumedOffset)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val currentHeight = headerHeightPx.floatValue
                // Nếu Header đang lửng lơ ở giữa (không Full cũng không Min)
                if (currentHeight > minHeaderHeightPx + 1f && currentHeight < maxHeaderHeightPx - 1f) {
                    val target = if (available.y < -300f) minHeaderHeightPx
                    else if (available.y > 300f) maxHeaderHeightPx
                    else if (currentHeight < (maxHeaderHeightPx + minHeaderHeightPx) / 2) minHeaderHeightPx
                    else maxHeaderHeightPx

                    animate(initialValue = currentHeight, targetValue = target) { value, _ ->
                        headerHeightPx.floatValue = value
                    }
                    // Tiêu thụ lực vuốt để danh sách không bị cuộn tiếp sau khi Header đã tự động đóng/mở
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val currentHeight = headerHeightPx.floatValue
                if (currentHeight > minHeaderHeightPx + 1f && currentHeight < maxHeaderHeightPx - 1f) {
                    val target = if (currentHeight < (maxHeaderHeightPx + minHeaderHeightPx) / 2) minHeaderHeightPx
                    else maxHeaderHeightPx

                    animate(initialValue = currentHeight, targetValue = target) { value, _ ->
                        headerHeightPx.floatValue = value
                    }
                }
                return Velocity.Zero
            }
        }
    }
    
    val currentHeaderHeightDp = with(density) { headerHeightPx.floatValue.toDp() }
    
    // Tính toán các thông số nội suy
    val avatarSize = lerp(screenWidth, 64.dp, scrollFraction)
    val avatarCornerRadius = lerp(0.dp, 32.dp, scrollFraction) 
    val avatarOffsetX = lerp(0.dp, 20.dp, scrollFraction)
    val avatarOffsetY = lerp(0.dp, 64.dp + 12.dp, scrollFraction) 
    
    val textBias = -scrollFraction 
    val textPaddingStart = lerp(0.dp, 100.dp, scrollFraction) 
    val textOffsetY = lerp(maxHeaderHeight - 78.dp, 64.dp + 22.dp, scrollFraction)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection)
    ) {
        // Cấu trúc Column để chia màn hình thành phần trên (Header) và dưới (List)
        // Việc này đảm bảo List LUÔN nằm dưới Header, không bao giờ bị đè lấn
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeaderHeightDp)
            ) {
                // Ảnh đại diện
                Box(
                    modifier = Modifier
                        .offset(x = avatarOffsetX, y = avatarOffsetY)
                        .size(avatarSize)
                        .clip(RoundedCornerShape(avatarCornerRadius))
                        .background(Color(0xFF333333))
                        .clickable {
                            photoPickerLauncher.launch(
                                CropImageContractOptions(
                                    uri = null,
                                    cropImageOptions = CropImageOptions(
                                        imageSourceIncludeGallery = true,
                                        imageSourceIncludeCamera = true,
                                        guidelines = CropImageView.Guidelines.ON,
                                        cropShape = CropImageView.CropShape.OVAL,
                                        aspectRatioX = 1,
                                        aspectRatioY = 1,
                                        fixAspectRatio = true
                                    )
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        // Thư viện Coil tự động load và cắt ảnh (Crop) cho vừa khít khung
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop // Tự động cắt lấy phần trung tâm
                        )
                    } else {
                        // Icon mặc định
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Default Avatar",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(avatarSize * 0.6f)
                        )
                    }
                }
                
                // Cụm Chữ (Tên + Ngày sinh)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = textOffsetY)
                        .padding(start = textPaddingStart)
                        .padding(horizontal = lerp(16.dp, 0.dp, scrollFraction))
                        .background(
                            color = Color.White.copy(alpha = 0.3f * (1f - scrollFraction)),
                            shape = RoundedCornerShape(lerp(20.dp, 0.dp, scrollFraction))
                        )
                        .border(
                            width = lerp(1.dp, 0.dp, scrollFraction),
                            color = Color.White.copy(alpha = 0.4f * (1f - scrollFraction)),
                            shape = RoundedCornerShape(lerp(20.dp, 0.dp, scrollFraction))
                        )
                        .padding(vertical = lerp(8.dp, 0.dp, scrollFraction))
                ) {
                    Text(
                        text = studentName,
                        color = Color.White,
                        fontSize = lerp(26.sp, 20.sp, scrollFraction),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = lerp(1.sp, 0.sp, scrollFraction),
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.8f * (1f - scrollFraction)),
                                offset = Offset(0f, 4f),
                                blurRadius = 12f
                            )
                        ),
                        modifier = Modifier.fillMaxWidth().wrapContentWidth(BiasAlignment.Horizontal(textBias))
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = birthDate,
                        color = Color.White,
                        fontSize = lerp(15.sp, 13.sp, scrollFraction),
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.8f * (1f - scrollFraction)),
                                offset = Offset(0f, 2f),
                                blurRadius = 8f
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(BiasAlignment.Horizontal(textBias))
                            .alpha(0.7f + 0.3f * scrollFraction) // Sáng dần lên khi thu nhỏ (0.7 -> 1.0)
                    )
                }
                
                // Dải ngang mờ (Divider) ở đáy Header, hiện rõ khi thu nhỏ
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .alpha(scrollFraction), // Hiện dần khi cuộn lên
                        color = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
            
            // Danh sách điểm số (Nằm HOÀN TOÀN TÁCH BIỆT bên dưới Header)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                
                val subjects = studentData?.subjects ?: emptyList()
                items(subjects) { score ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ExpandableScoreCard(score)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                item { Spacer(modifier = Modifier.height(48.dp)) }
            }
        }
        
        // TopAppBar luôn neo cứng trên cùng
        TopAppBar(
            title = {
                Text(
                    "Kết Quả Học Tập",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, 
                        contentDescription = "Back", 
                        tint = Color.White
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = BgDark.copy(alpha = 0.5f + 0.5f * scrollFraction),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )
    }
}

@Composable
fun ExpandableScoreCard(score: com.miharuniwa.tkb.data.AggregatedSubjectGrade) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val txScores = remember(score.marks) { score.marks.filter { it.type.contains("Thường xuyên", ignoreCase = true) || it.type.contains("TX", ignoreCase = true) || it.type.contains("QT", ignoreCase = true) }.map { it.score.toString() }.ifEmpty { listOf("-") } }
    val dkScores = remember(score.marks) { score.marks.filter { it.type.contains("Định kỳ", ignoreCase = true) || it.type.contains("DK", ignoreCase = true) || it.type.contains("GK", ignoreCase = true) }.map { it.score.toString() }.ifEmpty { listOf("-") } }
    val ktScore = remember(score.marks) { score.marks.filter { it.type.contains("Thi", ignoreCase = true) || it.type.contains("Kết thúc", ignoreCase = true) || it.type.contains("KT", ignoreCase = true) }.map { it.score.toString() }.firstOrNull() ?: "-" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.75f)) {
                    Text(
                        text = score.subjectName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (score.examDate.isNullOrBlank()) "Chưa có ngày thi" else score.examDate,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = score.finalScore?.toString() ?: ktScore,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = Color.White
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {

                    ScoreRow(label = "Điểm Thường Xuyên", scores = txScores, horizontal = true)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ScoreRow(label = "Kiểm tra Định kỳ", scores = dkScores, horizontal = true)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thi Kết thúc môn",
                            color = Color(0xFFDCDCDC),
                            fontSize = 14.sp
                        )
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = ktScore,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Điểm Tổng Kết",
                            color = Color(0xFFDCDCDC),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = score.finalScore?.toString() ?: "Chưa có",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreRow(label: String, scores: List<String>, horizontal: Boolean) {
    if (horizontal) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFFDCDCDC),
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scores.forEach { sc ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF23232A))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = sc, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    } else {
        Column {
            Text(
                text = label,
                color = Color(0xFFDCDCDC),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                scores.forEach { sc ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF23232A))
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sc,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
