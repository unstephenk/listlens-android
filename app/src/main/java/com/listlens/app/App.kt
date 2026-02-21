package com.listlens.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun ListLensApp() {
  MaterialTheme {
    val nav = rememberNavController()

    Scaffold { _ ->
      // Handle OAuth handoff deep link.
      // - https://theark.io/ebay/app?sid=...   (Android App Links)
      // - listlens://ebay/app?sid=...         (dev fallback, no verification)
      val pendingLink = DeepLinks.latest.value
      LaunchedEffect(pendingLink) {
        val uri = DeepLinks.consume() ?: return@LaunchedEffect
        val isHttpsAppLink = (uri.scheme == "https" && uri.host == "theark.io" && uri.path?.startsWith("/ebay/app") == true)
        val isSchemeFallback = (uri.scheme == "listlens" && uri.host == "ebay" && uri.path?.startsWith("/app") == true)
        if (isHttpsAppLink || isSchemeFallback) {
          val encoded = Uri.encode(uri.toString())
          nav.navigate("ebay-handoff?uri=$encoded")
        }
      }

      NavHost(
        navController = nav,
        startDestination = "category",
        modifier = Modifier,
      ) {
        composable("category") {
          CategoryScreen(
            onBooks = { nav.navigate("scan/books") },
            onRecentIsbn = { isbn -> nav.navigate("confirm/$isbn") },
            onDrafts = { nav.navigate("drafts") },
            onEbaySignIn = { nav.navigate("ebay") },
          )
        }
        composable("ebay") {
          EbayHomeScreen(
            onBack = { nav.popBackStack() },
          )
        }
        composable("ebay-handoff?uri={uri}") { backStack ->
          val raw = backStack.arguments?.getString("uri") ?: ""
          EbayHandoffScreen(
            uriString = Uri.decode(raw),
            onDone = { nav.popBackStack("category", inclusive = false) },
          )
        }

        composable("drafts") {
          DraftsScreen(
            onBack = { nav.popBackStack() },
            onResume = { isbn -> nav.navigate("photos/$isbn") },
            onExport = { isbn -> nav.navigate("package/$isbn") },
            onConfirm = { isbn -> nav.navigate("confirm/$isbn") },
          )
        }

        composable("scan/books") {
          ScanBooksScreen(
            title = "Books (auto-detect ISBN)",
            onBack = { nav.popBackStack() },
            onIsbnFound = { isbn -> nav.navigate("confirm/$isbn") },
          )
        }
        composable("confirm/{isbn}") { backStack ->
          val isbn = backStack.arguments?.getString("isbn") ?: ""
          ConfirmBookScreen(
            initialIsbn = isbn,
            onBack = { nav.popBackStack() },
            onAccept = { confirmedIsbn -> nav.navigate("photos/$confirmedIsbn") },
          )
        }
        composable("photos/{isbn}") { backStack ->
          val isbn = backStack.arguments?.getString("isbn") ?: ""
          PhotosScreen(
            isbn = isbn,
            onBack = { nav.popBackStack() },
            onContinue = { nav.navigate("package/$isbn") },
          )
        }
        composable("package/{isbn}") { backStack ->
          val isbn = backStack.arguments?.getString("isbn") ?: ""
          PackageScreen(
            isbn = isbn,
            onBack = { nav.popBackStack() },
            onDone = { nav.navigate("done/$isbn") },
          )
        }

        composable("done/{isbn}") { backStack ->
          val isbn = backStack.arguments?.getString("isbn") ?: ""
          DoneScreen(
            isbn = isbn,
            onScanNext = { nav.navigate("scan/books") },
            onDrafts = { nav.navigate("drafts") },
            onDeleteDraft = {
              // Delete stored photos for this ISBN then go home.
              val ctx = androidx.compose.ui.platform.LocalContext.current
              runCatching {
                java.io.File(ctx.filesDir, "photos/$isbn").deleteRecursively()
              }
              nav.popBackStack("category", inclusive = false)
            },
            onHome = { nav.popBackStack("category", inclusive = false) },
          )
        }
      }
    }
  }
}
