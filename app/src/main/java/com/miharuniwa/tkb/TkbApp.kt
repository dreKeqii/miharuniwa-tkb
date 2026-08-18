package com.miharuniwa.tkb

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miharuniwa.tkb.ui.theme.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import com.miharuniwa.tkb.data.AppPreferences
import com.miharuniwa.tkb.data.PdfDownloader
import com.miharuniwa.tkb.data.ScheduleRepository
import com.miharuniwa.tkb.ui.screens.DetailScreen
import com.miharuniwa.tkb.ui.screens.MainScreen
import com.miharuniwa.tkb.ui.screens.PdfViewerScreen
import com.miharuniwa.tkb.ui.screens.GradeClassesScreen
import com.miharuniwa.tkb.ui.screens.ClassGradeDetailScreen
import com.miharuniwa.tkb.ui.screens.StudentGradeScreen
import com.miharuniwa.tkb.ui.screens.FormsScreen
import kotlinx.coroutines.flow.first

@Composable
fun TkbApp(
    appPreferences: AppPreferences,
    repository: ScheduleRepository,
    pdfDownloader: PdfDownloader
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("main") {
            val rootUrlLoadingState = appPreferences.rootUrl.collectAsState(initial = "___LOADING___")
            val currentRootUrl = rootUrlLoadingState.value
            
            if (currentRootUrl == "___LOADING___") {
                Box(modifier = Modifier.fillMaxSize().background(BgDark))
                return@composable
            }
            
            val pagerState = rememberPagerState(initialPage = 0) { 3 }
            val coroutineScope = rememberCoroutineScope()
            
            Scaffold(
                containerColor = BgDark,
                bottomBar = {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = pagerState.currentPage == 0,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Lịch học"
                                )
                            },
                            label = { Text("Lịch học", fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryDark,
                                selectedTextColor = PrimaryDark,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = PrimaryDark.copy(alpha = 0.15f)
                            )
                        )
                        NavigationBarItem(
                            selected = pagerState.currentPage == 1,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Grade,
                                    contentDescription = "Bảng điểm"
                                )
                            },
                            label = { Text("Bảng điểm", fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryDark,
                                selectedTextColor = PrimaryDark,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = PrimaryDark.copy(alpha = 0.15f)
                            )
                        )
                        NavigationBarItem(
                            selected = pagerState.currentPage == 2,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "Biểu mẫu"
                                )
                            },
                            label = { Text("Biểu mẫu", fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryDark,
                                selectedTextColor = PrimaryDark,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = PrimaryDark.copy(alpha = 0.15f)
                            )
                        )

                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                ) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        if (page == 0) {
                            MainScreen(
                                repository = repository,
                                rootUrl = if (currentRootUrl == null) "" else currentRootUrl,
                                appPreferences = appPreferences,
                                onNavigateToDetail = { weekId, url, title ->
                                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                    navController.navigate("detail/$weekId?url=$encodedUrl&title=$encodedTitle")
                                },
                                onNavigateToPdf = { fileId, fileName, title ->
                                    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                    val encodedFileId = java.net.URLEncoder.encode(fileId, "UTF-8")
                                    val encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
                                    navController.navigate("pdf/$encodedFileId/$encodedFileName?title=$encodedTitle")
                                }
                            )
                        } else if (page == 1) {
                            GradeClassesScreen(
                                repository = repository,
                                rootUrl = if (currentRootUrl == null) "" else currentRootUrl,
                                onNavigateToClassDetail = { classId, url, title ->
                                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                                    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                    navController.navigate("grade_detail/$classId?url=$encodedUrl&title=$encodedTitle")
                                },
                                onNavigateToStudentDetail = { studentName, birthDate ->
                                    val encodedName = java.net.URLEncoder.encode(studentName, "UTF-8")
                                    val encodedDob = java.net.URLEncoder.encode(birthDate, "UTF-8")
                                    navController.navigate("student_overview/$encodedName/$encodedDob")
                                }
                            )
                        } else if (page == 2) {
                            FormsScreen(
                                repository = repository,
                                pdfDownloader = pdfDownloader,
                                appPreferences = appPreferences,
                                onNavigateToPdfViewer = { fileId, title, from ->
                                    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                                    val encodedFileId = java.net.URLEncoder.encode(fileId, "UTF-8")
                                    val encodedFileName = java.net.URLEncoder.encode("form_$fileId", "UTF-8")
                                    navController.navigate("pdf/$encodedFileId/$encodedFileName?title=$encodedTitle")
                                }
                            )
                        }
                    }
                }
            }
        }
        composable(
            "student_overview/{studentName}/{birthDate}",
            arguments = listOf(
                navArgument("studentName") { type = NavType.StringType },
                navArgument("birthDate") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val rawName = backStackEntry.arguments?.getString("studentName") ?: ""
            val rawDob = backStackEntry.arguments?.getString("birthDate") ?: ""
            val studentName = java.net.URLDecoder.decode(rawName, "UTF-8")
            val birthDate = java.net.URLDecoder.decode(rawDob, "UTF-8")
            
            com.miharuniwa.tkb.ui.screens.StudentOverviewScreen(
                studentName = studentName,
                birthDate = birthDate,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "detail/{weekId}?url={url}&title={title}",
            arguments = listOf(
                navArgument("weekId") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val weekId = backStackEntry.arguments?.getString("weekId") ?: ""
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val decodedTitle = java.net.URLDecoder.decode(title, "UTF-8")
            DetailScreen(
                weekId = weekId,
                url = java.net.URLDecoder.decode(url, "UTF-8"),
                repository = repository,
                appPreferences = appPreferences,
                onPdfClick = { fileId, fileName ->
                    val encodedTitle = java.net.URLEncoder.encode(decodedTitle, "UTF-8")
                    val encodedFileId = java.net.URLEncoder.encode(fileId, "UTF-8")
                    val encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
                    navController.navigate("pdf/$encodedFileId/$encodedFileName?title=$encodedTitle")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "grade_detail/{classId}?url={url}&title={title}",
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            ClassGradeDetailScreen(
                classId = classId,
                classUrl = java.net.URLDecoder.decode(url, "UTF-8"),
                className = java.net.URLDecoder.decode(title, "UTF-8"),
                repository = repository,
                appPreferences = appPreferences,
                onNavigateToGrades = { fileId, subjectName ->
                    val encodedSubjectName = java.net.URLEncoder.encode(subjectName, "UTF-8")
                    navController.navigate("student_grades/$fileId/$encodedSubjectName")
                },
                onPdfClick = { fileId, fileName, titleText ->
                    val encodedTitle = java.net.URLEncoder.encode(titleText, "UTF-8")
                    val encodedFileId = java.net.URLEncoder.encode(fileId, "UTF-8")
                    val encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
                    navController.navigate("pdf/$encodedFileId/$encodedFileName?title=$encodedTitle&isGradePdf=true")
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "student_grades/{fileId}/{subjectName}",
            arguments = listOf(
                navArgument("fileId") { type = NavType.StringType },
                navArgument("subjectName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getString("fileId") ?: ""
            val subjectName = backStackEntry.arguments?.getString("subjectName") ?: ""
            StudentGradeScreen(
                fileId = fileId,
                subjectName = java.net.URLDecoder.decode(subjectName, "UTF-8"),
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "pdf/{fileId}/{fileName}?title={title}&isGradePdf={isGradePdf}",
            arguments = listOf(
                navArgument("fileId") { type = NavType.StringType },
                navArgument("fileName") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("isGradePdf") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getString("fileId") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val isGradePdf = backStackEntry.arguments?.getBoolean("isGradePdf") ?: false
            
            PdfViewerScreen(
                fileId = java.net.URLDecoder.decode(fileId, "UTF-8"),
                fileName = java.net.URLDecoder.decode(fileName, "UTF-8"),
                title = java.net.URLDecoder.decode(title, "UTF-8"),
                downloader = pdfDownloader,
                appPreferences = appPreferences,
                isGradePdf = isGradePdf,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
