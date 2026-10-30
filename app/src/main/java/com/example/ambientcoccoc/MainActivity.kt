package com.example.ambientcoccoc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val captureRequest = 41
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(TextView(this).apply {
            text = "AMBIENT CỐC CỐC\nTạo ánh sáng màu ở viền đen khi xem video"
            textSize = 22f
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Cách dùng: chọn tỷ lệ gần đúng của video, nhấn Bật Ambient và đồng ý chia sẻ màn hình. Sau đó chuyển về Cốc Cốc và mở video toàn màn hình. Nếu vùng sáng lệch, tắt hiệu ứng rồi thử tỷ lệ khác."
            textSize = 15f
            setPadding(0, pad, 0, pad)
        })
        val ratio = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Video 16:9 (phổ biến)", "Video 2:1 (phim điện ảnh)", "Video 21:9", "Video 4:3"))
        }
        root.addView(TextView(this).apply { text = "Tỷ lệ khung hình video" })
        root.addView(ratio)
        val start = Button(this).apply {
            text = "BẬT AMBIENT MODE"
            setOnClickListener {
                getSharedPreferences("ambient", MODE_PRIVATE).edit()
                    .putFloat("ratio", when (ratio.selectedItemPosition) { 1 -> 2f; 2 -> 21f / 9f; 3 -> 4f / 3f; else -> 16f / 9f }).apply()
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                    Toast.makeText(this@MainActivity, "Hãy cấp quyền hiển thị trên ứng dụng khác, sau đó quay lại và nhấn Bật lần nữa.", Toast.LENGTH_LONG).show()
                } else {
                    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    startActivityForResult(mgr.createScreenCaptureIntent(), captureRequest)
                }
            }
        }
        root.addView(start, ViewGroup.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "TẮT HIỆU ỨNG"
            setOnClickListener { stopService(Intent(this@MainActivity, AmbientService::class.java)) }
        }, ViewGroup.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Lưu ý: bản thử nghiệm lấy màu từ màn hình. Nội dung DRM/bị chặn quay màn hình có thể không lấy được màu. Khi dùng, Android sẽ hiện thông báo đang chia sẻ màn hình."
            textSize = 12f
            setPadding(0, pad, 0, 0)
        })
        setContentView(root)
    }

    @Deprecated("Deprecated in Android API; kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, AmbientService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startForegroundService(i)
            Toast.makeText(this, "Ambient Mode đã bật. Mở lại Cốc Cốc để xem video.", Toast.LENGTH_LONG).show()
        }
    }
}
