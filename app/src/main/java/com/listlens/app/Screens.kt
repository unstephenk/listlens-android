package com.listlens.app

import android.Manifest
import android.annotation.SuppressLint
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.rememberAsyncImagePainter
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun CategoryScreen(
  onBooks: () -> Unit,
  onEbaySignIn: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("ListLens")
    Text("Pick a category to start")

    Button(onClick = onBooks, modifier = Modifier.fillMaxWidth()) {
      Text("Books")
    }

    Spacer(Modifier.weight(1f))

    Button(onClick = onEbaySignIn, modifier = Modifier.fillMaxWidth()) {
      Text("Sign in to eBay (Sandbox)")
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

  // Lightweight status + overlay.
  val status = remember { mutableStateOf("Scanning barcode…") }
  val foundIsbn = remember { mutableStateOf<String?>(null) }

  // Debug manual entry for deterministic testing on emulator.
  val showDebugDialog = remember { mutableStateOf(false) }
  val debugText = remember { mutableStateOf("9780143127741") }

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
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "Camera permission is required to scan.",
              color = Color.White,
            )
            Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) {
              Text("Grant permission")
            }
          }
        } else {
          CameraIsbnScanner(
            paused = didEmit.value,
            onStatus = { status.value = it },
            onIsbn = { isbn ->
              if (didEmit.value) return@CameraIsbnScanner
              didEmit.value = true
              foundIsbn.value = isbn
              status.value = "ISBN found"
              haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            },
          )

          // Overlay when found
          foundIsbn.value?.let { isbn ->
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000)),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text("ISBN detected", color = Color.White)
                Text(isbn, color = Color.White)
                Text("Hold still…", color = Color.White)
              }
            }
          }

          LaunchedEffect(foundIsbn.value) {
            val isbn = foundIsbn.value ?: return@LaunchedEffect
            // Small pause so the user sees the result.
            delay(1200)
            latestOnIsbnFound.value(isbn)
          }
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(status.value)
        Text("Tip: Try the back cover barcode first. OCR will kick in if needed.")

        if (BuildConfig.DEBUG) {
          Button(
            onClick = {
              // Reset to a known-good value each time for deterministic testing.
              debugText.value = "9780143127741"
              showDebugDialog.value = true
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Debug: Enter ISBN")
          }
        }

        Button(
          onClick = onBack,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Back")
        }
      }
    }

    if (BuildConfig.DEBUG && showDebugDialog.value) {
      AlertDialog(
        onDismissRequest = { showDebugDialog.value = false },
        title = { Text("Enter ISBN") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Paste ISBN-13 or ISBN-10. We'll validate + convert to ISBN-13.")
            OutlinedTextField(
              value = debugText.value,
              onValueChange = { debugText.value = it },
              singleLine = true,
              label = { Text("ISBN") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth(),
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val isbn = extractIsbnFromAny(debugText.value)
              if (isbn != null) {
                showDebugDialog.value = false
                if (!didEmit.value) {
                  didEmit.value = true
                  foundIsbn.value = isbn
                  status.value = "ISBN found"
                  haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
              } else {
                status.value = "Invalid ISBN"
              }
            },
          ) { Text("Use") }
        },
        dismissButton = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { debugText.value = "" }) { Text("Clear") }
            Button(onClick = { showDebugDialog.value = false }) { Text("Cancel") }
          }
        },
      )
    }
  }
}

private fun extractIsbnFromAny(raw: String): String? {
  // Pull out digits and try ISBN-13 first.
  val digits = raw.filter { it.isDigit() }
  if (digits.length >= 13) {
    // Scan windows of 13 digits (OCR sometimes includes extra digits).
    for (i in 0..(digits.length - 13)) {
      val candidate = digits.substring(i, i + 13)
      if ((candidate.startsWith("978") || candidate.startsWith("979")) && isValidEan13(candidate)) return candidate
    }
  }
  // Fallback: ISBN-10 → ISBN-13
  if (digits.length >= 10) {
    for (i in 0..(digits.length - 10)) {
      val candidate10 = digits.substring(i, i + 10)
      val converted = isbn10To13(candidate10)
      if (converted != null) return converted
    }
  }
  return null
}

private fun isbn10To13(isbn10: String): String? {
  if (isbn10.length != 10) return null
  val core = isbn10.substring(0, 9)
  if (!core.all { it.isDigit() }) return null
  val check10 = isbn10.last()
  val check10Value = when {
    check10.isDigit() -> check10.digitToInt()
    check10 == 'X' || check10 == 'x' -> 10
    else -> return null
  }
  if (check10Value != calcIsbn10Check(core)) return null

  val base12 = "978$core"
  val check13 = calcEan13CheckDigit(base12)
  return base12 + check13
}

private fun calcIsbn10Check(core9: String): Int {
  // Weighted sum 10..2
  val sum = core9.mapIndexed { index, c ->
    val weight = 10 - index
    weight * c.digitToInt()
  }.sum()
  val r = 11 - (sum % 11)
  return when (r) {
    10 -> 10
    11 -> 0
    else -> r
  }
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

private fun calcEan13CheckDigit(first12Digits: String): Int {
  require(first12Digits.length == 12)
  val sum = first12Digits.mapIndexed { index, c ->
    val n = c.digitToInt()
    if (index % 2 == 0) n else n * 3
  }.sum()
  return (10 - (sum % 10)) % 10
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraIsbnScanner(
  paused: Boolean,
  onStatus: (String) -> Unit,
  onIsbn: (String) -> Unit,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val latestOnIsbn = rememberUpdatedState(onIsbn)
  val latestOnStatus = rememberUpdatedState(onStatus)

  // Barcode scanner (fast path).
  val barcodeScanner = remember {
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

  // OCR (fallback path).
  val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
  val frameCount = remember { AtomicInteger(0) }

  DisposableEffect(Unit) {
    onDispose {
      barcodeScanner.close()
      textRecognizer.close()
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
              if (paused) {
                imageProxy.close()
                return@setAnalyzer
              }

              val mediaImage = imageProxy.image
              if (mediaImage == null) {
                imageProxy.close()
                return@setAnalyzer
              }

              val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

              // 1) Try barcode first.
              barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                  val raw = barcodes.firstOrNull()?.rawValue
                  val isbn = raw?.let(::extractIsbnFromAny)
                  if (isbn != null) {
                    latestOnStatus.value("Found ISBN via barcode")
                    latestOnIsbn.value(isbn)
                    imageProxy.close()
                    return@addOnSuccessListener
                  }

                  // 2) OCR fallback periodically (keep it light).
                  val n = frameCount.incrementAndGet()
                  val doOcr = n % 12 == 0
                  if (!doOcr) {
                    latestOnStatus.value("Scanning barcode…")
                    imageProxy.close()
                    return@addOnSuccessListener
                  }

                  latestOnStatus.value("No barcode yet — trying OCR…")
                  textRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                      val ocrText = visionText.text
                      val ocrIsbn = extractIsbnFromAny(ocrText)
                      if (ocrIsbn != null) {
                        latestOnStatus.value("Found ISBN via OCR")
                        latestOnIsbn.value(ocrIsbn)
                      }
                    }
                    .addOnCompleteListener {
                      imageProxy.close()
                    }
                }
                .addOnFailureListener {
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
      val coverUrl = remember(isbn) { "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false" }

      // Best-effort cover art (Open Library). If there's no cover, Coil will just fail silently.
      Image(
        painter = rememberAsyncImagePainter(coverUrl),
        contentDescription = "Book cover",
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF111111))
          .padding(8.dp),
      )

      Text("Detected ISBN:")
      Text(isbn)
      val state = remember { mutableStateOf<BookLookupState>(BookLookupState.Loading) }

      LaunchedEffect(isbn) {
        state.value = BookLookupState.Loading
        state.value = try {
          val book = OpenLibrary.lookupByIsbn(isbn)
          if (book == null) BookLookupState.NotFound else BookLookupState.Found(book)
        } catch (e: Exception) {
          BookLookupState.Error(e.message ?: "Lookup failed")
        }
      }

      when (val s = state.value) {
        is BookLookupState.Loading -> Text("Looking up book info…")
        is BookLookupState.NotFound -> Text("No Open Library match found (we can still continue).")
        is BookLookupState.Error -> Text("Lookup error: ${s.message}")
        is BookLookupState.Found -> {
          Text("Title: ${s.book.title}")
          s.book.publishDate?.let { Text("Published: $it") }
          s.book.publishers?.takeIf { it.isNotEmpty() }?.let { Text("Publisher: ${it.first()}") }
        }
      }

      RowButtons(
        left = "Retake",
        right = "Use this",
        onLeft = onBack,
        onRight = onAccept,
      )
    }
  }
}

sealed interface BookLookupState {
  data object Loading : BookLookupState
  data object NotFound : BookLookupState
  data class Error(val message: String) : BookLookupState
  data class Found(val book: BookInfo) : BookLookupState
}

data class BookInfo(
  val title: String,
  val publishDate: String?,
  val publishers: List<String>?,
)

private object OpenLibrary {
  suspend fun lookupByIsbn(isbn13: String): BookInfo? = withContext(Dispatchers.IO) {
    val url = URL("https://openlibrary.org/isbn/$isbn13.json")
    val conn = (url.openConnection() as HttpURLConnection).apply {
      connectTimeout = 6000
      readTimeout = 6000
      requestMethod = "GET"
      setRequestProperty("Accept", "application/json")
      setRequestProperty("User-Agent", "ListLens/0.0.1")
    }

    try {
      val code = conn.responseCode
      if (code == 404) return@withContext null
      if (code !in 200..299) throw IllegalStateException("HTTP $code")

      val body = conn.inputStream.bufferedReader().use { it.readText() }
      val json = JSONObject(body)

      val title = json.optString("title").ifBlank { "(unknown title)" }
      val publishDate = json.optString("publish_date").ifBlank { null }
      val publishersJson = json.optJSONArray("publishers")
      val publishers = publishersJson?.let { arr ->
        buildList {
          for (i in 0 until arr.length()) {
            val p = arr.optString(i)
            if (!p.isNullOrBlank()) add(p)
          }
        }
      }

      BookInfo(
        title = title,
        publishDate = publishDate,
        publishers = publishers,
      )
    } finally {
      conn.disconnect()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
  isbn: String,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val hasPermission = remember { mutableStateOf(false) }
  val requestPermission = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted -> hasPermission.value = granted },
  )

  LaunchedEffect(Unit) {
    requestPermission.launch(Manifest.permission.CAMERA)
  }

  val photosDir = remember(isbn) { File(context.filesDir, "photos/$isbn").apply { mkdirs() } }
  val photos = remember { mutableStateOf<List<File>>(emptyList()) }
  val errorText = remember { mutableStateOf<String?>(null) }

  LaunchedEffect(photosDir) {
    // Load any existing photos for this ISBN.
    photos.value = photosDir
      .listFiles()
      ?.filter { it.isFile }
      ?.sortedBy { it.name }
      ?: emptyList()
  }

  Scaffold(
    topBar = { TopAppBar(title = { Text("Photos") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "ISBN: $isbn",
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
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "Camera permission is required to take photos.",
              color = Color.White,
            )
            Button(onClick = { requestPermission.launch(Manifest.permission.CAMERA) }) {
              Text("Grant permission")
            }
          }
        } else {
          CameraPhotoCapture(
            enabled = photos.value.size < 5,
            outputDir = photosDir,
            onCaptured = { file ->
              photos.value = (photos.value + file).distinctBy { it.absolutePath }
              errorText.value = null
            },
            onError = { msg ->
              errorText.value = msg
            },
          )
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("Photos: ${photos.value.size}/5")
        errorText.value?.let { Text("Error: $it") }

        if (photos.value.isNotEmpty()) {
          LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            items(photos.value, key = { it.absolutePath }) { file ->
              Box {
                Image(
                  painter = rememberAsyncImagePainter(file),
                  contentDescription = "photo",
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.DarkGray),
                )
                IconButton(
                  onClick = {
                    runCatching { file.delete() }
                    photos.value = photosDir
                      .listFiles()
                      ?.filter { it.isFile }
                      ?.sortedBy { it.name }
                      ?: emptyList()
                  },
                  modifier = Modifier.align(Alignment.TopEnd),
                ) {
                  Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                  )
                }
              }
            }
          }
        }

        Button(
          onClick = {
            // TODO: next step is eBay OAuth + upload.
          },
          modifier = Modifier.fillMaxWidth(),
          enabled = photos.value.isNotEmpty(),
        ) {
          Text("Continue")
        }

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

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraPhotoCapture(
  enabled: Boolean,
  outputDir: File,
  onCaptured: (File) -> Unit,
  onError: (String) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

  val latestOnCaptured = rememberUpdatedState(onCaptured)
  val latestOnError = rememberUpdatedState(onError)

  val imageCaptureState = remember { mutableStateOf<ImageCapture?>(null) }

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

            val imageCapture = ImageCapture.Builder()
              .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
              .build()
            imageCaptureState.value = imageCapture

            try {
              cameraProvider.unbindAll()
              cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
              )
            } catch (e: Exception) {
              latestOnError.value("Camera bind failed: ${e.message}")
            }
          },
          mainExecutor,
        )
      }
    },
  )

  // Floating capture button overlay
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    Button(
      onClick = {
        val imageCapture = imageCaptureState.value ?: return@Button
        if (!enabled) return@Button

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(outputDir, "IMG_$ts.jpg")

        val output = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
          output,
          mainExecutor,
          object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
              latestOnCaptured.value(file)
            }

            override fun onError(exception: ImageCaptureException) {
              latestOnError.value("Capture failed: ${exception.message}")
            }
          },
        )
      },
      enabled = enabled,
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      Text(if (enabled) "Take photo" else "Max photos reached")
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
