package com.listlens.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Handle possible app-link on cold start.
    intent?.data?.let { handleAppLink(it) }
    setContent { ListLensApp() }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.data?.let { handleAppLink(it) }
  }

  private fun handleAppLink(uri: Uri) {
    // Handoff URL can arrive as either:
    // - https://theark.io/ebay/app?sid=...   (Android App Links)
    // - listlens://ebay/app?sid=...         (dev fallback, no verification)
    val isHttpsAppLink = (uri.scheme == "https" && uri.host == "theark.io" && uri.path?.startsWith("/ebay/app") == true)
    val isSchemeFallback = (uri.scheme == "listlens" && uri.host == "ebay" && uri.path?.startsWith("/app") == true)

    if (isHttpsAppLink || isSchemeFallback) {
      DeepLinks.latest.value = uri
    }
  }
}
