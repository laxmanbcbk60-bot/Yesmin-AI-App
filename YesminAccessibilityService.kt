package com.yesmin.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class YesminAccessibilityService : AccessibilityService() {

    companion object {
        var instance: YesminAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun tapOnScreen(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun scrollScreen(scrollDown: Boolean) {
        val path = Path().apply {
            if (scrollDown) {
                moveTo(500f, 1500f)
                lineTo(500f, 400f)
            } else {
                moveTo(500f, 400f)
                lineTo(500f, 1500f)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return "Screen empty"
        val sb = StringBuilder()
        extractText(root, sb)
        return sb.toString().take(800)
    }

    private fun extractText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        if (!node.text.isNullOrBlank()) {
            sb.append(node.text).append(" | ")
        }
        for (i in 0 until node.childCount) {
            extractText(node.getChild(i), sb)
        }
    }
}
