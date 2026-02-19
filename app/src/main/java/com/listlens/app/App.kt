package com.listlens.app

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
      // If we got an OAuth deep link, route it.
      val pendingLink = DeepLinks.latest.value
      LaunchedEffect(pendingLink) {
        val uri = DeepLinks.consume() ?: return@LaunchedEffect
        if (uri.host == "theark.io" && uri.path?.startsWith("/ebay/oauth") == true) {
          val encoded = Uri.encode(uri.toString())
          nav.navigate("ebay-redirect?uri=$encoded")
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
          )
        }
        // eBay flows are intentionally kept in code for later, but UI is deferred for now.
        composable("ebay") {
          EbayHomeScreen(
            onBack = { nav.popBackStack() },
          )
        }
        composable("ebay-redirect?uri={uri}") { backStack ->
          val raw = backStack.arguments?.getString("uri") ?: ""
          EbayRedirectScreen(
            uriString = Uri.decode(raw),
            onDone = { nav.popBackStack("category", inclusive = false) },
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
          ConfirmPlaceholderScreen(
            isbn = isbn,
            onBack = { nav.popBackStack() },
            onAccept = { nav.navigate("photos/$isbn") },
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
            onDone = { nav.popBackStack("category", inclusive = false) },
          )
        }
      }
    }
  }
}
