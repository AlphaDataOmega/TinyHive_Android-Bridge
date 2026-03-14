package com.tinyhive.bridge

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.tinyhive.bridge.databinding.ActivityMainBinding
import com.tinyhive.bridge.service.BridgeService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private val KEY_HIVE_URL = stringPreferencesKey("hive_url")
        private val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        private const val QR_SCANNER_REQUEST = 1001
    }

    private val client = OkHttpClient()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openQRScanner()
        } else {
            Toast.makeText(this, "Camera permission required for QR scanning", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadSavedUrl()

        // Handle deep link if present
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun handleIntent(intent: Intent) {
        // Handle deep link: tinyhive://pair?token=xxx&url=yyy
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri?.scheme == "tinyhive" && uri.host == "pair") {
                val token = uri.getQueryParameter("token")
                val url = uri.getQueryParameter("url")

                if (token != null && url != null) {
                    validateAndConnect(token, url)
                }
            }
        }
    }

    private fun setupUI() {
        // Enable accessibility button
        binding.btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Scan QR button
        binding.btnScanQr.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                openQRScanner()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Connect button (manual URL entry)
        binding.btnConnect.setOnClickListener {
            val url = binding.editHiveUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                saveUrlAndConnect(url)
            }
        }

        // Disconnect button
        binding.btnDisconnect.setOnClickListener {
            stopBridgeService()
        }

        // Listen for connection state changes
        BridgeService.onStateChanged = { state, error ->
            runOnUiThread {
                updateConnectionStatus(state, error)
            }
        }
    }

    private fun openQRScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        startActivityForResult(intent, QR_SCANNER_REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == QR_SCANNER_REQUEST && resultCode == RESULT_OK) {
            val scannedData = data?.getStringExtra("scanned_data")
            scannedData?.let { parseQRData(it) }
        }
    }

    private fun parseQRData(data: String) {
        // Parse deep link from QR: tinyhive://pair?token=xxx&url=yyy
        try {
            val uri = Uri.parse(data)
            if (uri.scheme == "tinyhive" && uri.host == "pair") {
                val token = uri.getQueryParameter("token")
                val url = uri.getQueryParameter("url")
                if (token != null && url != null) {
                    validateAndConnect(token, url)
                    return
                }
            }
        } catch (e: Exception) {
            // Not a valid URI, try as plain URL
        }

        // Maybe it's just a URL
        if (data.startsWith("http://") || data.startsWith("https://")) {
            saveUrlAndConnect(data)
        } else {
            Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateAndConnect(token: String, hiveUrl: String) {
        binding.statusConnection.text = "Validating..."
        binding.statusConnection.setTextColor(ContextCompat.getColor(this, R.color.warning))

        // Validate token with server
        val url = "${hiveUrl.trimEnd('/')}/api/bridge/pair/validate"
        val json = JSONObject().apply {
            put("token", token)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    binding.statusConnection.text = "Validation failed: ${e.message}"
                    binding.statusConnection.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            if (json.getBoolean("success")) {
                                val sessionToken = json.getString("session_token")
                                saveTokenAndConnect(hiveUrl, sessionToken)
                                Toast.makeText(this@MainActivity, "Paired successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                binding.statusConnection.text = "Validation failed"
                                binding.statusConnection.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error))
                            }
                        } catch (e: Exception) {
                            binding.statusConnection.text = "Invalid response"
                            binding.statusConnection.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error))
                        }
                    } else {
                        binding.statusConnection.text = "Token expired or invalid"
                        binding.statusConnection.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error))
                    }
                }
            }
        })
    }

    private fun saveTokenAndConnect(url: String, sessionToken: String) {
        lifecycleScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_HIVE_URL] = url
                prefs[KEY_SESSION_TOKEN] = sessionToken
            }
            startBridgeService(url, sessionToken)
        }
    }

    private fun loadSavedUrl() {
        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            val savedUrl = prefs[KEY_HIVE_URL]
            if (!savedUrl.isNullOrBlank()) {
                binding.editHiveUrl.setText(savedUrl)
            }
        }
    }

    private fun saveUrlAndConnect(url: String) {
        lifecycleScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_HIVE_URL] = url
            }
            startBridgeService(url, null)
        }
    }

    private fun updateStatus() {
        // Check accessibility service status
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        binding.statusAccessibility.text = if (accessibilityEnabled) "Enabled" else "Disabled"
        binding.statusAccessibility.setTextColor(
            ContextCompat.getColor(this,
                if (accessibilityEnabled) R.color.success else R.color.error)
        )
        binding.btnEnableAccessibility.isEnabled = !accessibilityEnabled

        // Check connection status
        updateConnectionStatus(BridgeService.connectionState, null)
    }

    private fun updateConnectionStatus(state: BridgeService.Companion.ConnectionState, error: String?) {
        val (text, color) = when (state) {
            BridgeService.Companion.ConnectionState.CONNECTED -> "Connected" to R.color.success
            BridgeService.Companion.ConnectionState.CONNECTING -> "Connecting..." to R.color.warning
            BridgeService.Companion.ConnectionState.ERROR -> "Error: ${error ?: "Unknown"}" to R.color.error
            BridgeService.Companion.ConnectionState.DISCONNECTED -> "Disconnected" to R.color.text_muted
        }

        binding.statusConnection.text = text
        binding.statusConnection.setTextColor(ContextCompat.getColor(this, color))

        // Update button visibility
        binding.btnConnect.isEnabled = state != BridgeService.Companion.ConnectionState.CONNECTED
        binding.btnScanQr.isEnabled = state != BridgeService.Companion.ConnectionState.CONNECTED
        binding.btnDisconnect.isEnabled = state == BridgeService.Companion.ConnectionState.CONNECTED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun startBridgeService(url: String, sessionToken: String?) {
        if (!isAccessibilityServiceEnabled()) {
            binding.statusConnection.text = "Enable accessibility first"
            binding.statusConnection.setTextColor(ContextCompat.getColor(this, R.color.error))
            return
        }

        val intent = Intent(this, BridgeService::class.java).apply {
            putExtra(BridgeService.EXTRA_HIVE_URL, url)
            sessionToken?.let { putExtra(BridgeService.EXTRA_SESSION_TOKEN, it) }
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopBridgeService() {
        stopService(Intent(this, BridgeService::class.java))
        updateConnectionStatus(BridgeService.Companion.ConnectionState.DISCONNECTED, null)
    }
}
