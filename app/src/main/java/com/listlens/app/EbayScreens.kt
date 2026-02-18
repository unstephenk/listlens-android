package com.listlens.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EbayHomeScreen(
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val tokenState = EbayAuth.tokenState(context).collectAsState(initial = EbayAuth.TokenState(null, null, 0L))
  val status = remember { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = { TopAppBar(title = { Text("eBay") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(if (tokenState.value.isSignedIn) "Signed in (token valid)" else "Not signed in")

      if (!tokenState.value.isSignedIn) {
        Button(
          onClick = {
            status.value = "Opening eBay sign-in…"
            scope.launch {
              EbayAuth.launchSignIn(context)
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Sign in to eBay (Sandbox)")
        }

        Text(
          "After signing in, you should be redirected to theark.io and then back to the app. " +
            "If Android shows a chooser, pick ListLens.",
        )
      } else {
        Button(
          onClick = {
            status.value = null
            scope.launch { EbayAuth.signOut(context) }
          },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Sign out") }
      }

      status.value?.let { Text(it) }

      Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EbayRedirectScreen(
  uriString: String,
  onDone: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val uri = remember(uriString) { runCatching { Uri.parse(uriString) }.getOrNull() }
  val parsed = remember(uriString) { EbayAuth.parseRedirect(uri) }

  val status = remember { mutableStateOf<String?>(null) }
  val manualCode = remember { mutableStateOf(parsed.code ?: "") }

  LaunchedEffect(uriString) {
    status.value = null
    if (!parsed.error.isNullOrBlank()) {
      status.value = "OAuth error: ${parsed.error} ${parsed.errorDescription ?: ""}".trim()
      return@LaunchedEffect
    }

    val code = parsed.code
    if (code.isNullOrBlank()) {
      status.value = "No authorization code found in redirect."
      return@LaunchedEffect
    }

    status.value = "Authorization code received. Exchanging for token…"
    val res = EbayAuth.exchangeCodeForToken(context, code)
    status.value = res.fold(
      onSuccess = { "Signed in successfully." },
      onFailure = { "Token exchange failed: ${it.message}" },
    )
  }

  Scaffold(
    topBar = { TopAppBar(title = { Text("eBay Sign-in") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Redirect received:")
      Text(uriString.take(240) + if (uriString.length > 240) "…" else "")

      status.value?.let { Text(it) }

      OutlinedTextField(
        value = manualCode.value,
        onValueChange = { manualCode.value = it },
        label = { Text("Authorization code (if needed)") },
        modifier = Modifier.fillMaxWidth(),
      )

      Button(
        onClick = {
          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          cm.setPrimaryClip(ClipData.newPlainText("ebay_code", manualCode.value))
          status.value = "Copied code to clipboard."
        },
        modifier = Modifier.fillMaxWidth(),
      ) { Text("Copy code") }

      Button(
        onClick = {
          status.value = "Exchanging code for token…"
          scope.launch {
            val res = EbayAuth.exchangeCodeForToken(context, manualCode.value)
            status.value = res.fold(
              onSuccess = { "Signed in successfully." },
              onFailure = { "Token exchange failed: ${it.message}" },
            )
          }
        },
        modifier = Modifier.fillMaxWidth(),
      ) { Text("Try token exchange") }

      Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }

      Text(
        "Dev note: eBay token exchange usually requires the client secret. " +
          "Set Gradle property EBAY_CLIENT_SECRET for sandbox dev, or move exchange to your server for production.",
      )
    }
  }
}
