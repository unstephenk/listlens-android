package com.listlens.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

private val Context.ebayServerDataStore: DataStore<Preferences> by preferencesDataStore(name = "ebay_server_auth")

object EbayAuthServer {
  private val KEY_ACCESS = stringPreferencesKey("access_token")
  private val KEY_REFRESH = stringPreferencesKey("refresh_token")
  private val KEY_EXPIRES_AT = longPreferencesKey("expires_at_ms")

  data class TokenState(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAtMs: Long,
  ) {
    val isSignedIn: Boolean get() = !accessToken.isNullOrBlank() && System.currentTimeMillis() < expiresAtMs
  }

  fun tokenState(context: Context): Flow<TokenState> = context.ebayServerDataStore.data.map { prefs ->
    TokenState(
      accessToken = prefs[KEY_ACCESS],
      refreshToken = prefs[KEY_REFRESH],
      expiresAtMs = prefs[KEY_EXPIRES_AT] ?: 0L,
    )
  }

  suspend fun signOut(context: Context) {
    context.ebayServerDataStore.edit { prefs ->
      prefs.remove(KEY_ACCESS)
      prefs.remove(KEY_REFRESH)
      prefs.remove(KEY_EXPIRES_AT)
    }
  }

  /**
   * 1) Call server /ebay/oauth/start to get an authorize URL.
   * 2) Open it in a Custom Tab.
   * 3) Server will redirect to https://theark.io/ebay/app?sid=... which will open the app.
   */
  suspend fun launchSignIn(context: Context) {
    val authorizeUrl = fetchAuthorizeUrl()
    withContext(Dispatchers.Main) {
      CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authorizeUrl))
    }
  }

  suspend fun handleAppRedirect(context: Context, uri: Uri): Result<Unit> {
    // Expected: https://theark.io/ebay/app?sid=...
    val sid = uri.getQueryParameter("sid")?.trim().orEmpty()
    if (sid.isBlank()) return Result.failure(IllegalArgumentException("Missing sid"))

    return withContext(Dispatchers.IO) {
      runCatching {
        val url = URL(BuildConfig.THEARK_BASE_URL.trimEnd('/') + "/ebay/oauth/result?sid=" + urlEncode(sid))
        val conn = (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 10_000
          readTimeout = 10_000
          setRequestProperty("Accept", "application/json")
          setRequestProperty("User-Agent", "ListLens/0.0.1")
        }

        val codeHttp = conn.responseCode
        val stream = if (codeHttp in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (codeHttp !in 200..299) throw IllegalStateException("HTTP $codeHttp $text")

        val json = JSONObject(text)
        val access = json.optString("access_token").ifBlank { null }
        val refresh = json.optString("refresh_token").ifBlank { null }
        val expiresInSec = json.optLong("expires_in", 0L)
        val expiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresInSec)

        context.ebayServerDataStore.edit { p ->
          if (!access.isNullOrBlank()) p[KEY_ACCESS] = access
          if (!refresh.isNullOrBlank()) p[KEY_REFRESH] = refresh
          p[KEY_EXPIRES_AT] = expiresAt
        }
      }.map { }
    }
  }

  private fun fetchAuthorizeUrl(): String {
    val url = URL(BuildConfig.THEARK_BASE_URL.trimEnd('/') + "/ebay/oauth/start")
    val conn = (url.openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 10_000
      readTimeout = 10_000
      setRequestProperty("Accept", "application/json")
      setRequestProperty("User-Agent", "ListLens/0.0.1")
    }
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    if (code !in 200..299) throw IllegalStateException("OAuth start failed: HTTP $code $text")

    val json = JSONObject(text)
    return json.getString("authorize_url")
  }

  private fun urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
