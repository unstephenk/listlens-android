package com.listlens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Handle possible deep link on cold start.
    DeepLinks.latest.value = intent?.data
    setContent { ListLensApp() }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    DeepLinks.latest.value = intent.data
  }
}
