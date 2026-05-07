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
        CrashHandler(this)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

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
                        runOnUiThread { showOfflineOverlay() }
                        view?.postDelayed({ loadConfig() }, 5000)
                    }
                }
                override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true) {
                        runOnUiThread { showOfflineOverlay() }
                        view?.postDelayed({ loadConfig() }, 5000)
                    }
                }
                
                override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                    val urlStr = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                    val cleanUrl = urlStr.split("?")[0]
                    
                    if (cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".webm") || cleanUrl.endsWith(".mkv")) {
                        val fileName = cleanUrl.substringAfterLast("/")
                        val qmsDir = File(Environment.getExternalStorageDirectory(), "QMS_Config")
                        if (!qmsDir.exists()) qmsDir.mkdirs()
                        val localFile = File(qmsDir, fileName)
                        
                        if (localFile.exists() && localFile.length() > 0) {
                            try {
                                val mimeType = if (cleanUrl.endsWith(".webm")) "video/webm" else "video/mp4"
                                return android.webkit.WebResourceResponse(mimeType, "UTF-8", java.io.FileInputStream(localFile))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Đang tải ngầm Video: $fileName...", Toast.LENGTH_LONG).show()
                            }
                            
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
                                    
                                    runOnUiThread {
                                        Toast.makeText(this@MainActivity, "Đã lưu video offline: $fileName", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    runOnUiThread { hideOfflineOverlay() }
                    view?.evaluateJavascript("""
                        (function() {
                            var videos = document.getElementsByTagName('video');
                            for(var i=0; i<videos.length; i++) {
                                var src = videos[i].src;
                                if (!src && videos[i].children.length > 0) {
                                    src = videos[i].children[0].src;
                                }
                                if(src && (src.indexOf('.mp4') !== -1 || src.indexOf('.webm') !== -1)) {
                                    return src;
                                }
                            }
                            return null;
                        })();
                    """.trimIndent()) { result ->
                        if (result != null && result != "null") {
                            val videoUrl = result.replace("\"", "")
                            downloadVideoIfNotExists(videoUrl)
                        }
                    }
                }
            }
        }

        val btnConfig = Button(this).apply {
            val size = (80 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, size).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            alpha = 1.0f // Hiển thị rõ hoàn toàn 100%
            text = "CẤU HÌNH"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.RED)
            
            setOnClickListener {
                showConfigMenu()
            }
        }

        frameLayout.addView(webView)
        frameLayout.addView(btnConfig)
        
        createOfflineOverlay(frameLayout)
        setContentView(frameLayout)

        checkPermissionsAndLoad()
        scheduleNightRestart()
    }

    private lateinit var offlineOverlay: android.widget.LinearLayout

    private fun createOfflineOverlay(parent: FrameLayout) {
        offlineOverlay = android.widget.LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            visibility = View.GONE
            
            val tvMsg = android.widget.TextView(this@MainActivity).apply {
                text = "Hệ thống đang tải hoặc mất kết nối...\nVui lòng chờ trong giây lát."
                setTextColor(android.graphics.Color.WHITE)
                textSize = 36f
                gravity = Gravity.CENTER
            }
            addView(tvMsg)
        }
        parent.addView(offlineOverlay)
    }

    private fun showOfflineOverlay() {
        offlineOverlay.visibility = View.VISIBLE
    }

    private fun hideOfflineOverlay() {
        offlineOverlay.visibility = View.GONE
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onBackPressed() {
        // Chặn phím Back để làm Kiosk Mode
    }

    private fun scheduleNightRestart() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 2)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        alarmManager.setRepeating(
            android.app.AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            android.app.AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun downloadVideoIfNotExists(urlStr: String) {
        val cleanUrl = urlStr.split("?")[0]
        val fileName = cleanUrl.substringAfterLast("/")
        val qmsDir = File(Environment.getExternalStorageDirectory(), "QMS_Config")
        if (!qmsDir.exists()) qmsDir.mkdirs()
        val localFile = File(qmsDir, fileName)
        
        if (!localFile.exists() || localFile.length() == 0L) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Phát hiện Video mới: $fileName\nĐang tải về QMS_Config...", Toast.LENGTH_LONG).show()
            }
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
                    
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "✅ Tải xong Video offline: $fileName", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
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
