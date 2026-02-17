package com.listlens.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun ListLensApp() {
  MaterialTheme {
    val nav = rememberNavController()

    Scaffold { _ ->
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
            onAccept = { nav.navigate("photos") },
          )
        }
        composable("photos") {
          PhotosPlaceholderScreen(onBack = { nav.popBackStack() })
        }
      }
    }
  }
}
