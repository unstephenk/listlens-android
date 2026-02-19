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
    // https://theark.io/ebay/app?sid=...
    if (uri.host == "theark.io" && uri.path?.startsWith("/ebay/app") == true) {
      DeepLinks.latest.value = uri
    }
  }
}
