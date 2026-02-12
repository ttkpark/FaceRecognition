package com.example.facerecognition.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.facerecognition.data.Attendance
import com.example.facerecognition.data.Person
import com.example.facerecognition.viewmodel.FaceRecognitionViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AttendanceScreen(
    viewModel: FaceRecognitionViewModel,
    onBack: () -> Unit = {}
) {
    val allPersons by viewModel.allPersons.collectAsState()
    val attendances by viewModel.attendances.collectAsState()
    
    // Person ID를 이름으로 매핑
    val personMap = remember(allPersons) {
        allPersons.associateBy { it.id }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "출석 기록",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 통계 정보
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "등록된 인원: ${allPersons.size}명",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "총 출석 기록: ${attendances.size}건",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 출석 기록 목록
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(attendances) { attendance ->
                val person = personMap[attendance.personId]
                AttendanceItem(
                    attendance = attendance,
                    personName = person?.name ?: "알 수 없음"
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("뒤로가기")
        }
    }
}

@Composable
fun AttendanceItem(
    attendance: Attendance,
    personName: String
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateString = dateFormat.format(Date(attendance.timestamp))
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = personName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = attendance.date,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
