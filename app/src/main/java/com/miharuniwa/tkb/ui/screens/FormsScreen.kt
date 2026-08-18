package com.miharuniwa.tkb.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.data.FormItemEntity
import com.miharuniwa.tkb.data.PdfDownloader
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FormsScreen(
    repository: ScheduleRepository,
    pdfDownloader: PdfDownloader,
    appPreferences: AppPreferences,
    onNavigateToPdfViewer: (String, String, String) -> Unit
) {
    val tabs = listOf("Sổ Tay Sinh Viên", "Biểu mẫu")
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { tabs.size })

    var handbookLink by remember { mutableStateOf<String?>(null) }
    var formsList by remember { mutableStateOf<List<FormItemEntity>>(emptyList()) }
    var isLoadingHandbook by remember { mutableStateOf(false) }
    var isLoadingForms by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredForms = remember(formsList, searchQuery) {
        if (searchQuery.isBlank()) formsList
        else formsList.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Fetch Sổ tay
    LaunchedEffect(Unit) {
        isLoadingHandbook = true
        handbookLink = repository.fetchHandbookLink("https://dkc.edu.vn/so-tay-sinh-vien-hoc-sinh-2025/")
        isLoadingHandbook = false
    }

    // Fetch Biểu mẫu
    LaunchedEffect(Unit) {
        isLoadingForms = true
        try {
            val cached = repository.getCachedForms()
            if (cached.isNotEmpty()) {
                formsList = cached
            }
            val fresh = repository.fetchAndCacheForms("https://dkc.edu.vn/cong-khai-dao-tao/")
            if (fresh.isNotEmpty()) {
                formsList = fresh
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingForms = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = SurfaceDark,
            contentColor = PrimaryDark,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = PrimaryDark
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { 
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = title,
                            color = if (pagerState.currentPage == index) PrimaryDark else TextSecondary,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> {
                    // Sổ tay Sinh viên
                    if (isLoadingHandbook) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryDark)
                        }
                    } else if (handbookLink != null) {
                        val fileId = try { handbookLink!!.split("/d/")[1].split("/")[0] } catch (e: Exception) { "" }
                        if (fileId.isNotEmpty()) {
                            PdfViewerScreen(
                                fileId = fileId,
                                fileName = "handbook_$fileId.pdf",
                                downloader = pdfDownloader,
                                appPreferences = appPreferences,
                                title = "",
                                onBack = { /* Không làm gì vì nằm trong tab */ },
                                showBackButton = false // Tùy chọn ẩn nút back ở PdfViewerScreen
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Không lấy được Sổ tay sinh viên", color = TextSecondary)
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Đang tải Sổ tay sinh viên...", color = TextSecondary)
                        }
                    }
                }
                1 -> {
                    // Biểu mẫu
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Tìm kiếm biểu mẫu...", color = TextSecondary.copy(alpha = 0.5f)) },
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
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        if (!isLoadingForms || formsList.isNotEmpty()) {
                            Text(
                                text = "${filteredForms.size} biểu mẫu",
                                color = PrimaryDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }

                        if (isLoadingForms && formsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = PrimaryDark)
                            }
                        } else if (filteredForms.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                                Text("Không tìm thấy biểu mẫu nào", color = TextSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredForms) { form ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onNavigateToPdfViewer(form.fileId, form.title, "Form")
                                        },
                                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = "PDF Icon",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = form.title,
                                            color = TextPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(
                                            onClick = {
                                                repository.savePdfToDownloads(context, form.fileId, form.title)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Tải xuống",
                                                tint = PrimaryDark
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

