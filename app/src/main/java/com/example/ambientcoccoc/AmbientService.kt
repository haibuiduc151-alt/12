package com.example.ambientcoccoc

import android.app.*
import android.app.Activity
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import kotlin.math.max
import kotlin.math.min

class AmbientService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: GlowView? = null
    private var wm: WindowManager? = null
    private var handler: Handler? = null
    private var lastUpdate = 0L
    private var screenW = 0
    private var screenH = 0
    private var density = 1
    private var ratio = 16f / 9f

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        // A fresh consent result starts a new projection session. Tear down any
        // previous session first so repeated taps do not leak virtual displays.
        releaseCaptureResources()
        createNotification()
        ratio = getSharedPreferences("ambient", MODE_PRIVATE).getFloat("ratio", 16f / 9f)
        val dm = resources.displayMetrics
        screenW = dm.widthPixels; screenH = dm.heightPixels; density = dm.densityDpi
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            projection = mgr.getMediaProjection(code, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, Handler(Looper.getMainLooper()))
            reader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
            val thread = HandlerThread("AmbientCapture").apply { start() }
            handler = Handler(thread.looper)
            reader?.setOnImageAvailableListener({ r ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate < 120) { r.acquireLatestImage()?.close(); return@setOnImageAvailableListener }
                lastUpdate = now
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try { sample(image) } finally { image.close() }
            }, handler)
            virtualDisplay = projection?.createVirtualDisplay("AmbientCapture", screenW, screenH, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, handler)
            showOverlay()
        } catch (_: Exception) { stopSelf() }
        return START_NOT_STICKY
    }

    private fun createNotification() {
        val channel = "ambient_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channel, "Ambient Mode", NotificationManager.IMPORTANCE_LOW))
        }
        val stopIntent = PendingIntent.getService(
            this, 8, Intent(this, AmbientService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel)
            else Notification.Builder(this)
        val notification = builder.setContentTitle("Ambient Mode đang chạy")
            .setContentText("Hiệu ứng viền màu cho video")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tắt hiệu ứng", stopIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(7, notification)
    }

    private fun showOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlay = GlowView(this)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                   else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(screenW, screenH, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START
        wm?.addView(overlay, p)
    }

    private fun sample(image: Image) {
        val plane = image.planes[0]
        val buf = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenW
        val bmp = Bitmap.createBitmap(screenW + rowPadding / pixelStride, screenH, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buf)
        val rect = videoRect()
        val inset = max(2, min(rect.width(), rect.height()) / 100)
        val top = average(bmp, rect.left + inset, rect.top + inset, rect.right - inset, rect.top + max(3, inset * 3))
        val bottom = average(bmp, rect.left + inset, rect.bottom - max(3, inset * 3), rect.right - inset, rect.bottom - inset)
        val left = average(bmp, rect.left + inset, rect.top + inset, rect.left + max(3, inset * 3), rect.bottom - inset)
        val right = average(bmp, rect.right - max(3, inset * 3), rect.top + inset, rect.right - inset, rect.bottom - inset)
        bmp.recycle()
        runOnUi { overlay?.updateColors(top, bottom, left, right, rect) }
    }

    private fun average(b: Bitmap, x1: Int, y1: Int, x2: Int, y2: Int): Int {
        val l = x1.coerceIn(0, b.width - 1); val r = x2.coerceIn(l + 1, b.width)
        val t = y1.coerceIn(0, b.height - 1); val bot = y2.coerceIn(t + 1, b.height)
        var rr = 0L; var gg = 0L; var bb = 0L; var n = 0
        val sx = max(1, (r - l) / 36); val sy = max(1, (bot - t) / 12)
        var y = t
        while (y < bot) { var x = l; while (x < r) { val c = b.getPixel(x, y); rr += Color.red(c); gg += Color.green(c); bb += Color.blue(c); n++; x += sx }; y += sy }
        if (n == 0) return Color.BLACK
        return Color.rgb((rr / n).toInt(), (gg / n).toInt(), (bb / n).toInt())
    }

    private fun videoRect(): Rect {
        val sw = screenW.toFloat(); val sh = screenH.toFloat()
        val screenRatio = sw / sh
        return if (screenRatio > ratio) {
            val w = (sh * ratio).toInt(); Rect((screenW - w) / 2, 0, (screenW + w) / 2, screenH)
        } else {
            val h = (sw / ratio).toInt(); Rect(0, (screenH - h) / 2, screenW, (screenH + h) / 2)
        }
    }

    private fun runOnUi(block: () -> Unit) { Handler(Looper.getMainLooper()).post(block) }

    private fun releaseCaptureResources() {
        try { overlay?.let { wm?.removeView(it) } } catch (_: Exception) {}
        overlay = null
        virtualDisplay?.release(); virtualDisplay = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close(); reader = null
        projection?.stop(); projection = null
        handler?.looper?.quitSafely(); handler = null
    }

    override fun onDestroy() {
        releaseCaptureResources()
        super.onDestroy()
    }

    inner class GlowView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var colors = intArrayOf(Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK)
        private var video = Rect(0, 0, screenW, screenH)
        fun updateColors(t: Int, b: Int, l: Int, r: Int, rect: Rect) {
            colors = intArrayOf(t, b, l, r); video = rect; invalidate()
        }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            val strength = 0.88f
            val blur = min(180f * resources.displayMetrics.density, max(width, height) * 0.22f)
            val v = video
            // Paint only the letterbox area, never the video or hardcoded subtitle region.
            if (v.top > 0) drawGradient(c, RectF(0f, 0f, width.toFloat(), v.top.toFloat()), colors[0], true, blur, strength)
            if (v.bottom < height) drawGradient(c, RectF(0f, v.bottom.toFloat(), width.toFloat(), height.toFloat()), colors[1], false, blur, strength)
            if (v.left > 0) drawHorizontal(c, RectF(0f, v.top.toFloat(), v.left.toFloat(), v.bottom.toFloat()), colors[2], true, blur, strength)
            if (v.right < width) drawHorizontal(c, RectF(v.right.toFloat(), v.top.toFloat(), width.toFloat(), v.bottom.toFloat()), colors[3], false, blur, strength)
        }
        private fun drawGradient(c: Canvas, r: RectF, color: Int, top: Boolean, blur: Float, a: Float) {
            paint.shader = LinearGradient(if (top) 0f else 0f, if (top) r.bottom else r.top,
                0f, if (top) r.top else r.bottom, withAlpha(color, a), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(r, paint); paint.shader = null
        }
        private fun drawHorizontal(c: Canvas, r: RectF, color: Int, left: Boolean, blur: Float, a: Float) {
            paint.shader = LinearGradient(if (left) r.right else r.left, 0f, if (left) r.left else r.right, 0f,
                withAlpha(color, a), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(r, paint); paint.shader = null
        }
        private fun withAlpha(color: Int, a: Float) = Color.argb((Color.alpha(color) * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
