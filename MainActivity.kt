package com.example.test3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.test3.ui.theme.Test3Theme
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Test3Theme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // BluetoothManager 관리
    val bluetoothManager = remember { BluetoothManager(context) }
    var isBluetoothConnected by remember { mutableStateOf(false) }

    // 연결 상태 업데이트
    LaunchedEffect(bluetoothManager) {
        isBluetoothConnected = bluetoothManager.isConnected()
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                navController = navController,
                isBluetoothConnected = isBluetoothConnected,
                onConnect = {
                    coroutineScope.launch {
                        val connected = bluetoothManager.connectToDevice("HC-06")
                        isBluetoothConnected = connected
                    }
                }
            )
        }

        composable("measurement") {
            MeasurementScreen(
                onBackClick = { navController.popBackStack() },
                bluetoothManager = bluetoothManager,
                dataControl = DataControl(context = LocalContext.current)
            )
        }

        composable("manual") {
            ManualScreen(
                onBackClick = { navController.popBackStack() },
                bluetoothManager = bluetoothManager,
                dataControl = DataControl(context = LocalContext.current)
            )
        }

        composable("terminal") {
            TerminalScreen(
                onBackClick = { navController.popBackStack() },
                bluetoothManager = bluetoothManager
            )
        }

    }
}

@Composable
fun MainScreen(
    navController: NavController,
    isBluetoothConnected: Boolean,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 텍스트 : Infinity
        Text(
            text = "Infinity",
            fontSize = 100.sp,
            modifier = Modifier.padding(bottom = 50.dp)
        )

        // 버튼 : 측정 시작
        Button(
            onClick = { navController.navigate("measurement") },
            modifier = Modifier
                .width(300.dp)
                .padding(bottom = 16.dp),
            enabled = isBluetoothConnected
        ) {
            Text("측정 시작")
        }

        // 버튼 : 수동 조작
        Button(
            onClick = { navController.navigate("manual") },
            modifier = Modifier
                .width(300.dp)
                .padding(bottom = 16.dp),
            enabled = isBluetoothConnected
        ) {
            Text("수동 조작")
        }

        // 버튼 : Terminal
        Button(
            onClick = { navController.navigate("terminal") },
            modifier = Modifier
                .width(300.dp)
                .padding(bottom = 16.dp),
            enabled = isBluetoothConnected
        ) {
            Text("Terminal")
        }

        // 버튼 : 블루투스 연결
        Button(
            onClick = { onConnect() },
            modifier = Modifier.width(300.dp),
            enabled = !isBluetoothConnected
        ) {
            Text("블루투스 연결")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    Test3Theme {
        val navController = rememberNavController()
        MainScreen(
            navController = navController,
            isBluetoothConnected = false,
            onConnect = {}
        )
    }
}