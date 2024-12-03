package com.example.test3

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import com.example.test3.ui.theme.Test3Theme
import kotlinx.coroutines.delay

@Composable
fun MeasurementScreen(
    onBackClick: () -> Unit,
    bluetoothManager: BluetoothManager? = null,
    dataControl: DataControl? = null
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("측정 부위를 차갑게 식힌 후 센서를 측정 부위에 부착해주세요") }
    var timerProgress by remember { mutableStateOf(0f) } // ProgressIndicator 진행도 (기본값 0)
    var isAnalyzing by remember { mutableStateOf(false) }
    var isMeasuring by remember { mutableStateOf(false) } // 측정 중 여부
    var isButtonVisible by remember { mutableStateOf(true) } // 버튼 가시성 여부
    val coroutineScope = rememberCoroutineScope()

    // 데이터 수신
    LaunchedEffect(bluetoothManager) {
        bluetoothManager?.receiveData(
            updateLog = { receivedMessage ->
                if (receivedMessage == "Recv : end") {
                    val storedData = bluetoothManager.getStoredData()

                    // 데이터가 비어있는 경우 오류 메시지 추가
                    if (storedData.any { it.isBlank() }) {
                        Toast.makeText(context, "Error: 데이터가 완전히 수신되지 않았습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        // 데이터 저장 및 처리
                        val saveResult = dataControl?.saveSensorValues(storedData)
                        if (saveResult != null) {
                            if (saveResult.startsWith("Error")) {
                                Toast.makeText(context, saveResult, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "모든 데이터 처리가 성공적으로 완료되었습니다. 파일: $saveResult", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    message = "완료"
                    isAnalyzing = false
                    isMeasuring = false
                    isButtonVisible = true // 버튼 가시성 켜기
                }
            },
            onDisconnected = {
                Toast.makeText(context, "블루투스 연결이 끊어졌습니다.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 32.dp),
            color = if (isAnalyzing) Color.Blue else Color.Black // 분석 중일 때 텍스트 색 변경
        )

        if (isAnalyzing) {
            CircularProgressIndicator( // 분석 중 로딩 표시
                modifier = Modifier.size(60.dp),
                strokeWidth = 6.dp
            )
        } else if (isButtonVisible) {
            Button(onClick = {
                // 완료 버튼 로직
                coroutineScope.launch {
                    isButtonVisible = false // 버튼 가시성 끄기

                    // 5초 카운트다운 시작
                    for (i in 5 downTo 1) {
                        message = "$i 초 후 측정 시작"
                        delay(1000L) // 1초 대기
                    }
                    // 측정 시작
                    bluetoothManager?.sendData("start") {}
                    message = "측정 중입니다..."
                    timerProgress = 0f // 초기화
                    isMeasuring = true // 프로세스바 표시 시작

                    // 30초 타이머 실행 (100단계)
                    val steps = 100 // 총 단계 수
                    val stepDelay = 30000L / steps // 단계별 지연 시간 (ms)
                    for (i in 0..steps) { // 0부터 steps까지 진행
                        timerProgress = i / steps.toFloat()
                        delay(stepDelay) // 단계별 대기
                    }
                    isAnalyzing = true
                    isMeasuring = false
                    message = "분석 중..."
                }
            },
                enabled = !isAnalyzing
            ) {
                Text(if (message != "완료") "완료" else "재측정")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isMeasuring) { // 측정 중일 때만 프로세스바 표시
            LinearProgressIndicator(
                progress = timerProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = Color.Green
            )
        }
    }
}

// 프리뷰 추가
@Preview(showBackground = true)
@Composable
fun MeasurementScreenPreview() {
    Test3Theme {
        MeasurementScreen(onBackClick = {})
    }
}
