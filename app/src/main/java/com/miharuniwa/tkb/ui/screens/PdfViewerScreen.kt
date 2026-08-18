package com.miharuniwa.tkb.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.miharuniwa.tkb.ui.theme.*
import androidx.lifecycle.viewModelScope
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.data.PdfDownloader
import com.miharuniwa.tkb.data.saveImageToStorage
import com.miharuniwa.tkb.ui.components.ImageCropper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewModel(private val downloader: PdfDownloader, private val fileId: String, private val fileName: String) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadPdf()
    }

    private fun loadPdf() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            val localFile = downloader.getLocalFile(fileName)
            if (localFile != null) {
                _uiState.value = UiState.Success(localFile)
            } else {
                val downloadedFile = downloader.downloadPdf(fileId, fileName)
                if (downloadedFile != null) {
                    _uiState.value = UiState.Success(downloadedFile)
                } else {
                    _uiState.value = UiState.Error("Không thể tải PDF. Vui lòng kiểm tra kết nối mạng.")
                }
            }
        }
    }

    sealed class UiState {
        object Loading : UiState()
        data class Success(val file: File) : UiState()
        data class Error(val message: String) : UiState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    fileId: String,
    fileName: String,
    title: String,
    downloader: PdfDownloader,
    appPreferences: AppPreferences,
    isGradePdf: Boolean = false,
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = remember(fileId) { PdfViewModel(downloader, fileId, fileName) }
    val uiState by viewModel.uiState.collectAsState()
    
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCropping by remember { mutableStateOf(false) }

    // Page tracking state
    var currentPageIndex by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }

    // Jump to page state
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageText by remember { mutableStateOf("") }

    if (isCropping && capturedBitmap != null) {
        ImageCropper(
            bitmap = capturedBitmap!!,
            onCrop = { cropped ->
                scope.launch {
                    val uriStr = if (isGradePdf) {
                        appPreferences.saveGradeLocationUri.first()
                    } else {
                        appPreferences.saveLocationUri.first()
                    }
                    
                    val outputName = if (isGradePdf) {
                        "DIEM_${fileId}_${System.currentTimeMillis()}.png"
                    } else {
                        // Format file name
                        val datePattern = Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})")
                        val parsedDates = datePattern.findAll(title).toList()
                        if (parsedDates.size >= 2) {
                            val start = parsedDates[0].groupValues
                            val sd = start[1].padStart(2, '0')
                            val sm = start[2].padStart(2, '0')
                            val sy = start[3]
                            
                            val end = parsedDates[1].groupValues
                            val ed = end[1].padStart(2, '0')
                            val em = end[2].padStart(2, '0')
                            val ey = end[3]
                            
                            if (sy == ey) "${sd}${sm}-${ed}${em}${ey}.png" else "${sd}${sm}${sy}-${ed}${em}${ey}.png"
                        } else {
                            "TKB_${System.currentTimeMillis()}.png"
                        }
                    }
                    
                    val success = saveImageToStorage(context, cropped, uriStr, outputName)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, if (success) "Đã lưu ảnh ($outputName)" else "Lỗi khi lưu ảnh", Toast.LENGTH_SHORT).show()
                    }
                    isCropping = false
                }
            },
            onShare = { cropped ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val cachePath = File(context.cacheDir, "images")
                        cachePath.mkdirs()
                        val file = File(cachePath, "tkb_share.png")
                        val fileOutputStream = java.io.FileOutputStream(file)
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
                        fileOutputStream.close()

                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )

                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ thời khóa biểu"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Lỗi khi chia sẻ", Toast.LENGTH_SHORT).show()
                        }
                    }
                    isCropping = false
                }
            },
            onCancel = { isCropping = false }
        )
        return
    }

    Scaffold(
        containerColor = BgDark,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showBackButton || title.isNotEmpty()) {
                TopAppBar(
                    title = { Text(title, color = TextPrimary, fontSize = 16.sp) },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở lại", tint = PrimaryDark)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark)
                )
            }
        },
        bottomBar = {
            if (totalPages > 1) {
                Surface(
                    color = SurfaceDark,
                    tonalElevation = 4.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Trang trước",
                                tint = if (currentPageIndex > 0) PrimaryDark else TextSecondary.copy(alpha = 0.3f)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(PrimaryDark.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .clickable { 
                                    jumpPageText = (currentPageIndex + 1).toString()
                                    showJumpDialog = true 
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Trang ${currentPageIndex + 1} / $totalPages",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Nhảy đến trang",
                                tint = PrimaryDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = { if (currentPageIndex < totalPages - 1) currentPageIndex++ },
                            enabled = currentPageIndex < totalPages - 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Trang sau",
                                tint = if (currentPageIndex < totalPages - 1) PrimaryDark else TextSecondary.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (showJumpDialog) {
            AlertDialog(
                onDismissRequest = { showJumpDialog = false },
                title = { Text("Nhảy đến trang", color = TextPrimary) },
                text = {
                    OutlinedTextField(
                        value = jumpPageText,
                        onValueChange = { jumpPageText = it },
                        label = { Text("Số trang (1 - $totalPages)", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryDark,
                            unfocusedBorderColor = BorderDark
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val pageNum = jumpPageText.toIntOrNull()
                        if (pageNum != null && pageNum in 1..totalPages) {
                            currentPageIndex = pageNum - 1
                        } else {
                            Toast.makeText(context, "Số trang không hợp lệ", Toast.LENGTH_SHORT).show()
                        }
                        showJumpDialog = false
                    }) {
                        Text("Đi", color = PrimaryDark, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJumpDialog = false }) {
                        Text("Hủy", color = TextSecondary)
                    }
                },
                containerColor = SurfaceDark
            )
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding).background(BgDark)) {
            when (val state = uiState) {
                is PdfViewModel.UiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = PrimaryDark)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Đang tải tài liệu...", color = TextPrimary)
                    }
                }
                is PdfViewModel.UiState.Error -> {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                is PdfViewModel.UiState.Success -> {
                    PdfRendererSinglePage(
                        file = state.file,
                        pageIndex = currentPageIndex,
                        onPageCountDetected = { count -> totalPages = count }
                    ) { bmp ->
                        capturedBitmap = bmp
                    }
                }
            }
            
            if (capturedBitmap != null) {
                IconButton(
                    onClick = { isCropping = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(SurfaceDark.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Crop, contentDescription = "Cắt", tint = PrimaryDark)
                }
            }
        }
    }
}

@Composable
fun PdfRendererSinglePage(
    file: File,
    pageIndex: Int,
    onPageCountDetected: (Int) -> Unit,
    onBitmapRendered: (Bitmap) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val zoomState = key(pageIndex) { rememberZoomState() }

    // Reset bitmap whenever page index changes
    LaunchedEffect(pageIndex) {
        bitmap = null
    }

    LaunchedEffect(file, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)
                onPageCountDetected(renderer.pageCount)
                
                if (renderer.pageCount > pageIndex) {
                    val page = renderer.openPage(pageIndex)
                    // Use higher resolution for better cropping quality, but cap size to prevent OOM
                    val maxResolution = 2400f
                    val scaleFactor = minOf(2.5f, maxResolution / maxOf(page.width, page.height).toFloat())
                    val w = (page.width * scaleFactor).toInt()
                    val h = (page.height * scaleFactor).toInt()
                    
                    val newBitmap = Bitmap.createBitmap(
                        if (w > 0) w else 1, 
                        if (h > 0) h else 1,
                        Bitmap.Config.ARGB_8888
                    )
                    newBitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(newBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap = newBitmap
                    onBitmapRendered(newBitmap)
                }
                renderer.close()
                descriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
            }
        }
    }

    if (bitmap != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .zoomable(zoomState),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryDark)
        }
    }
}

