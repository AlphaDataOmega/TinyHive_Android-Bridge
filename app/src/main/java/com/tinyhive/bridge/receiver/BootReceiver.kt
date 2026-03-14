package com.tinyhive.bridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tinyhive.bridge.service.BridgeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Receiver to restart the bridge service after device boot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private val KEY_HIVE_URL = stringPreferencesKey("hive_url")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (context == null) return

        Log.i(TAG, "Boot completed, checking if we should start bridge service")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.dataStore.data.first()
                val savedUrl = prefs[KEY_HIVE_URL]

                if (!savedUrl.isNullOrBlank()) {
                    Log.i(TAG, "Found saved URL, starting bridge service")
                    val serviceIntent = Intent(context, BridgeService::class.java).apply {
                        putExtra(BridgeService.EXTRA_HIVE_URL, savedUrl)
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service on boot", e)
            }
        }
    }
}
