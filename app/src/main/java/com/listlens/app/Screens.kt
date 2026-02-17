package com.listlens.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CategoryScreen(onBooks: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("ListLens")
    Text("Pick a category to start")

    Button(onClick = onBooks, modifier = Modifier.fillMaxWidth()) {
      Text("Books")
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPlaceholderScreen(
  title: String,
  onBack: () -> Unit,
  onFakeIsbn: (String) -> Unit,
) {
  val (isbn, setIsbn) = remember { mutableStateOf("9780143127741") }

  Scaffold(
    topBar = { TopAppBar(title = { Text("Scan") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(title)
      Text("TODO: CameraX + ML Kit barcode scan (ISBN-first), OCR fallback")

      OutlinedTextField(
        value = isbn,
        onValueChange = setIsbn,
        label = { Text("(Dev) ISBN") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )

      Button(onClick = { onFakeIsbn(isbn) }, modifier = Modifier.fillMaxWidth()) {
        Text("Continue")
      }

      Spacer(Modifier.weight(1f))
      Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("Back")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmPlaceholderScreen(
  isbn: String,
  onBack: () -> Unit,
  onAccept: () -> Unit,
) {
  Scaffold(
    topBar = { TopAppBar(title = { Text("Confirm") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("Detected ISBN:")
      Text(isbn)
      Text("TODO: ISBN-pure lookup → title/author/year/publisher/edition + confidence")

      RowButtons(
        left = "Retake",
        right = "Use this",
        onLeft = onBack,
        onRight = onAccept,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosPlaceholderScreen(onBack: () -> Unit) {
  Scaffold(
    topBar = { TopAppBar(title = { Text("Photos") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("TODO: capture up to 5 photos total")
      Text("Then: eBay OAuth → upload photos → create draft listing")

      Spacer(Modifier.weight(1f))
      Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("Back")
      }
    }
  }
}

@Composable
private fun RowButtons(
  left: String,
  right: String,
  onLeft: () -> Unit,
  onRight: () -> Unit,
) {
  androidx.compose.foundation.layout.Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Button(onClick = onLeft, modifier = Modifier.weight(1f)) { Text(left) }
    Button(onClick = onRight, modifier = Modifier.weight(1f)) { Text(right) }
  }
}
