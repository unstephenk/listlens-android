package com.listlens.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileFilter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Composable
fun CategoryScreen(
  onBooks: () -> Unit,
  onRecentIsbn: (String) -> Unit,
  onDrafts: () -> Unit,
  onEbaySignIn: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val recent = Prefs.recentIsbnsFlow(context).collectAsState(initial = emptyList())

  val draftCount = remember { mutableStateOf(0) }

  fun reloadDraftCount() {
    val photosRoot = File(context.filesDir, "photos")
    draftCount.value = photosRoot.listFiles()?.count { it.isDirectory } ?: 0
  }

  LaunchedEffect(Unit) {
    reloadDraftCount()
  }

  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) reloadDraftCount()
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }

  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("ListLens")
    Text("Pick a category to start")

    Button(onClick = onBooks, modifier = Modifier.fillMaxWidth()) {
      Text("Books")
    }

    if (recent.value.isNotEmpty()) {
      Text("Recent")
      recent.value.take(5).forEach { isbn ->
        val title = Prefs.bookTitleFlow(context, isbn).collectAsState(initial = null)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { onRecentIsbn(isbn) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            val t = title.value
            if (t.isNullOrBlank()) Text(isbn) else Text("$isbn — $t")
          }

          Button(
            onClick = { scope.launch { runCatching { Prefs.removeRecentIsbn(context, isbn) } } },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Remove")
          }
        }
      }

      Button(
        onClick = {
          // Best-effort clear; safe if it fails.
          scope.launch { runCatching { Prefs.clearRecentIsbns(context) } }
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Clear recent")
      }
    }

    if (draftCount.value > 0) {
      Button(
        onClick = onDrafts,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Drafts (${draftCount.value})")
      }
    }

    Spacer(Modifier.weight(1f))

    Button(onClick = onEbaySignIn, modifier = Modifier.fillMaxWidth()) {
      Text("Sign in to eBay (via theark.io)")
    }

    Text("Export: after photos, you’ll share a listing package (JSON + images).")
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
  onBack: () -> Unit,
  onResume: (String) -> Unit,
  onExport: (String) -> Unit,
  onConfirm: (String) -> Unit,
) {
  data class DraftRow(
    val isbn: String,
    val photoCount: Int,
    val lastUpdatedMs: Long,
    val title: String?,
  )
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val drafts = remember { mutableStateOf<List<DraftRow>>(emptyList()) }
  val error = remember { mutableStateOf<String?>(null) }
  val exportAllStatus = remember { mutableStateOf<String?>(null) }
  val filter = remember { mutableStateOf("") }

  val confirmDelete = remember { mutableStateOf<String?>(null) }
  val confirmDeleteAll = remember { mutableStateOf(false) }

  fun reload() {
    val photosRoot = File(context.filesDir, "photos")
    val list = photosRoot
      .listFiles(FileFilter { it.isDirectory })
      ?.map { dir ->
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        // Title is stored in DataStore; we read it in the UI layer.
        DraftRow(
          isbn = dir.name,
          photoCount = files.size,
          lastUpdatedMs = (files.maxOfOrNull { it.lastModified() } ?: dir.lastModified()),
          title = null,
        )
      }
      ?.sortedByDescending { it.lastUpdatedMs }
      ?: emptyList()
    drafts.value = list
  }

  LaunchedEffect(Unit) {
    runCatching { reload() }.onFailure { error.value = it.message }
  }

  Scaffold(
    topBar = { TopAppBar(title = { Text("Drafts") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      error.value?.let { Text("Error: $it") }
      exportAllStatus.value?.let { Text(it) }

      OutlinedTextField(
        value = filter.value,
        onValueChange = { filter.value = it },
        label = { Text("Filter (ISBN/title)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
      )

      if (drafts.value.isEmpty()) {
        Text("No drafts yet.")
      } else {
        val df = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }

        drafts.value
          .filter { row ->
            val q = filter.value.trim()
            if (q.isBlank()) true
            else row.isbn.contains(q, ignoreCase = true)
          }
          .forEach { row ->
          val title = Prefs.bookTitleFlow(context, row.isbn).collectAsState(initial = null)
          val updatedAt = Prefs.updatedAtFlow(context, row.isbn).collectAsState(initial = null)

          val t = title.value
          val label = if (t.isNullOrBlank()) row.isbn else "${row.isbn} — $t"
          val stamp = updatedAt.value ?: row.lastUpdatedMs
          val updated = runCatching { df.format(Date(stamp)) }.getOrNull()

          val cond = Prefs.conditionFlow(context, row.isbn).collectAsState(initial = null)
          val notes = Prefs.notesFlow(context, row.isbn).collectAsState(initial = null)

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { onResume(row.isbn) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              val suffix = if (updated == null) "" else " • $updated"
              Text("$label (${row.photoCount} photos)$suffix")
            }

            val metaLine = buildString {
              val c = cond.value
              val n = notes.value
              if (!c.isNullOrBlank()) append("Condition: $c")
              if (!n.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append("Notes: ")
                append(n.take(60))
                if (n.length > 60) append("…")
              }
            }
            if (metaLine.isNotBlank()) Text(metaLine)

            Button(
              onClick = { onExport(row.isbn) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Export")
            }

            Button(
              onClick = { onConfirm(row.isbn) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Edit/Confirm ISBN")
            }

            Button(
              onClick = {
                runCatching {
                  val dir = File(context.filesDir, "photos/${row.isbn}")
                  dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
                }
                reload()
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Clear photos")
            }

            Button(
              onClick = { confirmDelete.value = row.isbn },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Delete draft")
            }
          }
        }

        Button(
          onClick = { reload() },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Refresh")
        }

        Button(
          onClick = {
            exportAllStatus.value = "Exporting…"
            scope.launch {
              val authority = context.packageName + ".fileprovider"
              val photosRoot = File(context.filesDir, "photos")
              val isbns = drafts.value.map { it.isbn }
              val cacheOut = File(context.cacheDir, "exports/batch/${System.currentTimeMillis()}").apply { mkdirs() }

              val batchZip = withContext(Dispatchers.IO) {
                runCatching {
                  val batchDir = File(cacheOut, "batch").apply { mkdirs() }

                  var included = 0
                  isbns.forEach { isbn ->
                    val photosDir = File(photosRoot, isbn)
                    if (!photosDir.exists()) return@forEach
                    val files = photosDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
                    if (files.isEmpty()) return@forEach

                    val exportDir = File(batchDir, isbn).apply { mkdirs() }
                    val copied = files.mapIndexed { index, src ->
                      val ext = src.extension.ifBlank { "jpg" }
                      val dst = File(exportDir, String.format(Locale.US, "photo_%02d.%s", index + 1, ext))
                      src.copyTo(dst, overwrite = true)
                      dst
                    }

                    val title = runCatching { Prefs.bookTitleFlow(context, isbn).first() }.getOrNull()
                    val cond = runCatching { Prefs.conditionFlow(context, isbn).first() }.getOrNull() ?: ""
                    val notes = runCatching { Prefs.notesFlow(context, isbn).first() }.getOrNull() ?: ""

                    val json = JSONObject().apply {
                      put("schemaVersion", 1)
                      put("createdAtMs", System.currentTimeMillis())
                      put("isbn13", isbn)
                      put("title", title)
                      put("condition", cond)
                      put("notes", notes)
                      put("descriptionText", listingText(isbn = isbn, title = title, condition = cond, notes = notes))
                      put("coverUrl", BookMetadata.coverUrl(isbn, size = "L"))
                      put("photos", JSONArray(copied.map { it.name }))
                    }
                    File(exportDir, "listing.json").writeText(json.toString(2))
                    File(exportDir, "listing.txt").writeText(listingText(isbn = isbn, title = title, condition = cond, notes = notes) + "\n")

                    included++
                  }

                  if (included == 0) return@runCatching null

                  val zip = File(cacheOut, "listlens_batch_${System.currentTimeMillis()}.zip")
                  zipDirectoryToFile(batchDir, zip)
                  zip
                }.getOrNull()
              }

              if (batchZip == null) {
                exportAllStatus.value = "No drafts with photos to export."
                return@launch
              }

              val uri = FileProvider.getUriForFile(context, authority, batchZip)
              val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
              runCatching { context.startActivity(Intent.createChooser(intent, "Share batch ZIP")) }
              exportAllStatus.value = "Export ready (batch ZIP)."
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Export all drafts (batch ZIP)")
        }

        Button(
          onClick = { confirmDeleteAll.value = true },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Delete all drafts")
        }
      }

      Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
  }

  confirmDelete.value?.let { isbn ->
    AlertDialog(
      onDismissRequest = { confirmDelete.value = null },
      title = { Text("Delete draft?") },
      text = { Text("This deletes the draft folder for ISBN $isbn (photos, etc).") },
      confirmButton = {
        Button(
          onClick = {
            runCatching {
              val dir = File(context.filesDir, "photos/$isbn")
              dir.deleteRecursively()
              reload()
            }
            confirmDelete.value = null
          },
        ) { Text("Delete") }
      },
      dismissButton = {
        Button(onClick = { confirmDelete.value = null }) { Text("Cancel") }
      },
    )
  }

  if (confirmDeleteAll.value) {
    AlertDialog(
      onDismissRequest = { confirmDeleteAll.value = false },
      title = { Text("Delete ALL drafts?") },
      text = { Text("This deletes all stored draft photos. This cannot be undone.") },
      confirmButton = {
        Button(
          onClick = {
            runCatching {
              val dir = File(context.filesDir, "photos")
              dir.deleteRecursively()
              dir.mkdirs()
              reload()
            }
            confirmDeleteAll.value = false
          },
        ) { Text("Delete all") }
      },
      dismissButton = {
        Button(onClick = { confirmDeleteAll.value = false }) { Text("Cancel") }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBooksScreen(
  title: String,
  onBack: () -> Unit,
  onIsbnFound: (String) -> Unit,
) {
  enum class ScanSource { BARCODE, OCR, MANUAL }
  data class ScanHit(val isbn13: String, val source: ScanSource )

  val haptics = LocalHapticFeedback.current

  val hasPermission = remember { mutableStateOf(false) }
  val requestPermission = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
    onResult = { granted -> hasPermission.value = granted },
  )

  LaunchedEffect(Unit) {
    requestPermission.launch(Manifest.permission.CAMERA)
  }

  // Prevent double-emits when analyzer fires multiple frames.
  val didEmit = remember { mutableStateOf(false) }
  val latestOnIsbnFound = rememberUpdatedState(onIsbnFound)

  // Lightweight status + overlay.
  val status = remember { mutableStateOf("Scanning barcode…") }
  val hit = remember { mutableStateOf<ScanHit?>(null) }

  fun resetScan() {
    didEmit.value = false
    hit.value = null
    status.value = "Scanning barcode…"
  }

  // Manual entry for deterministic testing (and fallback when camera struggles).
  val showManualDialog = remember { mutableStateOf(false) }
  val manualText = remember { mutableStateOf("") }

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
            onHit = { found ->
              if (didEmit.value) return@CameraIsbnScanner
              didEmit.value = true
              hit.value = found
              status.value = "ISBN found"
              haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            },
          )

          // Overlay when found
          hit.value?.let { found ->
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
                Text(found.isbn13, color = Color.White)
                Text(
                  text = when (found.source) {
                    ScanSource.BARCODE -> "Source: barcode"
                    ScanSource.OCR -> "Source: OCR"
                    ScanSource.MANUAL -> "Source: manual"
                  },
                  color = Color.White,
                )

                RowButtons(
                  left = "Retake",
                  right = "Use this",
                  onLeft = { resetScan() },
                  onRight = { latestOnIsbnFound.value(found.isbn13) },
                )

                Button(onClick = { showManualDialog.value = true }) {
                  Text("Manual entry")
                }

                Text("If this looks wrong, retake or enter it manually.", color = Color.White)
              }
            }
          }

          // Navigation is explicit (tap) to avoid accidental scans while moving the camera.
          // (We keep the overlay so you can visually confirm the ISBN before proceeding.)
        }
      }

      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(status.value)
        Text("Tip: Try the back cover barcode first. OCR will kick in if needed.")

        Button(
          onClick = {
            manualText.value = ""
            showManualDialog.value = true
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Enter ISBN manually")
        }

        Button(
          onClick = onBack,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Back")
        }
      }
    }

    if (showManualDialog.value) {
      AlertDialog(
        onDismissRequest = { showManualDialog.value = false },
        title = { Text("Enter ISBN") },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Paste ISBN-13 or ISBN-10. We'll validate + convert to ISBN-13.")
            OutlinedTextField(
              value = manualText.value,
              onValueChange = { manualText.value = it },
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
              val isbn = Isbn.extractIsbn13(manualText.value)
              if (isbn != null) {
                showManualDialog.value = false
                didEmit.value = true
                hit.value = ScanHit(isbn13 = isbn, source = ScanSource.MANUAL)
                status.value = "ISBN found"
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
              } else {
                status.value = "Invalid ISBN"
              }
            },
          ) { Text("Use") }
        },
        dismissButton = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { manualText.value = "" }) { Text("Clear") }
            Button(onClick = { showManualDialog.value = false }) { Text("Cancel") }
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
  onHit: (ScanBooksScreen.ScanHit) -> Unit,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val latestOnHit = rememberUpdatedState(onHit)
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
                  val isbn = raw?.let(Isbn::extractIsbn13)
                  if (isbn != null) {
                    latestOnStatus.value("Found ISBN via barcode")
                    latestOnHit.value(ScanBooksScreen.ScanHit(isbn13 = isbn, source = ScanBooksScreen.ScanSource.BARCODE))
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
                      val ocrIsbn = Isbn.extractIsbn13(ocrText)
                      if (ocrIsbn != null) {
                        latestOnStatus.value("Found ISBN via OCR")
                        latestOnHit.value(ScanBooksScreen.ScanHit(isbn13 = ocrIsbn, source = ScanBooksScreen.ScanSource.OCR))
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
fun ConfirmBookScreen(
  initialIsbn: String,
  onBack: () -> Unit,
  onAccept: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val isbnText = remember { mutableStateOf(initialIsbn) }
  val normalized = remember(isbnText.value) { Isbn.extractIsbn13(isbnText.value) }
  val effectiveIsbn = normalized ?: ""

  val state = remember { mutableStateOf<BookLookupState>(BookLookupState.Loading) }
  val lookupError = remember { mutableStateOf<String?>(null) }

  LaunchedEffect(effectiveIsbn) {
    lookupError.value = null
    if (effectiveIsbn.isBlank()) {
      state.value = BookLookupState.Error("Enter a valid ISBN")
      return@LaunchedEffect
    }

    // Small debounce so typing doesn't spam the network.
    delay(350)

    state.value = BookLookupState.Loading
    state.value = try {
      val book = BookMetadata.lookupByIsbn(effectiveIsbn)
      if (book == null) BookLookupState.NotFound else BookLookupState.Found(book)
    } catch (e: Exception) {
      lookupError.value = e.message
      BookLookupState.Error(e.message ?: "Lookup failed")
    }
  }

  Scaffold(
    topBar = { TopAppBar(title = { Text("Confirm") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      val coverUrl = remember(effectiveIsbn) {
        if (effectiveIsbn.isBlank()) null else BookMetadata.coverUrl(effectiveIsbn, size = "L")
      }

      coverUrl?.let {
        // Best-effort cover art (Open Library). If there's no cover, Coil will just fail silently.
        Image(
          painter = rememberAsyncImagePainter(it),
          contentDescription = "Book cover",
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111111))
            .padding(8.dp),
        )
      }

      Text("ISBN")
      OutlinedTextField(
        value = isbnText.value,
        onValueChange = { isbnText.value = it },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
          val s = normalized
          if (s == null) Text("Paste ISBN-13 or ISBN-10 (we'll normalize to ISBN-13).")
          else Text("Normalized ISBN-13: $s")
        },
      )

      when (val s = state.value) {
        is BookLookupState.Loading -> Text("Looking up book info…")
        is BookLookupState.NotFound -> Text("No Open Library match found (you can still continue).")
        is BookLookupState.Error -> Text("Lookup: ${s.message}")
        is BookLookupState.Found -> {
          // Cache for the home "Recent" list.
          LaunchedEffect(s.book.title) {
            val isbnToCache = normalized ?: return@LaunchedEffect
            runCatching { Prefs.setBookTitle(context, isbnToCache, s.book.title) }
          }

          Text("Title: ${s.book.title}")
          s.book.publishDate?.let { Text("Published: $it") }
          s.book.publishers?.takeIf { it.isNotEmpty() }?.let { Text("Publisher: ${it.first()}") }
        }
      }

      RowButtons(
        left = "Retake",
        right = "Use this",
        onLeft = onBack,
        onRight = {
          val isbn13 = normalized
          if (isbn13 != null && Isbn.isValidIsbn13(isbn13)) {
            scope.launch { runCatching { Prefs.addRecentIsbn(context, isbn13) } }
            onAccept(isbn13)
          } else lookupError.value = "Invalid ISBN"
        },
      )

      lookupError.value?.let { Text("Error: $it") }
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

private fun analyzePhotoForWarnings(file: File): String? {
  // Very lightweight heuristic checks: "too dark" and "too flat".
  // (Not a substitute for real blur/glare detection, but catches obvious failures.)
  return runCatching {
    val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@runCatching null
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return@runCatching null

    var sum = 0.0
    var sumSq = 0.0
    var count = 0

    // Sample a grid of pixels (avoid full scan)
    val stepX = maxOf(1, w / 48)
    val stepY = maxOf(1, h / 48)
    var y = 0
    while (y < h) {
      var x = 0
      while (x < w) {
        val c = bmp.getPixel(x, y)
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = (c) and 0xFF
        val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
        sum += luma
        sumSq += luma * luma
        count++
        x += stepX
      }
      y += stepY
    }

    if (count == 0) return@runCatching null
    val mean = sum / count
    val varr = (sumSq / count) - (mean * mean)

    when {
      mean < 35 -> "Warning: photo looks very dark"
      varr < 120 -> "Warning: photo may be blurry/low-contrast"
      else -> null
    }
  }.getOrNull()
}

private fun listingText(
  isbn: String,
  title: String?,
  condition: String,
  notes: String,
): String = buildString {
  appendLine(title ?: "(unknown title)")
  appendLine("ISBN: $isbn")
  appendLine("Condition: $condition")
  if (notes.isNotBlank()) appendLine("Notes: $notes")
}.trim()

private fun zipDirectoryToFile(dir: File, zipFile: File) {
  ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
    val basePath = dir.absolutePath.trimEnd(File.separatorChar) + File.separator
    val files = dir.walkTopDown().filter { it.isFile }.toList()
    files.forEach { f ->
      val relative = f.absolutePath.removePrefix(basePath).replace(File.separatorChar, '/')
      val entry = ZipEntry(relative)
      zos.putNextEntry(entry)
      f.inputStream().use { it.copyTo(zos) }
      zos.closeEntry()
    }
  }
}

private object BookMetadata {
  suspend fun lookupByIsbn(isbn13: String): BookInfo? {
    // Primary: Open Library (fast, free)
    val open = runCatching { openLibraryLookup(isbn13) }.getOrNull()
    if (open != null) return open

    // Fallback: Google Books (no API key required for basic isbn search)
    return runCatching { googleBooksLookup(isbn13) }.getOrNull()
  }

  private suspend fun openLibraryLookup(isbn13: String): BookInfo? = withContext(Dispatchers.IO) {
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

  private suspend fun googleBooksLookup(isbn13: String): BookInfo? = withContext(Dispatchers.IO) {
    val url = URL("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn13")
    val conn = (url.openConnection() as HttpURLConnection).apply {
      connectTimeout = 7000
      readTimeout = 7000
      requestMethod = "GET"
      setRequestProperty("Accept", "application/json")
      setRequestProperty("User-Agent", "ListLens/0.0.1")
    }

    try {
      val code = conn.responseCode
      if (code !in 200..299) return@withContext null

      val body = conn.inputStream.bufferedReader().use { it.readText() }
      val json = JSONObject(body)
      val items = json.optJSONArray("items") ?: return@withContext null
      if (items.length() == 0) return@withContext null

      val first = items.optJSONObject(0) ?: return@withContext null
      val vol = first.optJSONObject("volumeInfo") ?: return@withContext null

      val title = vol.optString("title").ifBlank { "(unknown title)" }
      val publishedDate = vol.optString("publishedDate").ifBlank { null }
      val publishers = vol.optString("publisher").ifBlank { null }?.let { listOf(it) }

      BookInfo(
        title = title,
        publishDate = publishedDate,
        publishers = publishers,
      )
    } finally {
      conn.disconnect()
    }
  }

  fun coverUrl(isbn13: String, size: String = "L"): String =
    "https://covers.openlibrary.org/b/isbn/$isbn13-$size.jpg?default=false"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
  isbn: String,
  onBack: () -> Unit,
  onContinue: () -> Unit,
) {
  val context = LocalContext.current

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
  val warningText = remember { mutableStateOf<String?>(null) }
  val showLastWarning = remember { mutableStateOf(false) }

  fun reloadPhotos() {
    // Load any existing photos for this ISBN.
    photos.value = photosDir
      .listFiles()
      ?.filter { it.isFile }
      ?.sortedBy { it.name }
      ?: emptyList()
  }

  fun normalizePhotoNames() {
    // Keep deterministic ordering + stable filenames so reorder is easy.
    // photo_01.jpg ... photo_05.jpg
    val current = photosDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    if (current.isEmpty()) return

    // First rename to temp to avoid collisions.
    val temp = current.mapIndexed { i, f ->
      val ext = f.extension.ifBlank { "jpg" }
      val tmp = File(photosDir, "tmp_${System.currentTimeMillis()}_${i}.$ext")
      f.renameTo(tmp)
      tmp
    }

    temp.sortedBy { it.name }.forEachIndexed { idx, f ->
      val ext = f.extension.ifBlank { "jpg" }
      val finalName = String.format(Locale.US, "photo_%02d.%s", idx + 1, ext)
      val dst = File(photosDir, finalName)
      f.renameTo(dst)
    }
  }

  fun movePhoto(index: Int, delta: Int) {
    val list = photos.value
    val newIndex = index + delta
    if (index !in list.indices) return
    if (newIndex !in list.indices) return

    // Ensure names are normalized first so swap is clean.
    normalizePhotoNames()
    reloadPhotos()

    val updated = photos.value
    if (updated.size < 2) return
    val a = updated[index]
    val b = updated[newIndex]

    // swap using temp name
    val tmp = File(photosDir, "swap_${System.currentTimeMillis()}.${a.extension.ifBlank { "jpg" }}")
    a.renameTo(tmp)
    b.renameTo(a)
    tmp.renameTo(b)

    normalizePhotoNames()
    reloadPhotos()
  }

  LaunchedEffect(photosDir) {
    normalizePhotoNames()
    reloadPhotos()
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
              // After capture, normalize filenames so ordering stays stable.
              runCatching { normalizePhotoNames() }
              reloadPhotos()
              // In case rename failed, still append the new file.
              if (photos.value.none { it.absolutePath == file.absolutePath }) {
                photos.value = (photos.value + file).distinctBy { it.absolutePath }
              }
              errorText.value = null
              warningText.value = analyzePhotoForWarnings(file)
              showLastWarning.value = warningText.value != null
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
        warningText.value?.let { warn ->
          Button(
            onClick = { showLastWarning.value = true },
            modifier = Modifier.fillMaxWidth(),
          ) { Text("View last photo warning") }
        }

        if (photos.value.isNotEmpty()) {
          LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            items(photos.value.withIndex().toList(), key = { it.value.absolutePath }) { indexed ->
              val idx = indexed.index
              val file = indexed.value
              Box {
                Image(
                  painter = rememberAsyncImagePainter(file),
                  contentDescription = "photo",
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.DarkGray),
                )

                // Simple reorder controls (tap up/down). This is less fancy than drag & drop,
                // but it’s deterministic and works great on-device.
                Column(
                  modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = "${idx + 1}",
                    color = Color.White,
                    modifier = Modifier
                      .background(Color(0x88000000))
                      .padding(horizontal = 6.dp, vertical = 2.dp),
                  )

                  Button(
                    onClick = { movePhoto(idx, -1) },
                    enabled = idx > 0,
                  ) { Text("Up") }

                  Button(
                    onClick = { movePhoto(idx, +1) },
                    enabled = idx < photos.value.lastIndex,
                  ) { Text("Down") }
                }

                IconButton(
                  onClick = {
                    runCatching { file.delete() }
                    runCatching { normalizePhotoNames() }
                    reloadPhotos()
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
          onClick = onContinue,
          modifier = Modifier.fillMaxWidth(),
          enabled = photos.value.isNotEmpty(),
        ) {
          Text("Continue")
        }

        if (showLastWarning.value && warningText.value != null) {
          AlertDialog(
            onDismissRequest = { showLastWarning.value = false },
            title = { Text("Photo warning") },
            text = { Text(warningText.value ?: "") },
            confirmButton = {
              Button(onClick = { showLastWarning.value = false }) { Text("OK") }
            },
          )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageScreen(
  isbn: String,
  onBack: () -> Unit,
  onDone: () -> Unit,
) {
  val context = LocalContext.current

  val state = remember { mutableStateOf<BookLookupState>(BookLookupState.Loading) }
  LaunchedEffect(isbn) {
    state.value = BookLookupState.Loading
    state.value = try {
      val book = BookMetadata.lookupByIsbn(isbn)
      if (book == null) BookLookupState.NotFound else BookLookupState.Found(book)
    } catch (e: Exception) {
      BookLookupState.Error(e.message ?: "Lookup failed")
    }
  }

  val conditionOptions = listOf(
    "Like New",
    "Very Good",
    "Good",
    "Acceptable",
    "Poor",
  )
  val condition = remember { mutableStateOf(conditionOptions.first()) }
  val conditionMenuOpen = remember { mutableStateOf(false) }
  val notes = remember { mutableStateOf("") }

  // Persist condition + notes per ISBN so you can bounce around screens without losing context.
  LaunchedEffect(isbn) {
    val saved = runCatching { Prefs.conditionFlow(context, isbn).first() }.getOrNull()
    if (!saved.isNullOrBlank() && saved in conditionOptions) condition.value = saved
  }
  LaunchedEffect(isbn) {
    val saved = runCatching { Prefs.notesFlow(context, isbn).first() }.getOrNull()
    if (saved != null) notes.value = saved
  }

  // Save changes (light debounce).
  LaunchedEffect(condition.value) {
    delay(250)
    runCatching { Prefs.setCondition(context, isbn, condition.value) }
  }
  LaunchedEffect(notes.value) {
    delay(350)
    runCatching { Prefs.setNotes(context, isbn, notes.value) }
  }

  val lastExportPath = remember { mutableStateOf<String?>(null) }
  val error = remember { mutableStateOf<String?>(null) }

  // Read photos from the per-ISBN directory.
  val photosDir = remember(isbn) { File(context.filesDir, "photos/$isbn").apply { mkdirs() } }
  val photos = remember { mutableStateOf<List<File>>(emptyList()) }
  LaunchedEffect(photosDir) {
    photos.value = photosDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
  }

  Scaffold(
    topBar = { TopAppBar(title = { Text("Export") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("ISBN: $isbn")

      when (val s = state.value) {
        is BookLookupState.Loading -> Text("Looking up book info…")
        is BookLookupState.NotFound -> Text("No Open Library match found (export will still work).")
        is BookLookupState.Error -> Text("Lookup error: ${s.message}")
        is BookLookupState.Found -> {
          Text("Title: ${s.book.title}")
          s.book.publishDate?.let { Text("Published: $it") }
          s.book.publishers?.firstOrNull()?.let { Text("Publisher: $it") }
        }
      }

      // Condition picker
      Box {
        OutlinedTextField(
          value = condition.value,
          onValueChange = { },
          readOnly = true,
          label = { Text("Condition") },
          trailingIcon = {
            IconButton(onClick = { conditionMenuOpen.value = true }) {
              Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Pick")
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
          expanded = conditionMenuOpen.value,
          onDismissRequest = { conditionMenuOpen.value = false },
        ) {
          conditionOptions.forEach { opt ->
            DropdownMenuItem(
              text = { Text(opt) },
              onClick = {
                condition.value = opt
                conditionMenuOpen.value = false
              },
            )
          }
        }
      }

      OutlinedTextField(
        value = notes.value,
        onValueChange = { notes.value = it },
        label = { Text("Notes") },
        modifier = Modifier.fillMaxWidth(),
      )

      Text("Photos included: ${photos.value.size}")

      error.value?.let { Text("Error: $it") }
      lastExportPath.value?.let { Text("Last export: $it") }

      Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = photos.value.isNotEmpty(),
        onClick = {
          error.value = null
          try {
            val exportDir = File(context.cacheDir, "exports/$isbn/${System.currentTimeMillis()}").apply { mkdirs() }

            // Copy photos
            val copied = photos.value.mapIndexed { index, src ->
              val ext = src.extension.ifBlank { "jpg" }
              val dst = File(exportDir, String.format(Locale.US, "photo_%02d.%s", index + 1, ext))
              src.copyTo(dst, overwrite = true)
              dst
            }

            val book = (state.value as? BookLookupState.Found)?.book
            val json = JSONObject().apply {
              put("schemaVersion", 1)
              put("createdAtMs", System.currentTimeMillis())
              put("isbn13", isbn)
              put("title", book?.title)
              put("publishDate", book?.publishDate)
              put("publishers", book?.publishers?.let { JSONArray(it) })
              put("condition", condition.value)
              put("notes", notes.value)
              put(
                "descriptionText",
                listingText(
                  isbn = isbn,
                  title = book?.title,
                  condition = condition.value,
                  notes = notes.value,
                ),
              )
              put("coverUrl", BookMetadata.coverUrl(isbn, size = "L"))
              put(
                "photos",
                JSONArray(copied.map { it.name }),
              )
            }
            val jsonFile = File(exportDir, "listing.json")
            jsonFile.writeText(json.toString(2))

            // Also write a plain-text template for convenience.
            val txtFile = File(exportDir, "listing.txt")
            txtFile.writeText(
              listingText(
                isbn = isbn,
                title = book?.title,
                condition = condition.value,
                notes = notes.value,
              ) + "\n",
            )

            // Zip export
            val zipFile = File(exportDir.parentFile, "listlens_${isbn}_${System.currentTimeMillis()}.zip")
            zipDirectoryToFile(exportDir, zipFile)

            val authority = context.packageName + ".fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, zipFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "application/zip"
              putExtra(Intent.EXTRA_STREAM, uri)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share listing ZIP"))
            lastExportPath.value = zipFile.absolutePath
          } catch (e: Exception) {
            error.value = e.message ?: "Export failed"
          }
        },
      ) {
        Text("Share listing ZIP")
      }

      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          val book = (state.value as? BookLookupState.Found)?.book
          val text = listingText(
            isbn = isbn,
            title = book?.title,
            condition = condition.value,
            notes = notes.value,
          )

          val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
          }
          context.startActivity(Intent.createChooser(intent, "Share listing text"))
        },
      ) {
        Text("Share listing text")
      }

      Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
          val book = (state.value as? BookLookupState.Found)?.book
          val text = listingText(
            isbn = isbn,
            title = book?.title,
            condition = condition.value,
            notes = notes.value,
          )

          val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
          cm.setPrimaryClip(ClipData.newPlainText("ListLens listing", text))
          lastExportPath.value = "Copied listing text to clipboard"
        },
      ) {
        Text("Copy listing text")
      }

      RowButtons(
        left = "Back",
        right = "Done",
        onLeft = onBack,
        onRight = onDone,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoneScreen(
  isbn: String,
  onScanNext: () -> Unit,
  onDrafts: () -> Unit,
  onHome: () -> Unit,
) {
  val context = LocalContext.current
  val title = Prefs.bookTitleFlow(context, isbn).collectAsState(initial = null)

  Scaffold(
    topBar = { TopAppBar(title = { Text("Done") }) },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val t = title.value
      Text(if (t.isNullOrBlank()) "Saved draft for $isbn" else "Saved draft for $isbn — $t")
      Text("Next?")

      Button(onClick = onScanNext, modifier = Modifier.fillMaxWidth()) {
        Text("Scan next book")
      }

      Button(onClick = onDrafts, modifier = Modifier.fillMaxWidth()) {
        Text("View drafts")
      }

      Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
        Text("Back to home")
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
