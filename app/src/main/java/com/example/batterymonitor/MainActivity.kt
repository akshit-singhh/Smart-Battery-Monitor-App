package com.example.batterymonitor

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.batterymonitor.network.ApiService
import com.example.batterymonitor.network.RetrofitClient
import com.example.batterymonitor.model.WiFiRequest
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Response
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import com.example.batterymonitor.views.GradientCircularProgressView
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvEspIp: TextView
    private lateinit var ivWifiSignal: ImageView
    private lateinit var tvSerialLog: TextView
    private lateinit var svSerialLog: ScrollView
    private lateinit var btnScanQr: Button
    private lateinit var btnChangeWiFi: Button
    private lateinit var btnShowSettings: Button
    private lateinit var btnReboot: Button
    private lateinit var btnRecheckConnection: ImageView
    private lateinit var gradientProgress: GradientCircularProgressView
    private lateinit var etEditEspIp: EditText
    private lateinit var btnEditIp: ImageView
    private lateinit var btnSaveIp: ImageView

    private lateinit var tvVoltage: TextView
    private lateinit var tvCurrent: TextView
    private lateinit var tvPower: TextView
    private lateinit var tvSOC: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvEspMode: TextView
    private lateinit var mainContentLayout: LinearLayout
    private lateinit var fragmentContainer: FrameLayout
    private lateinit var tvBatteryStatus: TextView
    private lateinit var btnClearLogs: Button

    // NEW: Theme toggle icon and theme preference key
    private lateinit var themeToggleIcon: ImageView
    private val PREFS_NAME = "theme_prefs"
    private val THEME_KEY = "theme_is_dark"

    // default IP
    private var espIp: String = "192.168.1.00" // fallback default, can be replaced after detection
    // ESP IP will be fetched dynamically from API
    private lateinit var apiService: ApiService
    private var lastFetchedLog: String = ""
    private var logsCleared = false

    // coroutine scope
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    // polling job (single)
    private var pollingJob: Job? = null
    // separate logs job (slower)
    private var logsJob: Job? = null

    private var pollingForStaIp = false
    private var staIpPollJob: Job? = null


    // keep track of last wifi icon state to avoid unnecessary re-draws
    private var lastBarsToFill: Int = -1
    private var lastSignalState: SignalState = SignalState.NONE
    private enum class SignalState { NO_SIGNAL, NORMAL, BLINKING, NONE }

    // store mode confirmed from API; if non-null API has priority
    private var currentModeFromApi: String? = null

    private val WIFI_PERMISSION_REQUEST = 3001
    private var isPollingActive = true

    @SuppressLint("SetTextI18n", "MissingInflatedId", "ClickableViewAccessibility", "CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set theme and status bar AFTER content view is ready
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.background_color)
            espIp = loadEspIp()
        }

        // Load and apply the saved theme preference (after context is valid)
        loadThemePreference()

        // Initialize UI components
        mainContentLayout = findViewById(R.id.main_content_layout)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)

        tvEspIp = findViewById(R.id.tvEspIp)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        ivWifiSignal = findViewById(R.id.ivWifiSignal)
        tvSerialLog = findViewById(R.id.tvSerialLog)
        svSerialLog = findViewById(R.id.svSerialLog)
        btnScanQr = findViewById(R.id.btnScanQr)
        btnChangeWiFi = findViewById(R.id.btnChangeWiFi)
        btnShowSettings = findViewById(R.id.btnShowSettings)
        btnReboot = findViewById(R.id.btnReboot)
        btnRecheckConnection = findViewById<ImageView>(R.id.btnRecheckConnection)
        gradientProgress = findViewById(R.id.gradientProgress)
        btnEditIp = findViewById(R.id.btnEditIp)
        btnSaveIp = findViewById(R.id.btnSaveIp)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        val copyButton = findViewById<ImageButton>(R.id.btnCopyText)
        tvVoltage = findViewById(R.id.tvVoltage)
        tvCurrent = findViewById(R.id.tvCurrent)
        tvPower = findViewById(R.id.tvPower)
        tvSOC = findViewById(R.id.tvSOC)
        tvStatus = findViewById(R.id.tvStatus)
        tvEspMode = findViewById(R.id.tvEspMode)

        // Theme toggle
        themeToggleIcon = findViewById(R.id.theme_toggle_icon)
        themeToggleIcon.setOnClickListener {
            Toast.makeText(this, "Feature coming soon...", Toast.LENGTH_SHORT).show()
        }
        tvEspIp.text = "IP: $espIp"
        setupApiService() // initialize apiService with default IP
        val detectedIp = detectEspIp()
        if (detectedIp.isNotBlank() && detectedIp != "0.0.0.0") {
            espIp = detectedIp
        }
        btnRecheckConnection.setOnClickListener {
            scope.launch {
                setRefreshButtonState(true)
                fetchAndUpdateLiveData()
                setRefreshButtonState(false)
            }
        }

        // Clear logs button logic
        btnClearLogs.setOnClickListener {
            tvSerialLog.text = ""
            lastFetchedLog = ""
            logsCleared = true
        }

        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Serial Log", tvSerialLog.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnChangeWiFi.setOnClickListener { showWiFiConfigDialog() }
        // Copy IP button
        val btnCopyIp = findViewById<ImageView>(R.id.btnCopyIp)
        btnCopyIp.setOnClickListener {
            val ipText = tvEspIp.text.toString().removePrefix("IP: ").trim()
            if (ipText.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ESP IP", ipText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "IP copied: $ipText", Toast.LENGTH_SHORT).show()
            }
        }
        svSerialLog.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
        // Load saved IP or default
        espIp = loadEspIp()
        tvEspIp.text = "IP: $espIp"
        setupApiService(espIp)
        tvEspIp.text = "IP: $espIp"
        setupApiService() // initialize apiService with default IP
        updateModeFromSsidOnly()
        // Edit IP button
        btnEditIp.setOnClickListener {
            val currentIp = tvEspIp.text.toString().removePrefix("IP: ").trim()
            val editText = EditText(this).apply {
                setText(currentIp)
                textSize = tvEspIp.textSize / resources.displayMetrics.scaledDensity
                setTextColor(tvEspIp.currentTextColor)
                setSelectAllOnFocus(true)
            }
            val parentLayout = tvEspIp.parent as LinearLayout
            val index = parentLayout.indexOfChild(tvEspIp)
            parentLayout.removeView(tvEspIp)
            parentLayout.addView(editText, index)
            btnEditIp.visibility = View.GONE
            btnSaveIp.visibility = View.VISIBLE
            editText.requestFocus()
            editText.post {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }

            btnSaveIp.setOnClickListener {
                val newIp = editText.text.toString().trim()
                if (newIp.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                    saveEspIp(newIp)
                    setupApiService(newIp)
                    espIp = newIp

                    val newTv = TextView(this).apply {
                        id = R.id.tvEspIp
                        text = "IP: $newIp"
                        setTextColor(editText.currentTextColor)
                        textSize = editText.textSize / resources.displayMetrics.scaledDensity
                    }
                    parentLayout.removeView(editText)
                    parentLayout.addView(newTv, index)
                    tvEspIp = newTv

                    btnEditIp.visibility = View.VISIBLE
                    btnSaveIp.visibility = View.GONE
                    Toast.makeText(this, "Saved IP updated to $newIp", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid IP format", Toast.LENGTH_SHORT).show()
                }
            }
        }
        // Setup button click listeners
        btnScanQr.setOnClickListener {
            val intent = Intent(this, MLKitScannerActivity::class.java)
            startActivityForResult(intent, 2001)
        }
        // Button actions
        btnRecheckConnection.setOnClickListener {
            scope.launch {
                setRefreshButtonState(true)
                fetchAndUpdateLiveData()
                setRefreshButtonState(false)
            }
        }
        btnChangeWiFi.setOnClickListener { showWiFiConfigDialog() }
        btnShowSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnReboot.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reboot Battery Monitor")
                .setMessage("Are you sure you want to reboot the Battery Monitor?")
                .setPositiveButton("Reboot") { _, _ -> rebootDevice() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    private fun saveEspIp(ip: String) {
        val prefs = getSharedPreferences("ESP_PREFS", MODE_PRIVATE)
        prefs.edit { putString("esp_ip", ip) }
    }
    private fun loadEspIp(): String {
        val prefs = getSharedPreferences("ESP_PREFS", MODE_PRIVATE)
        return prefs.getString("esp_ip", "192.168.1.99") ?: "192.168.1.99"
    }

    override fun onResume() {
        super.onResume()
        isPollingActive = true
        startLiveDataPolling()
        startLogsPolling()
    }

    override fun onPause() {
        super.onPause()
        isPollingActive = false
        pollingJob?.cancel()
        logsJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        logsJob?.cancel()
        job.cancel()
    }

    private fun loadThemePreference() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean(THEME_KEY, false)
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun saveThemePreference(isDarkTheme: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean(THEME_KEY, isDarkTheme)
            apply()
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("MainActivity", "onConfigurationChanged: theme changed without activity restart.")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Check if there are fragments in the back stack
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()  // Remove the current fragment

            // Show the main content and hide fragment container
            mainContentLayout.visibility = View.VISIBLE
            fragmentContainer.visibility = View.GONE
        } else {
            // No fragments in back stack, so exit app or default behavior
            super.onBackPressed()
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 2001 && resultCode == RESULT_OK) {
            val scanned = data?.getStringExtra("SCAN_RESULT")
                ?.trim()
                ?.replace("\r", "")
                ?.replace("\n", "")
            if (scanned.isNullOrEmpty()) {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
                return
            }
            var fullUrl = scanned
            if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) {
                fullUrl = "http://$fullUrl"
            }
            try {
                val parsedUrl = fullUrl.toHttpUrlOrNull()
                if (parsedUrl == null || parsedUrl.host.isBlank()) {
                    Toast.makeText(this, "Scanned data is not a valid IP or URL", Toast.LENGTH_SHORT).show()
                    return
                }
                val newHost = parsedUrl.host ?: "192.168.4.1"
                // Only update service if host actually changes
                setupApiService(newHost)
                updateModeFromSsidOnly()
                // If reachable, API will override mode via our polling
                isEspReachable(newHost) { reachable ->
                    if (reachable) {
                        if (fullUrl.contains("/wifi_config")) {
                            showWiFiConfigDialog()
                        } else if (newHost == "192.168.4.1") {
                            AlertDialog.Builder(this)
                                .setTitle("Setup WiFi")
                                .setMessage("Setup your WiFi password to connect Battery Monitor to the internet.")
                                .setPositiveButton("Continue") { _, _ ->
                                    showWiFiConfigDialog()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            Toast.makeText(this, "ESP IP set to: $newHost", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        showEspWifiDetailsDialog()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error parsing scanned IP: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("DefaultLocale")
    private fun detectEspIp(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff
        )
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentSsid(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Android Q+ you generally need location permission to get SSID; check it
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return null
                }
            }
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")?.trim()
            if (ssid.isNullOrEmpty() || ssid.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true)) {
                null
            } else ssid
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting SSID: ${e.message}")
            null
        }
    }
    /**
     * Fallback-only: update mode text based on the phone's currently connected SSID.
     * This does not override API-driven mode when the ESP responds (API preferred).
     */
    @SuppressLint("SetTextI18n")
    private fun updateModeFromSsidOnly() {
        // If API already provided a mode, don't override it
        if (!currentModeFromApi.isNullOrBlank()) return

        val ssid = getCurrentSsid()
        when {
            ssid == null -> {
                tvEspMode.text = "NONE"
            }
            ssid.contains("BatteryMonitor-Setup", ignoreCase = true) -> {
                tvEspMode.text = "AP"
                // if we are connected to ESP AP, assume AP ip
                if (espIp != "192.168.4.1") {
                    setupApiService("192.168.4.1")
                }
            }
            else -> {
                // don't set mode text here — API will confirm STA when available
                tvEspMode.text = "STA?"
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WIFI_PERMISSION_REQUEST && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission granted, please scan again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "WiFi permission is required to detect Battery Monitor network.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWiFiConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_wifi_config, null)
        val etSsid = dialogView.findViewById<EditText>(R.id.etSsid)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)

        AlertDialog.Builder(this)
            .setTitle("WiFi Configuration")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val ssid = etSsid.text.toString().trim()
                val password = etPassword.text.toString().trim()
                if (ssid.isNotEmpty() && password.isNotEmpty()) {
                    sendWiFiConfigToESP(ssid, password)
                } else {
                    Toast.makeText(this, "Enter both SSID and Password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendWiFiConfigToESP(ssid: String, password: String) {
        scope.launch {
            try {
                val response = apiService.updateWiFi(WiFiRequest(ssid, password))
                if (response.isSuccessful) {
                    // Try to parse the JSON from the POST response immediately
                    val bodyStr = response.body()?.string()
                    var immediateStaIp: String? = null
                    if (!bodyStr.isNullOrBlank()) {
                        try {
                            val json = JSONObject(bodyStr)
                            val status = json.optString("status", "")
                            val staIp = json.optString("sta_ip", "")
                            if (status == "OK" && staIp.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                                immediateStaIp = staIp
                            }
                        } catch (_: Exception) {
                            // ignore parse errors
                        }
                    }

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "WiFi saved. Waiting for Battery Monitor to report IP...",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    if (!immediateStaIp.isNullOrEmpty()) {
                        // ✅ Save immediately if IP is present in POST response
                        saveEspIp(immediateStaIp)
                        withContext(Dispatchers.Main) {
                            setupApiService(immediateStaIp)
                            promptSwitchWiFi(immediateStaIp)
                        }
                    } else {
                        // fallback: start quick polling of /sta_ip for up to e.g. 60s
                        startStaIpPolling()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to update WiFi (HTTP ${response.code()})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    private fun promptSwitchWiFi(newIp: String) {
        AlertDialog.Builder(this)
            .setTitle("Switch to Home WiFi")
            .setMessage(
                "Battery Monitor is now connected to your home network.\n" +
                        "New IP: $newIp\n\n" +
                        "Please switch your phone to the same WiFi network."
            )
            .setPositiveButton("Open WiFi Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .setNegativeButton("Later", null)
            .show()
    }
    private fun isEspReachable(espIp: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val url = URL("http://$espIp/wifi_config")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.requestMethod = "GET"
                connection.connect()
                val isOk = connection.responseCode == 200
                runOnUiThread { callback(isOk) }
            } catch (_: Exception) {
                runOnUiThread { callback(false) }
            }
        }.start()
    }
    private fun showEspWifiDetailsDialog() {
        val ssid = "BatteryMonitor-Setup"
        val password = "12345678"
        AlertDialog.Builder(this)
            .setTitle("Connect to ESP WiFi")
            .setMessage("To continue, connect to:\n\nSSID: $ssid\nPassword: $password\n\nOpen WiFi settings and connect.")
            .setPositiveButton("Open WiFi Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    // ---------------------- Connection / Live data ----------------------
    /**
     * Only re-create Retrofit if IP changed or service not yet initialized.
     * Also updates the tvEspIp text.
     */
    @SuppressLint("SetTextI18n")
    private fun setupApiService(newIp: String? = null) {
        val ipToSet = newIp ?: espIp
        if (!::apiService.isInitialized || ipToSet != espIp) {
            espIp = ipToSet
            tvEspIp.text = "IP: $espIp"
            apiService = RetrofitClient.getClient("http://$espIp/").create(ApiService::class.java)
        }
    }
    @SuppressLint("SetTextI18n")
    private fun checkConnectionOnce() {
        scope.launch {
            try {
                apiService.getLiveData()
                tvConnectionStatus.text = "Connected"
                tvConnectionStatus.setTextColor(getColor(R.color.Connected))
            } catch (e: Exception) {
                tvConnectionStatus.text = "Disconnected"
                tvConnectionStatus.setTextColor(getColor(R.color.Disconnected))
            }
        }
    }
    private fun setRefreshButtonState(isRefreshing: Boolean) {
        if (isRefreshing) {
            btnRecheckConnection.isEnabled = false
            val rotation = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh)
            btnRecheckConnection.startAnimation(rotation)
        } else {
            btnRecheckConnection.isEnabled = true
            btnRecheckConnection.clearAnimation()
        }
    }
    /**
     * Start a single polling coroutine that repeatedly fetches live data.
     */
    private fun startLiveDataPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                fetchAndUpdateLiveData()
                delay(2000) // fixed interval; avoids overlapping calls
            }
        }
    }
    /**
     * Start logs polling at a slower rate so logs don't slow down live data
     */
    private fun startLogsPolling() {
        logsJob?.cancel()
        logsJob = scope.launch {
            while (isActive) {
                try {
                    val logsResponse = apiService.getSerialLogs()
                    val newLogs = if (logsResponse.isSuccessful) logsResponse.body()?.string() ?: "" else "Failed to fetch logs"

                    withContext(Dispatchers.Main) {
                        if (logsCleared) {
                            // After clearing, replace all logs with the newly fetched logs
                            tvSerialLog.text = newLogs.trim()
                            logsCleared = false
                        } else if (newLogs != lastFetchedLog) {
                            // If logs changed, update the text
                            tvSerialLog.text = newLogs.trim()
                        }
                        // Scroll to bottom
                        svSerialLog.post { svSerialLog.fullScroll(View.FOCUS_DOWN) }
                    }

                    lastFetchedLog = newLogs
                } catch (_: Exception) {
                    // Handle or ignore exceptions
                }
                delay(10000)  // Poll every 10 seconds
            }
        }
    }
    /**
     * This is the unified fetch function:
     * - determines connected/disconnected based solely on /live_data response
     * - updates UI values when connected
     * - leaves logs to separate polling
     */
    @SuppressLint("SetTextI18n")
    private suspend fun fetchAndUpdateLiveData() {
        if (currentModeFromApi.isNullOrBlank()) {
            updateModeFromSsidOnly()
        }
        var connected: Boolean
        try {
            val data = apiService.getLiveData()
            connected = true
            // ✅ Mode handling from API
            try {
                val mode = data.mode
                if (mode.isNotBlank()) {
                    currentModeFromApi = mode.uppercase(Locale.getDefault())
                    when (currentModeFromApi) {
                        "AP" -> {
                            tvEspMode.text = "AP"
                            if (espIp != "192.168.4.1") setupApiService("192.168.4.1")
                        }
                        "STA", "AP+STA", "AP_STA" -> {
                            tvEspMode.text = currentModeFromApi
                        }
                        else -> {
                            tvEspMode.text = currentModeFromApi
                        }
                    }
                }
            } catch (_: Exception) { }
            // ✅ Determine if Idle
            var isIdle = false
            val rawStatus = data.status ?: ""
            val status = rawStatus.trim().lowercase(Locale.getDefault())
            val displayStatus = when {
                "discharging" in status -> "Discharging"
                "charging" in status -> "Charging"
                "idle" in status || "standby" in status -> {
                    isIdle = true
                    "Idle"
                }
                else -> "Unknown"
            }
            tvBatteryStatus.text = displayStatus
            // ✅ Set colors
            val colorNormal = getColor(R.color.text_normal)
            val colorDisabled = getColor(R.color.text_disabled)
            // ✅ Voltage, Current, SOC
            try { tvVoltage.text = "%.2f V".format(data.voltage) } catch (_: Exception) {}
            try { tvCurrent.text = "%.2f A".format(data.current) } catch (_: Exception) {}
            try {
                val soc = data.soc.coerceIn(0f, 100f)
                tvSOC.text = "%.1f%%".format(soc)
                gradientProgress.setProgress(soc)
            } catch (_: Exception) {}
            // ✅ Power value with conditional gray-out
            try {
                tvPower.text = "%.2f W".format(data.power)
                tvPower.setTextColor(if (isIdle) colorDisabled else colorNormal)
            } catch (_: Exception) {
                tvPower.text = "--"
                tvPower.setTextColor(colorNormal)
            }
            // ✅ IP handling
            try {
                if (data.ip.isNotBlank()) {
                    if (espIp != data.ip) {
                        val oldIp = espIp
                        espIp = data.ip
                        saveEspIp(espIp)
                        setupApiService(espIp)
                        stopStaIpPolling()
                        val timeStamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            .format(java.util.Date())
                        Log.i("ESP_IP", "[$timeStamp] New IP received: $espIp (old: $oldIp)")
                        runOnUiThread {
                            Toast.makeText(this, "Battery Monitor new IP: $espIp", Toast.LENGTH_LONG).show()
                        }
                    }
                    tvEspIp.text = "IP: $espIp"
                } else if (currentModeFromApi != "AP") {
                    startStaIpPolling()
                }
            } catch (_: Exception) {}
            // ✅ RSSI
            try { updateWifiSignal(data.rssi) } catch (_: Exception) {}
        } catch (e: Exception) {
            connected = false
            currentModeFromApi = null
            tvSerialLog.text = "Error fetching data:\n${e.localizedMessage}"
            updateWifiSignal(-1000)
        }
        if (connected) {
            tvConnectionStatus.text = "Connected"
            tvConnectionStatus.setTextColor(getColor(R.color.Connected))
        } else if (isPollingActive) {
            tvConnectionStatus.text = "Disconnected"
            tvConnectionStatus.setTextColor(getColor(R.color.Disconnected))
        }
    }
    @SuppressLint("SetTextI18n")
    private fun startStaIpPolling() {
        if (pollingForStaIp) return
        pollingForStaIp = true
        staIpPollJob = lifecycleScope.launch {
            val maxAttempts = 60 // e.g. up to 60s
            var attempts = 0
            while (pollingForStaIp && attempts++ < maxAttempts) {
                try {
                    val resp = apiService.getStaIp()
                    if (resp.isSuccessful) {
                        val text = resp.body()?.string()?.trim() ?: ""
                        if (text.isNotBlank() && text != "NOT_CONNECTED" &&
                            text.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                            // ✅ Save IP when received via polling
                            saveEspIp(text)
                            pollingForStaIp = false
                            setupApiService(text)
                            runOnUiThread {
                                tvEspIp.text = "IP: $text"
                                Toast.makeText(
                                    this@MainActivity,
                                    "Battery Monitor connected to STA at $text",
                                    Toast.LENGTH_LONG
                                ).show()
                                promptSwitchWiFi(text)
                            }
                            break
                        }
                    }
                } catch (_: Exception) {
                    // ignore: may fail while network switching
                }
                delay(1000)
            }
            pollingForStaIp = false
            staIpPollJob?.cancel()
            staIpPollJob = null
        }
    }
    private fun stopStaIpPolling() {
        pollingForStaIp = false
        staIpPollJob?.cancel()
        staIpPollJob = null
    }
    private fun rebootDevice() {
        scope.launch {
            try {
                val response = apiService.rebootDevice()
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Rebooting Battery Monitor...", Toast.LENGTH_LONG).show()
                    delay(3000) // Optional: give ESP time to restart
                } else {
                    Toast.makeText(this@MainActivity, "Failed to reboot", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun normalizeApRssi(rssi: Int): Int {
        // Convert positive AP RSSI to approx. negative dBm
        return if (rssi > 0) rssi - 100 else rssi
    }
    private var blinkingJob: Job? = null
    private fun updateWifiSignal(rssi: Int) {
        val currentSignalState: SignalState
        val barsToFill: Int
        // 🛠 In AP mode, never blink — but still calculate bars from RSSI
        if (currentModeFromApi == "AP" || tvEspMode.text.toString() == "AP") {
            val rssiDbm = normalizeApRssi(rssi)
            when {
                rssiDbm <= -1000 -> {
                    currentSignalState = SignalState.NO_SIGNAL
                    barsToFill = 0
                }
                rssiDbm < -80 -> {
                    currentSignalState = SignalState.NORMAL
                    barsToFill = 1
                }
                rssiDbm < -70 -> {
                    currentSignalState = SignalState.NORMAL
                    barsToFill = 2
                }
                rssiDbm < -60 -> {
                    currentSignalState = SignalState.NORMAL
                    barsToFill = 3
                }
                else -> {
                    currentSignalState = SignalState.NORMAL
                    barsToFill = 4
                }
            }
        } else {
            // Original STA mode logic with blinking
            if (rssi <= -1000) {
                currentSignalState = SignalState.NO_SIGNAL
                barsToFill = 0
            } else if (rssi < -80) {
                currentSignalState = SignalState.NORMAL
                barsToFill = 1
            } else if (rssi < -70) {
                currentSignalState = SignalState.NORMAL
                barsToFill = 2
            } else if (rssi < -60) {
                currentSignalState = SignalState.NORMAL
                barsToFill = 3
            } else if (rssi <= 0) {
                currentSignalState = SignalState.NORMAL
                barsToFill = 4
            } else {
                currentSignalState = SignalState.BLINKING
                barsToFill = 4
            }
        }
        // Cancel blink job if leaving blinking state
        if (currentSignalState != SignalState.BLINKING && blinkingJob != null) {
            blinkingJob?.cancel()
            blinkingJob = null
        }
        // Only update if changed
        if (currentSignalState != lastSignalState || barsToFill != lastBarsToFill) {
            lastSignalState = currentSignalState
            lastBarsToFill = barsToFill

            when (currentSignalState) {
                SignalState.NO_SIGNAL -> loadSvg(createNoSignalSvg())
                SignalState.NORMAL -> loadSvg(createWifiIconSvgByBars(barsToFill))
                SignalState.BLINKING -> startBlinkingFadeAnimation()
                SignalState.NONE -> {}
            }
        }
    }
    private fun startBlinkingFadeAnimation() {
        blinkingJob?.cancel()
        blinkingJob = lifecycleScope.launch {
            val red = intArrayOf(239, 68, 68)    // #EF4444
            val gray = intArrayOf(209, 213, 219) // #D1D5DB
            val steps = 30 // higher = smoother fade
            val delayMs = 20L
            var forward = true
            var t = 0
            while (isActive) {
                val factor = t / steps.toFloat()
                val r = (gray[0] + (red[0] - gray[0]) * factor).roundToInt()
                val g = (gray[1] + (red[1] - gray[1]) * factor).roundToInt()
                val b = (gray[2] + (red[2] - gray[2]) * factor).roundToInt()
                val color = String.format("#%02X%02X%02X", r, g, b)
                loadSvg(createBlinkingWifiSvg(color))
                if (forward) t++ else t--
                if (t >= steps) forward = false
                if (t <= 0) forward = true
                delay(delayMs)
            }
        }
    }
    private fun loadSvg(svgString: String) {
        val imageLoader = ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
        val request = ImageRequest.Builder(this)
            .data(svgString.toByteArray())
            .decoderFactory(SvgDecoder.Factory())
            .target(ivWifiSignal)
            .build()
        imageLoader.enqueue(request)
    }
    private fun createWifiIconSvgByBars(barsToFill: Int): String {
        fun colorForBar(bar: Int) = when {
            barsToFill == 1 && bar == 1 -> "#EF4444"          // Red for weak
            barsToFill == 2 && bar <= 2 -> "#FBBF24"          // Yellow for medium
            barsToFill >= 3 && bar <= barsToFill -> "#22C55E" // Green for strong
            else -> "#D1D5DB"                                 // Gray for empty
        }
        return """
    <svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 -960 960 960" width="24">
      <!-- Dot -->
      <path d="M480-120q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29Z" 
            fill="${colorForBar(1)}"/>
      <!-- Inner arc -->
      <path d="M254-346l-84-86q59-59 138.5-93.5T480-560q92 0 171.5 35T790-430l-84 84q-44-44-102-69t-124-25q-66 0-124 25t-102 69Z"
            fill="${colorForBar(2)}"/>
      <!-- Outer arc -->
      <path d="M84-516 0-600q92-94 215-147t265-53q142 0 265 53t215 147l-84 84q-77-77-178.5-120.5T480-680q-116 0-217.5 43.5T84-516Z"
            fill="${colorForBar(3)}"/>
    </svg>
    """.trimIndent()
    }
    private fun createBlinkingWifiSvg(color: String): String {
        return """
    <svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 -960 960 960" width="24">
      <path d="M480-120q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29Z" 
            fill="$color"/>
      <path d="M254-346l-84-86q59-59 138.5-93.5T480-560q92 0 171.5 35T790-430l-84 84q-44-44-102-69t-124-25q-66 0-124 25t-102 69Z"
            fill="$color"/>
      <path d="M84-516 0-600q92-94 215-147t265-53q142 0 265 53t215 147l-84 84q-77-77-178.5-120.5T480-680q-116 0-217.5 43.5T84-516Z"
            fill="$color"/>
    </svg>
    """.trimIndent()
    }
    private fun createNoSignalSvg(): String {
        return """
    <svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 -960 960 960" width="24">
      <!-- WiFi in gray -->
      <path d="M480-120q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29Z" 
            fill="#D1D5DB"/>
      <path d="M254-346l-84-86q59-59 138.5-93.5T480-560q92 0 171.5 35T790-430l-84 84q-44-44-102-69t-124-25q-66 0-124 25t-102 69Z"
            fill="#D1D5DB"/>
      <path d="M84-516 0-600q92-94 215-147t265-53q142 0 265 53t215 147l-84 84q-77-77-178.5-120.5T480-680q-116 0-217.5 43.5T84-516Z"
            fill="#D1D5DB"/>
      <!-- Red cross line -->
      <line x1="100" y1="-100" x2="860" y2="-860" stroke="#EF4444" stroke-width="60" stroke-linecap="round"/>
    </svg>
    """.trimIndent()
    }
}