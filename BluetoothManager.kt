package com.example.test3

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class BluetoothManager(private val context: Context) {
    private var bluetoothSocket: BluetoothSocket? = null
    private val accumulatedDataRef = AtomicReference("") // 누적 데이터 관리
    private val sensorDataBuffer = Array(20) { "" } // 데이터를 임시 저장할 배열 (20개)
    private var dataIndex = 0 // 데이터가 저장될 인덱스
    private var isReceiving = false // 데이터 수신 활성 상태

    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected ?: false
    }

    // 블루투스 장치 연결
    suspend fun connectToDevice(deviceName: String = "HC-06"): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            showToast("블루투스를 지원하지 않는 장치입니다.")
            return false
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showToast("블루투스 권한이 필요합니다.")
            return false
        }

        if (!bluetoothAdapter.isEnabled) {
            showToast("블루투스가 비활성화되어 있습니다. 활성화해주세요.")
            return false
        }

        val pairedDevices = bluetoothAdapter.bondedDevices
        val targetDevice = pairedDevices.find { it.name == deviceName }

        if (targetDevice == null) {
            showToast("$deviceName 장치가 페어링되어 있지 않습니다.")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val socket = targetDevice.createRfcommSocketToServiceRecord(targetDevice.uuids[0].uuid)
                socket.connect()
                bluetoothSocket = socket
                withContext(Dispatchers.Main) {
                    showToast("$deviceName 연결에 성공했습니다.")
                }
                true
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showToast("$deviceName 연결에 실패했습니다.")
                }
                false
            }
        }
    }

    // 데이터 전송
    fun sendData(message: String, updateLog: (String) -> Unit) {
        if (bluetoothSocket == null) {
            updateLog("Error: 블루투스 장치가 연결되지 않았습니다.")
            return
        }

        try {
            val outputStream: OutputStream = bluetoothSocket!!.outputStream
            outputStream.write(message.toByteArray())
            updateLog("Send : $message")

            // "start" 명령 시 초기화
            if (message == "start") {
                sensorDataBuffer.fill("") // 배열 초기화
                dataIndex = 0
                isReceiving = true // 데이터 수신 시작
            }
        } catch (e: IOException) {
            e.printStackTrace()
            updateLog("Error: 데이터 전송 실패")
        }
    }

    // 데이터 수신 및 저장
    suspend fun receiveData(
        updateLog: (String) -> Unit,
        onDisconnected: () -> Unit
    ) {
        if (bluetoothSocket == null) {
            updateLog("Error: 블루투스 장치가 연결되지 않았습니다.")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream = bluetoothSocket!!.inputStream
                val buffer = ByteArray(1024)

                while (true) {
                    try {
                        val bytes = inputStream.read(buffer)
                        val receivedChunk = String(buffer, 0, bytes)
                        val currentData = accumulatedDataRef.get()
                        val newData = currentData + receivedChunk

                        if (newData.contains("\n")) {
                            val splitIndex = newData.indexOf("\n")
                            val completeMessage = newData.substring(0, splitIndex).trim()

                            withContext(Dispatchers.Main) {
                                updateLog("Recv : $completeMessage")
                            }

                            // 데이터 처리
                            if (isReceiving) {
                                if (completeMessage == "end") {
                                    isReceiving = false // 데이터 수신 중지
                                } else {
                                    // 배열에 데이터를 저장
                                    if (dataIndex < sensorDataBuffer.size) {
                                        sensorDataBuffer[dataIndex] = completeMessage
                                        dataIndex++
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            updateLog("Error: 데이터 버퍼가 초과되었습니다.")
                                        }
                                    }
                                }
                            }

                            accumulatedDataRef.set(newData.substring(splitIndex + 1))
                        } else {
                            accumulatedDataRef.set(newData)
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            updateLog("Error: 데이터 수신 실패")
                            onDisconnected()
                        }
                        break
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    updateLog("Error: 블루투스 통신 실패")
                    onDisconnected()
                }
            }
        }
    }

    // 저장된 데이터 반환 및 초기화
    fun getStoredData(): List<String> {
        // 배열에 빈 값이 있으면 오류 메시지 반환
        if (sensorDataBuffer.any { it.isBlank() }) {
            return listOf("Error: 데이터가 완전히 수신되지 않았습니다.")
        }

        return sensorDataBuffer.toList() // 데이터를 반환
    }

    // 연결 해제
    fun disconnect() {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
            showToast("블루투스 연결이 해제되었습니다.")
        } catch (e: IOException) {
            showToast("연결 해제 실패")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}