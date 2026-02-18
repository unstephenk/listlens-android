package com.listlens.app

import android.net.Uri
import androidx.compose.runtime.mutableStateOf

object DeepLinks {
  // Latest incoming VIEW intent data.
  val latest = mutableStateOf<Uri?>(null)

  fun consume(): Uri? {
    val v = latest.value
    latest.value = null
    return v
  }
}
