package com.listlens.app

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
  val tokenState = EbayAuthServer.tokenState(context).collectAsState(initial = EbayAuthServer.TokenState(null, null, 0L))
  val status = remember { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = { TopAppBar(title = { Text("eBay") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(if (tokenState.value.isSignedIn) "Signed in" else "Not signed in")

      if (!tokenState.value.isSignedIn) {
        Button(
          onClick = {
            status.value = "Opening eBay sign-in…"
            scope.launch {
              runCatching { EbayAuthServer.launchSignIn(context) }
                .onFailure { status.value = "Start failed: ${it.message}" }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Sign in")
        }

        Text(
          "This sign-in uses theark.io as the redirect handler so the eBay client secret stays on the server."
        )
      } else {
        Button(
          onClick = {
            scope.launch { EbayAuthServer.signOut(context) }
            status.value = null
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
fun EbayHandoffScreen(
  uriString: String,
  onDone: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val status = remember { mutableStateOf<String?>("Completing sign-in…") }

  LaunchedEffect(uriString) {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull()
    if (uri == null) {
      status.value = "Invalid redirect URI"
      return@LaunchedEffect
    }

    scope.launch {
      val res = EbayAuthServer.handleAppRedirect(context, uri)
      status.value = res.fold(
        onSuccess = { "Signed in successfully." },
        onFailure = { "Sign-in failed: ${it.message}" },
      )
    }
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

      Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
  }
}
