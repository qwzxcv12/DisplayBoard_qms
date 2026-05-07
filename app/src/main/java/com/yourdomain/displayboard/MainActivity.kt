package com.yourdomain.displayboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val defaultUrl = "http://192.168.1.189:5000/duong-dan-display"
    private var currentUrl = defaultUrl

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val serviceIntent = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val frameLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        view?.postDelayed({ loadConfig() }, 5000)
                    }
                }
                override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true) {
                        view?.postDelayed({ loadConfig() }, 5000)
                    }
                }
                
                override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                    val urlStr = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                    
                    if (urlStr.endsWith(".mp4") || urlStr.endsWith(".webm") || urlStr.endsWith(".mkv")) {
                        val fileName = urlStr.substringAfterLast("/")
                        val qmsDir = File(Environment.getExternalStorageDirectory(), "QMS_Config")
                        if (!qmsDir.exists()) qmsDir.mkdirs()
                        val localFile = File(qmsDir, fileName)
                        
                        if (localFile.exists() && localFile.length() > 0) {
                            try {
                                val mimeType = if (urlStr.endsWith(".webm")) "video/webm" else "video/mp4"
                                return android.webkit.WebResourceResponse(mimeType, "UTF-8", java.io.FileInputStream(localFile))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            // Download in background
                            kotlin.concurrent.thread {
                                try {
                                    val tempFile = File(qmsDir, "$fileName.download")
                                    val connection = java.net.URL(urlStr).openConnection()
                                    connection.connect()
                                    val input = connection.getInputStream()
                                    val output = java.io.FileOutputStream(tempFile)
                                    val data = ByteArray(4096)
                                    var count: Int
                                    while (input.read(data).also { count = it } != -1) {
                                        output.write(data, 0, count)
                                    }
                                    output.flush()
                                    output.close()
                                    input.close()
                                    tempFile.renameTo(localFile)
                                    Log.d("QMS", "Downloaded video to cache: $fileName")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
        }

        val btnConfig = Button(this).apply {
            val size = (60 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            alpha = 0.0f
            text = "⚙"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            
            setOnHoverListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> v.alpha = 1.0f
                    MotionEvent.ACTION_HOVER_EXIT -> v.alpha = 0.0f
                }
                false
            }
            
            setOnClickListener {
                alpha = 1.0f
                showConfigMenu()
                postDelayed({ alpha = 0.0f }, 3000) // Ẩn lại sau 3s nếu dùng cảm ứng
            }
        }

        frameLayout.addView(webView)
        frameLayout.addView(btnConfig)
        setContentView(frameLayout)

        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        } else {
            loadConfig()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            loadConfig()
        }
    }

    private fun loadConfig() {
        var finalUrl = defaultUrl
        try {
            val qmsDir = File(Environment.getExternalStorageDirectory(), "QMS_Config")
            if (!qmsDir.exists()) {
                qmsDir.mkdirs()
            }
            val file = File(qmsDir, "qms_config.xml")
            if (file.exists()) {
                val content = file.readText()
                val match = Regex("<url>(.*?)</url>").find(content)
                if (match != null && match.groupValues.size > 1) {
                    finalUrl = match.groupValues[1].trim()
                    currentUrl = finalUrl
                    Toast.makeText(this, "Đã đọc cấu hình: $finalUrl", Toast.LENGTH_LONG).show()
                }
            } else {
                try {
                    file.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<config>\n    <!-- Thay doi url duong dan o ben duoi -->\n    <url>$defaultUrl</url>\n</config>")
                    Toast.makeText(this, "Đã tạo file cấu hình tại mục QMS_Config", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("QMS", "Không thể tạo file mẫu: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("QMS", "Lỗi đọc file: ${e.message}")
        }
        webView.loadUrl(finalUrl)
    }

    private fun showConfigMenu() {
        val options = arrayOf("Tải lại trang (Refresh)", "Cấu hình đường dẫn")
        AlertDialog.Builder(this)
            .setTitle("Tuỳ chọn")
            .setItems(options) { _, which ->
                if (which == 0) {
                    Toast.makeText(this, "Đang tải lại giao diện...", Toast.LENGTH_SHORT).show()
                    loadConfig()
                } else {
                    showPasswordDialog()
                }
            }
            .show()
    }

    private fun showPasswordDialog() {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = "Nhập mật khẩu"

        AlertDialog.Builder(this)
            .setTitle("Yêu cầu mật khẩu")
            .setView(input)
            .setPositiveButton("Xác nhận") { _, _ ->
                if (input.text.toString() == "2024") {
                    showEditUrlDialog()
                } else {
                    Toast.makeText(this, "Sai mật khẩu!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun showEditUrlDialog() {
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        input.setText(currentUrl)
        
        AlertDialog.Builder(this)
            .setTitle("Cấu hình URL hệ thống")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val newUrl = input.text.toString().trim()
                saveUrlToConfig(newUrl)
                loadConfig()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun saveUrlToConfig(newUrl: String) {
        try {
            val qmsDir = File(Environment.getExternalStorageDirectory(), "QMS_Config")
            if (!qmsDir.exists()) qmsDir.mkdirs()
            val file = File(qmsDir, "qms_config.xml")
            file.writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<config>\n    <!-- Thay doi url duong dan o ben duoi -->\n    <url>$newUrl</url>\n</config>")
            Toast.makeText(this, "Lưu thành công!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khi lưu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // Nút OK, Enter hoặc Play/Pause trên remote TV Box
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || 
            keyCode == android.view.KeyEvent.KEYCODE_ENTER || 
            keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            Toast.makeText(this, "Đang tải lại giao diện...", Toast.LENGTH_SHORT).show()
            loadConfig()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
