package com.example.test3

import android.content.Context
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.os.Environment
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.DecompositionSolver
import org.apache.commons.math3.linear.LUDecomposition
import org.apache.commons.math3.linear.RealMatrix
import kotlin.math.pow


class DataControl(private val context: Context) {

    // 센서 데이터 저장 배열
    private val sensor_1 = MutableList(20) { 0.0 }
    private val sensor_2 = MutableList(20) { 0.0 }
    private val sensor_3 = MutableList(20) { 0.0 }
    private val sensor_4 = MutableList(20) { 0.0 }
    private val sensor_5 = MutableList(20) { 0.0 }
    private var currentIndex = 0 // 데이터를 저장할 인덱스

    // Vein_Scope 폴더 생성 및 경로 가져오기
    private fun getVeinScopeFolder(): File {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vein_Scope")
        if (!folder.exists()) {
            folder.mkdirs() // 폴더가 없으면 생성
        }
        return folder
    }

    fun saveSensorValues(dataList: List<String>): String {
        return try {
            // 받은 데이터를 각 센서 변수에 저장
            processReceivedData(dataList)

            // JSON 데이터 생성 및 저장
            val sensorDataArray = JSONArray()

            for (i in 0 until currentIndex) {
                for (sensorId in 1..5) { // 센서 ID 1~5
                    val sensorObject = JSONObject()
                    sensorObject.put("data_type", 0) // 원본 데이터 타입
                    sensorObject.put("sensor_id", sensorId)
                    sensorObject.put("time", i + 1) // 측정 순서 (1~20)
                    sensorObject.put(
                        "value",
                        when (sensorId) {
                            1 -> sensor_1[i].format(2)
                            2 -> sensor_2[i].format(2)
                            3 -> sensor_3[i].format(2)
                            4 -> sensor_4[i].format(2)
                            5 -> sensor_5[i].format(2)
                            else -> 0.0
                        }
                    )
                    sensorDataArray.put(sensorObject)
                }
            }

            // JSON 객체 생성
            val rootObject = JSONObject()
            rootObject.put("sensor_data", sensorDataArray)

            // 현재 시간 가져오기
            val currentTime = System.currentTimeMillis()
            val formattedTime = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(currentTime)

            // 파일 이름에 시간 추가
            val fileNameWithTime = "sensor_data_$formattedTime.json"

            // JSON 파일 저장 (Vein_Scope 폴더에)
            val file = File(getVeinScopeFolder(), fileNameWithTime)
            file.writeText(rootObject.toString(4)) // JSON 저장 (4는 들여쓰기)

            // 성공적으로 저장된 파일 이름 반환
            fileNameWithTime
        } catch (e: Exception) {
            // 에러 발생 시 반환 메시지
            "Error: 센서 값 저장 중 오류 발생: ${e.message}"
        }
    }

    fun applyOffsetAndAppend(jsonFileName: String): String {
        return try {
            // JSON 파일 읽기
            val file = File(getVeinScopeFolder(), jsonFileName)
            if (!file.exists()) {
                return "Error: 파일이 존재하지 않습니다."
            }

            val jsonData = file.readText()
            val rootObject = JSONObject(jsonData)
            val sensorDataArray = rootObject.getJSONArray("sensor_data")

            // 오프셋 파일 읽기
            val offsetFile = File(getVeinScopeFolder(), "offset_values.json")
            if (!offsetFile.exists()) {
                return "Error: offset_values.json 파일이 존재하지 않습니다."
            }

            val offsetData = JSONObject(offsetFile.readText())
            val offsets = listOf(
                offsetData.getDouble("sensor_1_offset"),
                offsetData.getDouble("sensor_2_offset"),
                offsetData.getDouble("sensor_3_offset"),
                offsetData.getDouble("sensor_4_offset"),
                offsetData.getDouble("sensor_5_offset")
            )

            // 센서 데이터 로드
            for (i in 0 until sensorDataArray.length()) {
                val sensorObject = sensorDataArray.getJSONObject(i)
                if (sensorObject.getInt("data_type") == 0) { // 원본 데이터만 로드
                    val sensorId = sensorObject.getInt("sensor_id")
                    val time = sensorObject.getInt("time")
                    val value = sensorObject.getDouble("value")

                    when (sensorId) {
                        1 -> sensor_1[time - 1] = value
                        2 -> sensor_2[time - 1] = value
                        3 -> sensor_3[time - 1] = value
                        4 -> sensor_4[time - 1] = value
                        5 -> sensor_5[time - 1] = value
                    }
                }
            }

            // Offset 데이터를 기존 JSON에 추가
            for (i in 0 until 20) {
                for (sensorId in 1..5) {
                    val sensorObject = JSONObject()
                    sensorObject.put("data_type", 1) // Offset 데이터 타입
                    sensorObject.put("sensor_id", sensorId)
                    sensorObject.put("time", i + 1)
                    sensorObject.put(
                        "value",
                        when (sensorId) {
                            1 -> (sensor_1[i] + offsets[0]).format(2)
                            2 -> (sensor_2[i] + offsets[1]).format(2)
                            3 -> (sensor_3[i] + offsets[2]).format(2)
                            4 -> (sensor_4[i] + offsets[3]).format(2)
                            5 -> (sensor_5[i] + offsets[4]).format(2)
                            else -> 0.0
                        }
                    )
                    sensorDataArray.put(sensorObject)
                }
            }

            // JSON 파일에 덮어쓰기
            file.writeText(rootObject.toString(4)) // JSON 덮어쓰기 (4는 들여쓰기)

            // 결과 반환
            jsonFileName
        } catch (e: Exception) {
            "Error: Offset 데이터 처리 중 오류 발생: ${e.message}"
        }
    }

    fun applyDifferentiationAndAppend(jsonFileName: String): String {
        return try {
            // JSON 파일 읽기
            val file = File(getVeinScopeFolder(), jsonFileName)
            if (!file.exists()) {
                return "Error: 파일이 존재하지 않습니다."
            }

            val jsonData = file.readText()
            val rootObject = JSONObject(jsonData)
            val sensorDataArray = rootObject.getJSONArray("sensor_data")

            // 센서 데이터 로드
            for (i in 0 until sensorDataArray.length()) {
                val sensorObject = sensorDataArray.getJSONObject(i)
                if (sensorObject.getInt("data_type") == 1) { // Offset 적용된 데이터만 로드
                    val sensorId = sensorObject.getInt("sensor_id")
                    val time = sensorObject.getInt("time")
                    val value = sensorObject.getDouble("value")

                    when (sensorId) {
                        1 -> sensor_1[time - 1] = value
                        2 -> sensor_2[time - 1] = value
                        3 -> sensor_3[time - 1] = value
                        4 -> sensor_4[time - 1] = value
                        5 -> sensor_5[time - 1] = value
                    }
                }
            }

            // 미분 계산
            val differentiatedSensor1 = differentiate(sensor_1)
            val differentiatedSensor2 = differentiate(sensor_2)
            val differentiatedSensor3 = differentiate(sensor_3)
            val differentiatedSensor4 = differentiate(sensor_4)
            val differentiatedSensor5 = differentiate(sensor_5)

            // 미분 데이터를 기존 JSON에 추가
            for (i in 1 until 20) { // 미분은 1번부터 시작
                for (sensorId in 1..5) {
                    val sensorObject = JSONObject()
                    sensorObject.put("data_type", 2) // 미분 데이터 타입
                    sensorObject.put("sensor_id", sensorId)
                    sensorObject.put("time", i + 1)
                    sensorObject.put(
                        "value",
                        when (sensorId) {
                            1 -> differentiatedSensor1[i - 1].format(2)
                            2 -> differentiatedSensor2[i - 1].format(2)
                            3 -> differentiatedSensor3[i - 1].format(2)
                            4 -> differentiatedSensor4[i - 1].format(2)
                            5 -> differentiatedSensor5[i - 1].format(2)
                            else -> 0.0
                        }
                    )
                    sensorDataArray.put(sensorObject)
                }
            }

            // JSON 파일에 덮어쓰기
            file.writeText(rootObject.toString(4)) // JSON 덮어쓰기 (4는 들여쓰기)

            // 결과 반환
            jsonFileName
        } catch (e: Exception) {
            "Error: 미분 데이터 처리 중 오류 발생: ${e.message}"
        }
    }

    private fun differentiate(data: List<Double>): List<Double> {
        return data.zipWithNext { a, b -> b - a }
    }

    fun saveOffsetValues(dataList: List<String>): String {
        return try {
            // 받은 데이터를 각 센서 변수에 저장
            processReceivedData(dataList)

            // 실수형 변수 5개 초기화
            var sumSensor1 = 0.0
            var sumSensor2 = 0.0
            var sumSensor3 = 0.0
            var sumSensor4 = 0.0
            var sumSensor5 = 0.0

            // 센서 데이터 20개의 합계 계산
            for (i in 0 until currentIndex) {
                sumSensor1 += sensor_1[i]
                sumSensor2 += sensor_2[i]
                sumSensor3 += sensor_3[i]
                sumSensor4 += sensor_4[i]
                sumSensor5 += sensor_5[i]
            }

            // 평균값 계산
            val avgSensor1 = sumSensor1 / currentIndex
            val avgSensor2 = sumSensor2 / currentIndex
            val avgSensor3 = sumSensor3 / currentIndex
            val avgSensor4 = sumSensor4 / currentIndex
            val avgSensor5 = sumSensor5 / currentIndex

            // 1번 센서를 기준으로 오프셋 값 계산
            val offsetSensor1 = 0.0 // 기준 센서는 0으로 설정
            val offsetSensor2 = (avgSensor1 - avgSensor2).format(2)
            val offsetSensor3 = (avgSensor1 - avgSensor3).format(2)
            val offsetSensor4 = (avgSensor1 - avgSensor4).format(2)
            val offsetSensor5 = (avgSensor1 - avgSensor5).format(2)

            // JSON 생성
            val offsetObject = JSONObject()
            offsetObject.put("sensor_1_offset", offsetSensor1)
            offsetObject.put("sensor_2_offset", offsetSensor2)
            offsetObject.put("sensor_3_offset", offsetSensor3)
            offsetObject.put("sensor_4_offset", offsetSensor4)
            offsetObject.put("sensor_5_offset", offsetSensor5)

            // JSON 파일 저장 (Vein_Scope 폴더에)
            val file = File(getVeinScopeFolder(), "offset_values.json")
            file.writeText(offsetObject.toString(4)) // JSON 저장 (4는 들여쓰기)

            // 결과 반환 메시지
            "오프셋 값 계산 및 저장 완료: ${offsetObject.toString(4)}"
        } catch (e: Exception) {
            // 에러 발생 시 반환 메시지
            "Error: 오프셋 값 계산 중 오류 발생: ${e.message}"
        }
    }

    // 소수점 자리수 제한 함수
    private fun Double.format(digits: Int) = "%.${digits}f".format(this).toDouble()

    // 받은 데이터를 구분하고 각 배열에 저장하는 함수
    private fun processReceivedData(dataList: List<String>) {
        try {
            if (dataList.size > 20) {
                showToast("Error: 데이터 리스트의 크기가 20을 초과했습니다.")
                return
            }

            // 데이터를 하나씩 처리
            for ((index, data) in dataList.withIndex()) {
                // 데이터 " " 기준으로 나누기
                val values = data.trim().split(" ").map { it.toDoubleOrNull() }

                if (values.size != 5 || values.any { it == null }) {
                    showToast("Error: 데이터 형식이 잘못되었습니다.")
                    return
                }

                // 데이터 배열에 저장
                sensor_1[index] = values[0] ?: 0.0
                sensor_2[index] = values[1] ?: 0.0
                sensor_3[index] = values[2] ?: 0.0
                sensor_4[index] = values[3] ?: 0.0
                sensor_5[index] = values[4] ?: 0.0
            }

            currentIndex = dataList.size // 현재 인덱스를 리스트 크기로 설정
            showToast("데이터가 성공적으로 처리되었습니다.")
        } catch (e: Exception) {
            showToast("Error: 데이터 처리 중 오류 발생: ${e.message}")
        }
    }

    // Toast 메시지 표시 함수
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

}
