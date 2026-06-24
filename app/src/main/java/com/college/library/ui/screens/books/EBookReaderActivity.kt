package com.college.library.ui.screens.books

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar

class EBookReaderActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("ebook_url") ?: return
        
        val finalUrl = if (url.contains("drive.google.com")) {
            val fileId = extractDriveFileId(url)
            if (fileId.isNotEmpty()) "https://drive.google.com/file/d/$fileId/preview" else url
        } else {
            url
        }

        val frameLayout = FrameLayout(this)
        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
        }
        val progressBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
            }
        }
        
        frameLayout.addView(webView)
        frameLayout.addView(progressBar)
        setContentView(frameLayout)
        
        webView.loadUrl(finalUrl)
    }

    private fun extractDriveFileId(url: String): String {
        val patterns = listOf("/file/d/([a-zA-Z0-9_-]+)", "id=([a-zA-Z0-9_-]+)", "/d/([a-zA-Z0-9_-]+)")
        patterns.forEach { pattern ->
            val match = Regex(pattern).find(url)
            if (match != null) return match.groupValues[1]
        }
        return ""
    }
}
