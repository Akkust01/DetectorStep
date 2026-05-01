package com.example.detectorstep

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    // UI элементы
    private lateinit var tvStepCount: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvSensorInfo: TextView
    private lateinit var btnReset: Button

    // Сенсоры
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Переменные для подсчета шагов
    private var stepCount = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastTime = 0L

    // НАСТРОЙКИ ЧУВСТВИТЕЛЬНОСТИ
    private val threshold = 12.0f      // Порог срабатывания
    private val timeInterval = 300      // Минимальное время между шагами (мс)
    private val shakeCountToAlert = 10  // Каждые 10 шагов - уведомление

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация UI
        tvStepCount = findViewById(R.id.tvStepCount)
        tvStatus = findViewById(R.id.tvStatus)
        tvSensorInfo = findViewById(R.id.tvSensorInfo)
        btnReset = findViewById(R.id.btnReset)

        // Инициализация сенсоров
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Проверка наличия акселерометра
        checkSensorAvailability()

        // Кнопка сброса
        btnReset.setOnClickListener {
            resetStepCounter()
        }

        // Устанавливаем начальный статус
        updateStepCount()
    }

    // Проверка наличия акселерометра на устройстве
    private fun checkSensorAvailability() {
        if (accelerometer == null) {
            tvStatus.text = "❌ Акселерометр не найден!"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            tvSensorInfo.text = "Устройство не поддерживает датчик движения"
            btnReset.isEnabled = false
        } else {
            tvStatus.text = "✅ Акселерометр готов к работе"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            tvStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            tvSensorInfo.text = String.format(Locale.getDefault(),
                "Акселерометр: найден\nЧувствительность: %.1f", threshold)
        }
    }

    // Подключение к датчику при запуске приложения
    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            tvStatus.text = "🔵 Слушатель активен"
        }
    }

    // Отключение от датчика при сворачивании приложения
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        tvStatus.text = "⚫ Слушатель отключен"
    }

    // Обработка изменений показаний акселерометра
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val currentTime = System.currentTimeMillis()

            // Вычисляем изменение ускорения
            val deltaX = Math.abs(x - lastX)
            val deltaY = Math.abs(y - lastY)
            val deltaZ = Math.abs(z - lastZ)
            val totalDelta = deltaX + deltaY + deltaZ

            // ОПРЕДЕЛЕНИЕ ШАГА
            if (totalDelta > threshold && (currentTime - lastTime) > timeInterval) {
                stepCount++
                updateStepCount()
                lastTime = currentTime

                // Реакция на каждые 10 шагов
                if (stepCount % shakeCountToAlert == 0) {
                    onThresholdReached()
                }
            }

            // Сохраняем текущие значения для следующего сравнения
            lastX = x
            lastY = y
            lastZ = z

            // Обновляем отладочную информацию
            updateSensorInfo(x, y, z, totalDelta)
        }
    }

    // Изменение точности сенсора
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val accuracyText = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Низкая"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Средняя"
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Высокая"
            else -> "Неизвестно"
        }
        tvSensorInfo.text = "Точность: $accuracyText"
    }

    // Обновление счетчика на экране
    private fun updateStepCount() {
        runOnUiThread {
            tvStepCount.text = stepCount.toString()
        }
    }

    // Обновление информации о сенсоре
    private fun updateSensorInfo(x: Float, y: Float, z: Float, delta: Float) {
        runOnUiThread {
            tvSensorInfo.text = String.format(Locale.getDefault(),
                "X: %.2f | Y: %.2f | Z: %.2f\nИзменение: %.2f (порог: %.1f)",
                x, y, z, delta, threshold)
        }
    }

    // Действия при достижении порога (каждые 10 шагов)
    private fun onThresholdReached() {
        // 1. ВИБРАЦИЯ
        vibratePhone(300)

        // 2. ЗВУК
        playNotificationSound()

        // 3. ИЗМЕНЕНИЕ UI
        runOnUiThread {
            tvStatus.text = "🎉 Достигнут порог! Шагов: $stepCount"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            tvStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))

            // Меняем цвет счетчика
            if (stepCount % 20 == 0) {
                tvStepCount.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            } else {
                tvStepCount.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            }
        }

        // 4. TOAST УВЕДОМЛЕНИЕ
        Toast.makeText(this, "🔥 $stepCount шагов! Отличная работа!", Toast.LENGTH_SHORT).show()
    }

    // Вибрация телефона
    private fun vibratePhone(milliseconds: Long) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(milliseconds)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Воспроизведение звука уведомления
    private fun playNotificationSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            mediaPlayer.setOnCompletionListener { mp ->
                mp.release()
            }
            mediaPlayer.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Сброс счетчика шагов
    private fun resetStepCounter() {
        stepCount = 0
        updateStepCount()

        // Обновляем статус
        tvStatus.text = "⚪ Счетчик сброшен"
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
        tvStatus.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        tvStepCount.setTextColor(ContextCompat.getColor(this, android.R.color.black))

        // Вибрация при сбросе
        vibratePhone(100)
        Toast.makeText(this, "Счетчик обнулен", Toast.LENGTH_SHORT).show()
    }
}