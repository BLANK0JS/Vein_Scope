package com.example.test3

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.example.test3.ui.theme.Test3Theme
import kotlinx.coroutines.launch

@Composable
fun ManualScreen(
    onBackClick: () -> Unit,
    bluetoothManager: BluetoothManager? = null,
    dataControl: DataControl? = null
) {
    val context = LocalContext.current
    var sliderValue by remember { mutableStateOf(90f) } // 초기값 90
    var log by remember { mutableStateOf(listOf<String>()) }
    val coroutineScope = rememberCoroutineScope()

    // 데이터 수신
    LaunchedEffect(bluetoothManager) {
        bluetoothManager?.receiveData(
            updateLog = { message ->
                log = listOf(message) + log

                // "end" 데이터 수신 처리
                if (message == "Recv : end") {
                    coroutineScope.launch {
                        val storedData = bluetoothManager.getStoredData() // 저장된 데이터 가져오기

                        // 데이터가 비어있는 경우 오류 메시지 추가
                        if (storedData.any { it.isBlank() }) {
                            log = listOf("Error: 데이터가 완전히 수신되지 않았습니다.") + log
                        } else {
                            val saveResult = dataControl?.saveSensorValues(storedData)
                            if (saveResult != null) {
                                if (saveResult.startsWith("Error")) {
                                    log = listOf(saveResult) + log
                                } else {
                                    log = listOf("데이터 저장 완료: $saveResult") + log
                                    Toast.makeText(context, "모든 데이터 처리가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            },
            onDisconnected = {
                Toast.makeText(context, "블루투스 연결이 끊어졌습니다.", Toast.LENGTH_SHORT).show()
                log = listOf("Error: Bluetooth disconnected.") + log
                onBackClick() // 연결이 끊어지면 이전 화면으로 돌아감
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 측정 시작 버튼
        Button(
            onClick = {
                bluetoothManager?.sendData("start") { newLog ->
                    log = listOf(newLog) + log
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(text = "측정 시작", color = Color.White, fontSize = 18.sp)
        }

        // 레이저 ON/OFF 버튼
        Text(
            text = "레이저",
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    bluetoothManager?.sendData("on") { newLog ->
                        log = listOf(newLog) + log
                    }
                },
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text("ON", fontSize = 16.sp)
            }
            Button(
                onClick = {
                    bluetoothManager?.sendData("off") { newLog ->
                        log = listOf(newLog) + log
                    }
                },
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Text("OFF", fontSize = 16.sp)
            }
        }

        // 서보모터 각도 슬라이더
        Text(
            text = "서보모터 각도: ${sliderValue.toInt()}",
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 16.dp)
        )
        Slider(
            value = sliderValue,
            onValueChange = { newValue -> sliderValue = newValue },
            valueRange = 70f..110f,
            steps = 39,
            onValueChangeFinished = {
                bluetoothManager?.sendData(sliderValue.toInt().toString()) { newLog ->
                    log = listOf(newLog) + log
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // 로그 표시 (스크롤 가능)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
                .background(Color.LightGray)
                .clip(RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            log.forEach { logEntry ->
                Text(
                    text = logEntry,
                    fontSize = 16.sp,
                    color = if (logEntry.startsWith("Error")) Color.Red else Color.Black, // 에러 로그 강조
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ManualScreenWithReceivePreview() {
    Test3Theme {
        ManualScreen(
            onBackClick = {}
        )
    }
}
