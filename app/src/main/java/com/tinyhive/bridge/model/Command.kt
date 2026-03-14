package com.tinyhive.bridge.model

import com.google.gson.annotations.SerializedName

/**
 * Commands sent from TinyHive to the bridge app.
 */
data class BridgeCommand(
    @SerializedName("id") val id: String,
    @SerializedName("action") val action: String,
    @SerializedName("params") val params: Map<String, Any>? = null
)

/**
 * Response sent back to TinyHive.
 */
data class BridgeResponse(
    @SerializedName("id") val id: String,
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: Any? = null,
    @SerializedName("error") val error: String? = null
)

/**
 * Screen element for read_screen responses.
 */
data class ScreenElement(
    @SerializedName("text") val text: String?,
    @SerializedName("content_description") val contentDescription: String?,
    @SerializedName("class_name") val className: String?,
    @SerializedName("resource_id") val resourceId: String?,
    @SerializedName("bounds") val bounds: String?,
    @SerializedName("clickable") val clickable: Boolean,
    @SerializedName("focusable") val focusable: Boolean
)

/**
 * Device info for identify command.
 */
data class DeviceInfo(
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("android_version") val androidVersion: String,
    @SerializedName("app_version") val appVersion: String,
    @SerializedName("screen_width") val screenWidth: Int,
    @SerializedName("screen_height") val screenHeight: Int
)

/**
 * Supported actions.
 */
object Actions {
    const val IDENTIFY = "identify"
    const val PING = "ping"
    const val OPEN_APP = "open_app"
    const val TAP = "tap"
    const val TAP_COORDINATES = "tap_coordinates"
    const val LONG_PRESS = "long_press"
    const val SWIPE = "swipe"
    const val TYPE_TEXT = "type_text"
    const val READ_SCREEN = "read_screen"
    const val BACK = "back"
    const val HOME = "home"
    const val RECENTS = "recents"
    const val SCROLL = "scroll"
    const val GET_CURRENT_APP = "get_current_app"
    const val SCREENSHOT = "screenshot"
    const val NOTIFICATIONS = "notifications"
}
