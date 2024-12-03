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
import java.io.IOException
import kotlin.math.pow
import org.apache.commons.math3.fitting.WeightedObservedPoint
import org.apache.commons.math3.fitting.GaussianCurveFitter
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction


class DataControl(private val context: Context) {

    // 센서 데이터 저장 배열
    private val sensor_1 = MutableList(20) { 0.0 }
    private val sensor_2 = MutableList(20) { 0.0 }
    private val sensor_3 = MutableList(20) { 0.0 }
    private val sensor_4 = MutableList(20) { 0.0 }
    private val sensor_5 = MutableList(20) { 0.0 }
    private var currentIndex = 0 // 데이터를 저장할 인덱스
    private val sigma = 1.0 // 가우시안 분산값

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

            // 데이터 타입 0: 원본 데이터 저장
            for (i in 0 until currentIndex) {
                for (sensorId in 1..5) {
                    val sensorObject = JSONObject()
                    sensorObject.put("data_type", 0)
                    sensorObject.put("sensor_id", sensorId)
                    sensorObject.put("time", i + 1)
                    sensorObject.put("value", getSensorValue(sensorId, i).format(2))
                    sensorDataArray.put(sensorObject)
                }
            }

            // 데이터 타입 1: 선형 회귀로 처리
            val regressedData = applyLinearRegression()
            for (i in regressedData[0].indices) {
                for (sensorId in 1..5) {
                    val sensorObject = JSONObject()
                    sensorObject.put("data_type", 1)
                    sensorObject.put("sensor_id", sensorId)
                    sensorObject.put("time", i + 1)
                    sensorObject.put("value", regressedData[sensorId - 1][i].format(2))
                    sensorDataArray.put(sensorObject)
                }
            }

            // 데이터 타입 2: 첫 번째 값을 기준으로 보정
            val offsetData = calculateOffsetsBasedOnFirstValue(regressedData)
            for (i in 0 until offsetData[0].size) {
                for (sensorId in 1..5) {
                    val sensorObject = JSONObject()
                    sensorObject.put("data_type", 2)
                    sensorObject.put("sensor_id", sensorId)
                    sensorObject.put("time", i + 1)
                    sensorObject.put("value", offsetData[sensorId - 1][i].format(2))
                    sensorDataArray.put(sensorObject)
                }
            }

            // 데이터 타입 3 저장: 센서 시작과 끝의 차이
            val differences = calculateDifferences(offsetData)
            val dataType3 = mutableListOf<Pair<Double, Double>>()
            for ((sensorId, value) in differences.withIndex()) {
                val sensorObject = JSONObject()
                sensorObject.put("data_type", 3)
                sensorObject.put("sensor_id", sensorId + 1)
                sensorObject.put("value", value.format(2))
                sensorDataArray.put(sensorObject)
                dataType3.add((sensorId + 1).toDouble() to value)
            }

            // 데이터 타입 4: Spline 보간 데이터 저장
            val splineData = calculateSplineInterpolation(dataType3)
            val splineObject = JSONObject()
            splineObject.put("data_type", 4)

            val interpolatedPoints = JSONArray()
            for (point in splineData.interpolatedPoints) {
                val pointObject = JSONObject()
                pointObject.put("x", point.first)
                pointObject.put("y", point.second)
                interpolatedPoints.put(pointObject)
            }
            splineObject.put("interpolated_points", interpolatedPoints)

            sensorDataArray.put(splineObject)

            // 데이터 타입 5 저장: max_x_value
            val maxValueObject = JSONObject()
            maxValueObject.put("data_type", 5)
            maxValueObject.put("max_x_value", splineData.maxXValue.format(2))
            sensorDataArray.put(maxValueObject)

            // JSON 객체 생성
            val rootObject = JSONObject()
            rootObject.put("sensor_data", sensorDataArray)

            // 파일 이름 생성 및 저장
            val currentTime = System.currentTimeMillis()
            val formattedTime = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault()).format(currentTime)
            val fileNameWithTime = "sensor_data_$formattedTime.json"

            val file = File(getVeinScopeFolder(), fileNameWithTime)
            file.writeText(rootObject.toString(4)) // JSON 저장

            fileNameWithTime
        } catch (e: Exception) {
            "Error: 센서 값 저장 중 오류 발생: ${e.message}"
        }
    }

    private fun applyLinearRegression(): List<List<Double>> {
        val regressed = List(5) { MutableList(20) { 0.0 } }
        for (sensorId in 1..5) {
            val sensor = getSensorArray(sensorId)

            // x, y 데이터를 준비
            val xValues = sensor.indices.map { it.toDouble() }
            val yValues = sensor

            // 선형 회귀를 위한 행렬 계산
            val xMatrix = Array2DRowRealMatrix(xValues.map { doubleArrayOf(1.0, it) }.toTypedArray())
            val yMatrix = Array2DRowRealMatrix(yValues.toDoubleArray())
            val solver: DecompositionSolver = LUDecomposition(xMatrix.transpose().multiply(xMatrix)).solver
            val coefficients = solver.solve(xMatrix.transpose().multiply(yMatrix))

            // 계수 추출 (y = a + bx)
            val a = coefficients.getEntry(0, 0)
            val b = coefficients.getEntry(1, 0)

            // 회귀 직선을 따라 y값 계산
            for (i in sensor.indices) {
                regressed[sensorId - 1][i] = a + b * i
            }
        }
        return regressed
    }

    // Spline 피팅 함수
    private fun calculateSplineInterpolation(dataType3: List<Pair<Double, Double>>): SplineData {
        val xValues = dataType3.map { it.first }.toDoubleArray()
        val yValues = dataType3.map { it.second }.toDoubleArray()

        // SplineInterpolator 생성
        val splineInterpolator = SplineInterpolator()
        val splineFunction: PolynomialSplineFunction = splineInterpolator.interpolate(xValues, yValues)

        // x 범위를 설정하고 y 값을 계산
        val interpolatedPoints = mutableListOf<Pair<Double, Double>>()
        val step = 0.1
        var x = xValues.minOrNull() ?: 0.0
        val maxX = xValues.maxOrNull() ?: 0.0
        while (x <= maxX) {
            interpolatedPoints.add(Pair(x, splineFunction.value(x)))
            x += step
        }

        // 최대값 계산 (y의 최대값과 그에 해당하는 x 값)
        val maxPoint = interpolatedPoints.maxByOrNull { it.second } ?: Pair(0.0, 0.0)

        return SplineData(interpolatedPoints, maxPoint.first)
    }

    private fun calculateDifferences(filteredData: List<List<Double>>): List<Double> {
        return List(5) { sensorId -> filteredData[sensorId].last() - filteredData[sensorId].first() }
    }

    private fun calculateOffsetsBasedOnFirstValue(filteredData: List<List<Double>>): List<List<Double>> {
        val offsets = mutableListOf<MutableList<Double>>()
        for (sensorId in 1..5) {
            // 각 센서의 첫 번째 데이터를 기준으로 보정
            val firstValue = filteredData[sensorId - 1].first()
            val offsetValues = filteredData[sensorId - 1].map { it - firstValue }.toMutableList()
            offsets.add(offsetValues)
        }
        return offsets
    }

    private fun getSensorArray(sensorId: Int): MutableList<Double> {
        return when (sensorId) {
            1 -> sensor_1
            2 -> sensor_2
            3 -> sensor_3
            4 -> sensor_4
            5 -> sensor_5
            else -> throw IllegalArgumentException("Invalid sensor ID")
        }
    }

    private fun getSensorValue(sensorId: Int, index: Int): Double {
        return when (sensorId) {
            1 -> sensor_1[index]
            2 -> sensor_2[index]
            3 -> sensor_3[index]
            4 -> sensor_4[index]
            5 -> sensor_5[index]
            else -> 0.0
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this).toDouble()

    private fun processReceivedData(dataList: List<String>) {
        try {
            for ((index, data) in dataList.withIndex()) {
                val values = data.split(" ").map { it.toDouble() }
                sensor_1[index] = values[0]
                sensor_2[index] = values[1]
                sensor_3[index] = values[2]
                sensor_4[index] = values[3]
                sensor_5[index] = values[4]
            }
            currentIndex = dataList.size
        } catch (e: Exception) {
            showToast("Error: 데이터 처리 중 오류 발생: ${e.message}")
        }
    }

    data class SplineData(
        val interpolatedPoints: List<Pair<Double, Double>>,
        val maxXValue: Double
    )

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
