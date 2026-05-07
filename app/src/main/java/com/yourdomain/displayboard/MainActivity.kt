package com.yourdomain.displayboard

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen ON
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start Background Service
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
                mediaPlaybackRequiresUserGesture = false // Bypass Audio Auto-play
                cacheMode = WebSettings.LOAD_NO_CACHE // Always fresh for Display
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null) // Hardware acceleration for CSS animations
            webViewClient = WebViewClient()
            
            // THAY BẰNG URL SERVER CỦA BẠN
            loadUrl("http://192.168.1.xxx:5000/duong-dan-display") 
        }
        setContentView(webView)
    }
}
