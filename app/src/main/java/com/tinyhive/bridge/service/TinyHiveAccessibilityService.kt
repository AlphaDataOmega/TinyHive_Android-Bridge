package com.tinyhive.bridge.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.Gson
import com.tinyhive.bridge.model.*

/**
 * Accessibility Service that receives commands from TinyHive and controls apps.
 */
class TinyHiveAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TinyHiveA11y"
        var instance: TinyHiveAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private val gson = Gson()
    private var currentPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                currentPackage = it.packageName?.toString()
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    /**
     * Execute a command and return the response.
     */
    fun executeCommand(command: BridgeCommand): BridgeResponse {
        return try {
            when (command.action) {
                Actions.IDENTIFY -> handleIdentify(command)
                Actions.PING -> BridgeResponse(command.id, true, mapOf("pong" to true))
                Actions.OPEN_APP -> handleOpenApp(command)
                Actions.TAP -> handleTap(command)
                Actions.TAP_COORDINATES -> handleTapCoordinates(command)
                Actions.LONG_PRESS -> handleLongPress(command)
                Actions.SWIPE -> handleSwipe(command)
                Actions.TYPE_TEXT -> handleTypeText(command)
                Actions.READ_SCREEN -> handleReadScreen(command)
                Actions.BACK -> handleBack(command)
                Actions.HOME -> handleHome(command)
                Actions.RECENTS -> handleRecents(command)
                Actions.SCROLL -> handleScroll(command)
                Actions.GET_CURRENT_APP -> handleGetCurrentApp(command)
                Actions.SCREENSHOT -> handleScreenshot(command)
                Actions.NOTIFICATIONS -> handleNotifications(command)
                else -> BridgeResponse(command.id, false, error = "Unknown action: ${command.action}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${command.action}", e)
            BridgeResponse(command.id, false, error = e.message)
        }
    }

    private fun handleIdentify(command: BridgeCommand): BridgeResponse {
        val displayMetrics = resources.displayMetrics
        val info = DeviceInfo(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            appVersion = "1.0.0",
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels
        )
        return BridgeResponse(command.id, true, info)
    }

    private fun handleOpenApp(command: BridgeCommand): BridgeResponse {
        val packageName = command.params?.get("package") as? String
            ?: return BridgeResponse(command.id, false, error = "Missing 'package' param")

        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return BridgeResponse(command.id, false, error = "App not found: $packageName")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return BridgeResponse(command.id, true, mapOf("launched" to packageName))
    }

    private fun handleTap(command: BridgeCommand): BridgeResponse {
        val text = command.params?.get("text") as? String
        val resourceId = command.params?.get("resource_id") as? String
        val contentDesc = command.params?.get("content_description") as? String

        val root = rootInActiveWindow
            ?: return BridgeResponse(command.id, false, error = "No active window")

        val node = findNode(root, text, resourceId, contentDesc)
            ?: return BridgeResponse(command.id, false, error = "Element not found")

        val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        root.recycle()

        return BridgeResponse(command.id, result, if (result) mapOf("tapped" to true) else null,
            if (!result) "Click action failed" else null)
    }

    private fun handleTapCoordinates(command: BridgeCommand): BridgeResponse {
        val x = (command.params?.get("x") as? Number)?.toFloat()
            ?: return BridgeResponse(command.id, false, error = "Missing 'x' param")
        val y = (command.params?.get("y") as? Number)?.toFloat()
            ?: return BridgeResponse(command.id, false, error = "Missing 'y' param")

        val result = performTap(x, y)
        return BridgeResponse(command.id, result)
    }

    private fun handleLongPress(command: BridgeCommand): BridgeResponse {
        val x = (command.params?.get("x") as? Number)?.toFloat()
            ?: return BridgeResponse(command.id, false, error = "Missing 'x' param")
        val y = (command.params?.get("y") as? Number)?.toFloat()
            ?: return BridgeResponse(command.id, false, error = "Missing 'y' param")

        val result = performLongPress(x, y)
        return BridgeResponse(command.id, result)
    }

    private fun handleSwipe(command: BridgeCommand): BridgeResponse {
        val direction = command.params?.get("direction") as? String
            ?: return BridgeResponse(command.id, false, error = "Missing 'direction' param")

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()
        val centerX = width / 2
        val centerY = height / 2

        val (startX, startY, endX, endY) = when (direction.lowercase()) {
            "up" -> listOf(centerX, centerY + 400, centerX, centerY - 400)
            "down" -> listOf(centerX, centerY - 400, centerX, centerY + 400)
            "left" -> listOf(centerX + 400, centerY, centerX - 400, centerY)
            "right" -> listOf(centerX - 400, centerY, centerX + 400, centerY)
            else -> return BridgeResponse(command.id, false, error = "Invalid direction: $direction")
        }

        val result = performSwipe(startX, startY, endX, endY)
        return BridgeResponse(command.id, result)
    }

    private fun handleTypeText(command: BridgeCommand): BridgeResponse {
        val text = command.params?.get("text") as? String
            ?: return BridgeResponse(command.id, false, error = "Missing 'text' param")

        val root = rootInActiveWindow
            ?: return BridgeResponse(command.id, false, error = "No active window")

        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return BridgeResponse(command.id, false, error = "No focused input field")

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        focusedNode.recycle()
        root.recycle()

        return BridgeResponse(command.id, result)
    }

    private fun handleReadScreen(command: BridgeCommand): BridgeResponse {
        val root = rootInActiveWindow
            ?: return BridgeResponse(command.id, false, error = "No active window")

        val elements = mutableListOf<ScreenElement>()
        collectElements(root, elements, maxDepth = 15)
        root.recycle()

        return BridgeResponse(command.id, true, mapOf(
            "package" to (currentPackage ?: "unknown"),
            "element_count" to elements.size,
            "elements" to elements.take(100) // Limit to 100 elements
        ))
    }

    private fun handleBack(command: BridgeCommand): BridgeResponse {
        val result = performGlobalAction(GLOBAL_ACTION_BACK)
        return BridgeResponse(command.id, result)
    }

    private fun handleHome(command: BridgeCommand): BridgeResponse {
        val result = performGlobalAction(GLOBAL_ACTION_HOME)
        return BridgeResponse(command.id, result)
    }

    private fun handleRecents(command: BridgeCommand): BridgeResponse {
        val result = performGlobalAction(GLOBAL_ACTION_RECENTS)
        return BridgeResponse(command.id, result)
    }

    private fun handleScroll(command: BridgeCommand): BridgeResponse {
        val direction = command.params?.get("direction") as? String ?: "down"

        val root = rootInActiveWindow
            ?: return BridgeResponse(command.id, false, error = "No active window")

        val scrollable = findScrollableNode(root)
        if (scrollable == null) {
            root.recycle()
            return BridgeResponse(command.id, false, error = "No scrollable element found")
        }

        val action = if (direction.lowercase() == "up") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }

        val result = scrollable.performAction(action)
        scrollable.recycle()
        root.recycle()

        return BridgeResponse(command.id, result)
    }

    private fun handleGetCurrentApp(command: BridgeCommand): BridgeResponse {
        return BridgeResponse(command.id, true, mapOf("package" to (currentPackage ?: "unknown")))
    }

    private fun handleScreenshot(command: BridgeCommand): BridgeResponse {
        // Screenshot requires Android 11+ and additional permissions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return BridgeResponse(command.id, false, error = "Screenshot requires Android 11+")
        }
        // For now, just indicate it's not implemented
        return BridgeResponse(command.id, false, error = "Screenshot not yet implemented")
    }

    private fun handleNotifications(command: BridgeCommand): BridgeResponse {
        val result = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        return BridgeResponse(command.id, result)
    }

    // --- Helper methods ---

    private fun findNode(
        root: AccessibilityNodeInfo,
        text: String?,
        resourceId: String?,
        contentDesc: String?
    ): AccessibilityNodeInfo? {
        // Try resource ID first (most reliable)
        if (!resourceId.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(resourceId)
            if (nodes.isNotEmpty()) return nodes[0]
        }

        // Try text match
        if (!text.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.text?.toString()?.contains(text, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
                    return node
                }
            }
        }

        // Try content description
        if (!contentDesc.isNullOrBlank()) {
            return findNodeByContentDescription(root, contentDesc)
        }

        return null
    }

    private fun findNodeByContentDescription(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (root.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByContentDescription(child, desc)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    private fun collectElements(
        node: AccessibilityNodeInfo,
        elements: MutableList<ScreenElement>,
        depth: Int = 0,
        maxDepth: Int = 15
    ) {
        if (depth > maxDepth) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // Only include elements with text/description or that are clickable
        if (node.text != null || node.contentDescription != null || node.isClickable) {
            elements.add(ScreenElement(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                className = node.className?.toString(),
                resourceId = node.viewIdResourceName,
                bounds = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
                clickable = node.isClickable,
                focusable = node.isFocusable
            ))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectElements(child, elements, depth + 1, maxDepth)
            child.recycle()
        }
    }

    private fun performTap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    private fun performLongPress(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        return dispatchGesture(gesture, null, null)
    }
}
