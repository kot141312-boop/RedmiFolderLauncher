package com.kir.folderlauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class BottomSwipeService : Service() {

    private lateinit var windowManager: WindowManager
    private var gestureView: View? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "folder_drawer_service")
                .setContentTitle("Folder Drawer")
                .setContentText("Ожидание жеста снизу вверх")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("Folder Drawer")
                .setContentText("Ожидание жеста снизу вверх")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
        }

        startForeground(1, notification)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addGestureArea()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "folder_drawer_service",
                "Folder Drawer",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    private fun addGestureArea() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            36,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

        val view = View(this)
        var startY = 0f
        var totalMovement = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    totalMovement = 0f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    totalMovement = event.rawY - startY
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (totalMovement < -45f) {
                        openDrawer()
                    }
                    true
                }

                else -> true
            }
        }

        gestureView = view
        windowManager.addView(view, params)
    }

    private fun openDrawer() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        startActivity(intent)
    }
        override fun onDestroy() {
        gestureView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }

        gestureView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
