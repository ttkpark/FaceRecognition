package com.example.facerecognition.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: com.example.facerecognition.viewmodel.FaceRecognitionViewModel,
    onNavigateToCamera: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    onNavigateToAttendance: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Camera) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("얼굴 인식 출석 시스템") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == Screen.Camera,
                    onClick = { currentScreen = Screen.Camera },
                    icon = { Text("카메라") }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.Register,
                    onClick = { currentScreen = Screen.Register },
                    icon = { Text("등록") }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.Attendance,
                    onClick = { currentScreen = Screen.Attendance },
                    icon = { Text("출석") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                Screen.Camera -> CameraScreen(
                    viewModel = viewModel,
                    onNavigateToRegister = { currentScreen = Screen.Register },
                    onNavigateToAttendance = { currentScreen = Screen.Attendance }
                )
                Screen.Register -> RegisterScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.Camera }
                )
                Screen.Attendance -> AttendanceScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = Screen.Camera }
                )
            }
        }
    }
}

enum class Screen {
    Camera,
    Register,
    Attendance
}
