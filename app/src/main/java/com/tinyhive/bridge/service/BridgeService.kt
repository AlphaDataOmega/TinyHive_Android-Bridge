package com.tinyhive.bridge.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.tinyhive.bridge.MainActivity
import com.tinyhive.bridge.R
import com.tinyhive.bridge.TinyHiveApp
import com.tinyhive.bridge.model.BridgeCommand
import com.tinyhive.bridge.model.BridgeResponse
import kotlinx.coroutines.*
import okhttp3.*

/**
 * Foreground service that maintains WebSocket connection to TinyHive.
 */
class BridgeService : Service() {

    companion object {
        private const val TAG = "BridgeService"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_HIVE_URL = "hive_url"
        const val EXTRA_SESSION_TOKEN = "session_token"

        var isRunning = false
            private set
        var connectionState = ConnectionState.DISCONNECTED
            private set

        enum class ConnectionState {
            DISCONNECTED, CONNECTING, CONNECTED, ERROR
        }

        // Listeners for UI updates
        var onStateChanged: ((ConnectionState, String?) -> Unit)? = null
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var hiveUrl: String? = null
    private var sessionToken: String? = null
    private var reconnectJob: Job? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(java.time.Duration.ofSeconds(30))
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.i(TAG, "BridgeService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        hiveUrl = intent?.getStringExtra(EXTRA_HIVE_URL)
        sessionToken = intent?.getStringExtra(EXTRA_SESSION_TOKEN)

        if (hiveUrl.isNullOrBlank()) {
            Log.e(TAG, "No hive URL provided")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
        connect()

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Service stopped")
        scope.cancel()
        Log.i(TAG, "BridgeService destroyed")
        super.onDestroy()
    }

    private fun connect() {
        val url = hiveUrl ?: return
        val wsUrl = buildWebSocketUrl(url)

        Log.i(TAG, "Connecting to: $wsUrl")
        updateState(ConnectionState.CONNECTING)

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                updateState(ConnectionState.CONNECTED)
                updateNotification("Connected to ${extractHost(url)}")

                // Send initial identify with session token if available
                val identify = mutableMapOf(
                    "type" to "identify",
                    "client" to "android_bridge",
                    "version" to "1.0.0",
                    "device_id" to android.os.Build.MODEL.replace(" ", "_")
                )
                sessionToken?.let { identify["session_token"] = it }
                webSocket.send(gson.toJson(identify))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                updateState(ConnectionState.DISCONNECTED)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                updateState(ConnectionState.ERROR, t.message)
                scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        scope.launch {
            try {
                val command = gson.fromJson(text, BridgeCommand::class.java)
                Log.d(TAG, "Received command: ${command.action}")

                val accessibilityService = TinyHiveAccessibilityService.instance
                if (accessibilityService == null) {
                    val errorResponse = BridgeResponse(
                        command.id,
                        false,
                        error = "Accessibility service not running"
                    )
                    webSocket?.send(gson.toJson(errorResponse))
                    return@launch
                }

                val response = accessibilityService.executeCommand(command)
                webSocket?.send(gson.toJson(response))

            } catch (e: Exception) {
                Log.e(TAG, "Error handling message: ${e.message}", e)
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000) // Wait 5 seconds before reconnecting
            if (isRunning) {
                Log.i(TAG, "Attempting reconnect...")
                connect()
            }
        }
    }

    private fun buildWebSocketUrl(url: String): String {
        var wsUrl = url.trim()

        // Convert http(s) to ws(s)
        wsUrl = when {
            wsUrl.startsWith("https://") -> wsUrl.replace("https://", "wss://")
            wsUrl.startsWith("http://") -> wsUrl.replace("http://", "ws://")
            wsUrl.startsWith("wss://") || wsUrl.startsWith("ws://") -> wsUrl
            else -> "wss://$wsUrl"
        }

        // Add bridge endpoint if not present
        if (!wsUrl.contains("/bridge")) {
            wsUrl = wsUrl.trimEnd('/') + "/api/bridge/ws"
        }

        return wsUrl
    }

    private fun extractHost(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("wss://")
            .removePrefix("ws://")
            .split("/")[0]
    }

    private fun updateState(state: ConnectionState, error: String? = null) {
        connectionState = state
        onStateChanged?.invoke(state, error)
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TinyHiveApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TinyHive Bridge")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
