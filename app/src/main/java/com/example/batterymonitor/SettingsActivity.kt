package com.example.batterymonitor

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.batterymonitor.model.SettingsRequest
import com.example.batterymonitor.network.ApiService
import com.example.batterymonitor.network.RetrofitClient
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class SettingsActivity : AppCompatActivity(), CoroutineScope {

    private lateinit var apiService: ApiService
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var etCapacity: EditText
    private lateinit var etVoltageOffset: EditText
    private lateinit var etCurrentOffset: EditText
    private lateinit var etMvPerAmp: EditText
    private lateinit var etChargeThreshold: EditText
    private lateinit var etDischargeThreshold: EditText
    private lateinit var etCurrentDeadzone: EditText   // ✅ Added variable
    private lateinit var etEspIp: EditText
    private lateinit var etSoc: TextInputEditText
    private var canStopAnimation = false

    private lateinit var btnSaveSettings: Button
    private lateinit var refreshButtonLayout: LinearLayout
    private lateinit var ivRefreshIcon: ImageView
    private lateinit var tvRefreshText: TextView
    private lateinit var tvSettingsStatus: TextView
    private lateinit var ivCalculateIcon: ImageView

    private val ESP_IP_KEY = "esp_ip"
    private var espIp: String = "192.168.1.99"

    @SuppressLint("SetTextI18n", "MissingInflatedId", "DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize UI components
        etCapacity = findViewById(R.id.etCapacity)
        etVoltageOffset = findViewById(R.id.etVoltageOffset)
        etCurrentOffset = findViewById(R.id.etCurrentOffset)
        etMvPerAmp = findViewById(R.id.etMvPerAmp)
        etChargeThreshold = findViewById(R.id.etChargeThreshold)
        etDischargeThreshold = findViewById(R.id.etDischargeThreshold)
        etCurrentDeadzone = findViewById(R.id.etCurrentDeadzone)
        etEspIp = findViewById(R.id.etEspIp)
        etSoc = findViewById(R.id.etSoc)
        ivCalculateIcon = findViewById(R.id.ivCalculateIcon)

        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        refreshButtonLayout = findViewById(R.id.refreshButtonLayout)
        ivRefreshIcon = findViewById(R.id.ivRefreshIcon)
        tvRefreshText = findViewById(R.id.tvRefreshText)
        tvSettingsStatus = findViewById(R.id.tvSettingsStatus)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Load ESP IP from shared preferences or intent extras
        val prefs = getSharedPreferences("ESP_PREFS", MODE_PRIVATE)
        espIp = prefs.getString(ESP_IP_KEY, intent.getStringExtra("espIp")) ?: espIp
        etEspIp.setText(espIp)

        setupApiService()
        fetchSettings()
        startVibrationAnimation()

        btnSaveSettings.setOnClickListener { saveSettings() }

        // Calculate SOC button click handling with alert dialog
        findViewById<LinearLayout>(R.id.calculateButtonLayout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("SOC Estimation Notice")
                .setMessage(
                    "This will estimate SOC based on battery voltage.\n\n" +
                            "⚠ Lead-acid SOC estimation from voltage is approximate and works best when the battery is at rest (no charging/discharging for at least 2–4 hours).\n" +
                            "Use at your own risk."
                )
                .setPositiveButton("OK") { _, _ ->
                    tvSettingsStatus.text = "Calculating SOC..."
                    startVibrationAnimation()

                    launch {
                        try {
                            val liveData = withContext(Dispatchers.IO) { apiService.getLiveData() }

                            if (!liveData.status.equals("Idle", ignoreCase = true)) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@SettingsActivity,
                                        "Battery must be idle for accurate SOC estimation.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    tvSettingsStatus.text = "Calculation aborted."
                                    stopVibrationAnimation()
                                }
                                return@launch
                            }

                            val approxSoc = calculateLeadAcidSoc(liveData.voltage)
                            withContext(Dispatchers.Main) {
                                etSoc.setText(String.format("%.1f", approxSoc))
                                tvSettingsStatus.text = "Estimated SOC: ${"%.1f".format(approxSoc)}%"
                                stopVibrationAnimation()
                            }

                        } catch (e: Exception) {
                            Log.e("SettingsActivity", "Error calculating SOC", e)
                            withContext(Dispatchers.Main) {
                                tvSettingsStatus.text = "Error: ${e.localizedMessage}"
                                stopVibrationAnimation()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    stopVibrationAnimation()
                }
                .show()
        }

        // Refresh button click handling
        refreshButtonLayout.setOnClickListener {
            tvSettingsStatus.text = "Refreshing..."
            startIconRotation()
            fetchSettings {
                stopIconRotation()
            }
        }
    }

    private fun setupApiService() {
        apiService = RetrofitClient.getClient("http://$espIp/").create(ApiService::class.java)
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun fetchSettings(onComplete: (() -> Unit)? = null) {
        launch {
            try {
                val settings = withContext(Dispatchers.IO) { apiService.getSettings() }
                etCapacity.setText(settings.capacity_ah.toString())
                etVoltageOffset.setText(settings.voltage_offset.toString())
                etCurrentOffset.setText(settings.current_offset.toString())
                etMvPerAmp.setText(settings.mv_per_amp.toString())
                etChargeThreshold.setText(settings.charge_threshold.toString())
                etDischargeThreshold.setText(settings.discharge_threshold.toString())
                etCurrentDeadzone.setText(settings.current_deadzone.toString())
                etSoc.setText(String.format("%.1f", settings.soc))
                tvSettingsStatus.text = "Settings loaded."
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error fetching settings", e)
                tvSettingsStatus.text = "Error fetching settings: ${e.localizedMessage}"
            } finally {
                onComplete?.invoke()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun saveSettings() {
        btnSaveSettings.isEnabled = false
        tvSettingsStatus.text = "Saving..."

        val capacity = etCapacity.text.toString().toFloatOrNull()
        val voltageOffset = etVoltageOffset.text.toString().toFloatOrNull()
        val currentOffset = etCurrentOffset.text.toString().toFloatOrNull()
        val mvPerAmp = etMvPerAmp.text.toString().toFloatOrNull()
        val chargeThreshold = etChargeThreshold.text.toString().toFloatOrNull()
        val dischargeThreshold = etDischargeThreshold.text.toString().toFloatOrNull()
        val currentDeadzone = etCurrentDeadzone.text.toString().toFloatOrNull()
        val socValue = etSoc.text?.toString()?.toFloatOrNull()

        // ✅ Added currentDeadzone to validation
        if (listOf(capacity, voltageOffset, currentOffset, mvPerAmp, chargeThreshold, dischargeThreshold, currentDeadzone, socValue).any { it == null }) {
            tvSettingsStatus.text = "Invalid input. Please check all fields."
            btnSaveSettings.isEnabled = true
            return
        }

        val settingsRequest = SettingsRequest(
            capacity_ah = capacity!!,
            voltage_offset = voltageOffset!!,
            current_offset = currentOffset!!,
            mv_per_amp = mvPerAmp!!,
            charge_threshold = chargeThreshold!!,
            discharge_threshold = dischargeThreshold!!,
            current_deadzone = currentDeadzone!!, // ✅ send to ESP
            soc = socValue!!
        )

        launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    apiService.updateSettings(settingsRequest)
                }

                if (resp.isSuccessful) {
                    tvSettingsStatus.text = "Settings saved successfully!"
                } else {
                    tvSettingsStatus.text = "Failed: ${resp.code()}"
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error saving settings", e)
                tvSettingsStatus.text = "Error: ${e.localizedMessage}"
            } finally {
                btnSaveSettings.isEnabled = true
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun calculateLeadAcidSoc(voltage: Float): Float {
        val table = listOf(
            12.73f to 100f,
            12.62f to 90f,
            12.50f to 80f,
            12.37f to 70f,
            12.24f to 60f,
            12.10f to 50f,
            11.96f to 40f,
            11.81f to 30f,
            11.66f to 20f,
            11.51f to 10f,
            10.50f to 0f
        )

        if (voltage >= table.first().first) return 100f
        if (voltage <= table.last().first) return 0f

        for (i in 0 until table.size - 1) {
            val (vHigh, socHigh) = table[i]
            val (vLow, socLow) = table[i + 1]
            if (voltage <= vHigh && voltage >= vLow) {
                val ratio = (voltage - vLow) / (vHigh - vLow)
                return socLow + ratio * (socHigh - socLow)
            }
        }
        return 0f
    }

    private fun startIconRotation() {
        canStopAnimation = false

        val rotate = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
        }
        ivRefreshIcon.startAnimation(rotate)

        ivRefreshIcon.postDelayed({
            canStopAnimation = true
            stopIconRotation()
        }, 2000)
    }

    private fun stopIconRotation() {
        if (canStopAnimation) {
            ivRefreshIcon.clearAnimation()
        }
    }

    private fun startVibrationAnimation() {
        val vibration = TranslateAnimation(
            -5f, 5f,
            0f, 0f
        ).apply {
            duration = 100
            repeatMode = Animation.REVERSE
            repeatCount = 10
            interpolator = LinearInterpolator()
        }
        ivCalculateIcon.startAnimation(vibration)
    }

    private fun stopVibrationAnimation() {
        ivCalculateIcon.clearAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
