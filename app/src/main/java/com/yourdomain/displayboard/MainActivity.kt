package com.yourdomain.displayboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val defaultUrl = "http://192.168.1.189:5000/duong-dan-display"

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

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = WebViewClient()
        }
        setContentView(webView)

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
}
