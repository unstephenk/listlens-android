package com.listlens.app

import android.Manifest
import android.annotation.SuppressLint
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

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
fun ScanBooksScreen(
  title: String,
  onBack: () -> Unit,
  onIsbnFound: (String) -> Unit,
) {
  val haptics = LocalHapticFeedback.current

  val hasPermission = remember { mutableStateOf(false) }
  val requestPermission = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted -> hasPermission.value = granted },
  )

  LaunchedEffect(Unit) {
    requestPermission.launch(Manifest.permission.CAMERA)
  }

  // Prevent double-navigation when analyzer fires multiple frames.
  val didEmit = remember { mutableStateOf(false) }
  val latestOnIsbnFound = rememberUpdatedState(onIsbnFound)

  Scaffold(
    topBar = { TopAppBar(title = { Text("Scan") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = title,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = 16.dp)
          .background(Color.Black),
        contentAlignment = Alignment.Center,
      ) {
        if (!hasPermission.value) {
          Text(
            text = "Camera permission is required to scan.",
            color = Color.White,
            modifier = Modifier.padding(16.dp),
          )
        } else {
          CameraBarcodeScanner(
            onBarcodeValue = { raw ->
              if (didEmit.value) return@CameraBarcodeScanner
              val isbn = raw?.let(::extractIsbn13)
              if (isbn != null) {
                didEmit.value = true
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                latestOnIsbnFound.value(isbn)
              }
            },
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("Tip: Point the camera at the barcode. We'll auto-detect ISBN.")
        Button(
          onClick = onBack,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Back")
        }
      }
    }
  }
}

private fun extractIsbn13(raw: String): String? {
  val digits = raw.filter { it.isDigit() }
  if (digits.length != 13) return null
  if (!(digits.startsWith("978") || digits.startsWith("979"))) return null
  return if (isValidEan13(digits)) digits else null
}

private fun isValidEan13(digits: String): Boolean {
  if (digits.length != 13 || !digits.all { it.isDigit() }) return false
  val sum = digits.dropLast(1).mapIndexed { index, c ->
    val n = c.digitToInt()
    if (index % 2 == 0) n else n * 3
  }.sum()
  val check = (10 - (sum % 10)) % 10
  return check == digits.last().digitToInt()
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraBarcodeScanner(
  onBarcodeValue: (String?) -> Unit,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val latestOnBarcodeValue = rememberUpdatedState(onBarcodeValue)

  val scanner = remember {
    val options = BarcodeScannerOptions.Builder()
      .setBarcodeFormats(
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
      )
      .build()
    BarcodeScanning.getClient(options)
  }

  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

  DisposableEffect(Unit) {
    onDispose {
      scanner.close()
      analysisExecutor.shutdown()
    }
  }

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      PreviewView(ctx).also { pv ->
        pv.scaleType = PreviewView.ScaleType.FILL_CENTER

        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener(
          {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
              .setTargetResolution(Size(1280, 720))
              .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
              .build()

            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
              val mediaImage = imageProxy.image
              if (mediaImage == null) {
                imageProxy.close()
                return@setAnalyzer
              }

              val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
              scanner.process(image)
                .addOnSuccessListener { barcodes ->
                  val raw = barcodes.firstOrNull()?.rawValue
                  latestOnBarcodeValue.value(raw)
                }
                .addOnCompleteListener {
                  imageProxy.close()
                }
            }

            try {
              cameraProvider.unbindAll()
              cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
              )
            } catch (_: Exception) {
              // If binding fails, we just show a blank preview.
            }
          },
          ContextCompat.getMainExecutor(ctx),
        )
      }
    },
  )
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
