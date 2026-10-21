package com.yesmin.ai

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings

class ActionExecutor(private val context: Context) {

    private var isFlashOn = false

    fun execute(command: YesminCommand): String {
        return try {
            when (command.action.uppercase()) {
                "OPEN_APP" -> openAppByName(command.param1)
                "PLAY_YOUTUBE" -> playYouTube(command.param1)
                "TAP_SCREEN" -> {
                    val x = command.param1.toFloatOrNull() ?: 540f
                    val y = command.param2.toFloatOrNull() ?: 1000f
                    val svc = YesminAccessibilityService.instance
                    if (svc != null) {
                        svc.tapOnScreen(x, y)
                        "Touched screen at ($x, $y)"
                    } else {
                        openAccessibilitySettings()
                        "Please enable Yesmin Accessibility Service first!"
                    }
                }
                "SCROLL_DOWN" -> {
                    YesminAccessibilityService.instance?.scrollScreen(true)
                    "Scrolled down"
                }
                "SCROLL_UP" -> {
                    YesminAccessibilityService.instance?.scrollScreen(false)
                    "Scrolled up"
                }
                "TOGGLE_FLASHLIGHT" -> toggleFlashlight()
                else -> ""
            }
        } catch (e: Exception) {
            "Action error: ${e.localizedMessage}"
        }
    }

    private fun openAppByName(appName: String): String {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        val target = packages.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true) ||
            it.packageName.contains(appName, ignoreCase = true)
        }
        return if (target != null) {
            val launchIntent = pm.getLaunchIntentForPackage(target.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                "Opened $appName"
            } else {
                "Cannot launch $appName"
            }
        } else {
            "App '$appName' not found"
        }
    }

    private fun playYouTube(query: String): String {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return "Searching '$query' on YouTube"
    }

    private fun toggleFlashlight(): String {
        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = camManager.cameraIdList.firstOrNull() ?: return "No camera found"
        isFlashOn = !isFlashOn
        camManager.setTorchMode(cameraId, isFlashOn)
        return if (isFlashOn) "Flashlight ON" else "Flashlight OFF"
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
