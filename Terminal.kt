package com.example.test3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.example.test3.ui.theme.Test3Theme
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

@Composable
fun TerminalScreen(
    onBackClick: () -> Unit,
    bluetoothManager: BluetoothManager? = null,
    receivedData: String = ""
) {
    var inputText by remember { mutableStateOf("") }
    var terminalLog by remember { mutableStateOf(listOf<String>()) }
    val context = LocalContext.current

    // 블루투스 데이터 수신
    LaunchedEffect(receivedData) {
        bluetoothManager?.receiveData(
            updateLog = { message ->
                terminalLog = listOf(message) + terminalLog
            },
            onDisconnected = {
                Toast.makeText(context, "블루투스 연결이 끊어졌습니다.", Toast.LENGTH_SHORT).show()
                terminalLog = listOf("Error: Bluetooth disconnected.") + terminalLog
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            reverseLayout = true
        ) {
            items(terminalLog.size) { index ->
                Text(
                    text = terminalLog[index],
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp)) // 라운드 추가
                    .background(Color.LightGray),
                contentAlignment = Alignment.CenterStart // 텍스트 수직 중앙 정렬
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        color = Color.Black
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            bluetoothManager?.sendData(inputText) { log ->
                                terminalLog = listOf(log) + terminalLog
                            }
                            inputText = ""
                        }
                    }),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        bluetoothManager?.sendData(inputText) { log ->
                            terminalLog = listOf(log) + terminalLog
                        }
                        inputText = ""
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("전송")
            }
        }
    }
}

// 프리뷰 추가
@Preview(showBackground = true)
@Composable
fun TerminalScreenPreview() {
    Test3Theme {
        TerminalScreen(onBackClick = {})
    }
}
