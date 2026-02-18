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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

private val Context.ebayDataStore: DataStore<Preferences> by preferencesDataStore(name = "ebay_auth")

object EbayAuth {
  // eBay OAuth endpoints
  private fun authorizeBase(): String = when (BuildConfig.EBAY_ENV) {
    "sandbox" -> "https://auth.sandbox.ebay.com/oauth2/authorize"
    else -> "https://auth.ebay.com/oauth2/authorize"
  }

  private fun tokenUrl(): String = when (BuildConfig.EBAY_ENV) {
    "sandbox" -> "https://api.sandbox.ebay.com/identity/v1/oauth2/token"
    else -> "https://api.ebay.com/identity/v1/oauth2/token"
  }

  private val KEY_ACCESS = stringPreferencesKey("access_token")
  private val KEY_REFRESH = stringPreferencesKey("refresh_token")
  private val KEY_EXPIRES_AT = longPreferencesKey("expires_at_ms")
  private val KEY_LAST_CODE_VERIFIER = stringPreferencesKey("last_code_verifier")
  private val KEY_LAST_STATE = stringPreferencesKey("last_state")

  data class TokenState(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAtMs: Long,
  ) {
    val isSignedIn: Boolean get() = !accessToken.isNullOrBlank() && System.currentTimeMillis() < expiresAtMs
  }

  fun tokenState(context: Context): Flow<TokenState> = context.ebayDataStore.data.map { prefs ->
    TokenState(
      accessToken = prefs[KEY_ACCESS],
      refreshToken = prefs[KEY_REFRESH],
      expiresAtMs = prefs[KEY_EXPIRES_AT] ?: 0L,
    )
  }

  suspend fun signOut(context: Context) {
    context.ebayDataStore.edit { prefs ->
      prefs.remove(KEY_ACCESS)
      prefs.remove(KEY_REFRESH)
      prefs.remove(KEY_EXPIRES_AT)
      prefs.remove(KEY_LAST_CODE_VERIFIER)
      prefs.remove(KEY_LAST_STATE)
    }
  }

  /**
   * Starts OAuth in a browser tab. eBay requires redirect_uri to be the RuName.
   *
   * NOTE: Token exchange for eBay Authorization Code flow typically requires the client secret.
   * For production, do this exchange on a server you control (do NOT ship secret in APK).
   */
  suspend fun launchSignIn(context: Context) {
    val state = randomUrlSafe(24)
    val verifier = randomUrlSafe(64)
    val challenge = sha256UrlSafe(verifier)

    context.ebayDataStore.edit { prefs ->
      prefs[KEY_LAST_CODE_VERIFIER] = verifier
      prefs[KEY_LAST_STATE] = state
    }

    val uri = Uri.parse(authorizeBase()).buildUpon()
      .appendQueryParameter("client_id", BuildConfig.EBAY_CLIENT_ID)
      .appendQueryParameter("redirect_uri", BuildConfig.EBAY_REDIRECT_RU_NAME)
      .appendQueryParameter("response_type", "code")
      .appendQueryParameter("scope", BuildConfig.EBAY_SCOPES)
      .appendQueryParameter("state", state)
      .appendQueryParameter("code_challenge", challenge)
      .appendQueryParameter("code_challenge_method", "S256")
      .build()

    withContext(Dispatchers.Main) {
      CustomTabsIntent.Builder().build().launchUrl(context, uri)
    }
  }

  data class RedirectResult(
    val code: String?,
    val state: String?,
    val error: String?,
    val errorDescription: String?,
  )

  fun parseRedirect(data: Uri?): RedirectResult {
    if (data == null) return RedirectResult(null, null, null, null)
    return RedirectResult(
      code = data.getQueryParameter("code"),
      state = data.getQueryParameter("state"),
      error = data.getQueryParameter("error"),
      errorDescription = data.getQueryParameter("error_description"),
    )
  }

  suspend fun exchangeCodeForToken(context: Context, code: String): Result<Unit> {
    // Pull verifier/state from stored prefs.
    val prefs = context.ebayDataStore.data.first()
    val codeVerifier = prefs[KEY_LAST_CODE_VERIFIER] ?: ""
    if (codeVerifier.isNullOrBlank()) return Result.failure(IllegalStateException("Missing PKCE verifier; start sign-in again."))

    // eBay typically requires Basic auth with client_id:client_secret for token exchange.
    val secret = BuildConfig.EBAY_CLIENT_SECRET
    if (secret.isBlank()) {
      return Result.failure(
        IllegalStateException(
          "EBAY_CLIENT_SECRET is not set. For dev, set Gradle property EBAY_CLIENT_SECRET. " +
            "For production, do token exchange on your server (do not ship the secret in the app).",
        ),
      )
    }

    return withContext(Dispatchers.IO) {
      runCatching {
        val url = URL(tokenUrl())
        val conn = (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 10_000
          readTimeout = 10_000
          doOutput = true
          setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
          setRequestProperty("Accept", "application/json")
          setRequestProperty("User-Agent", "ListLens/0.0.1")
          val basic = Base64.encodeToString(
            (BuildConfig.EBAY_CLIENT_ID + ":" + secret).toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
          )
          setRequestProperty("Authorization", "Basic $basic")
        }

        val body = buildString {
          append("grant_type=authorization_code")
          append("&code=").append(urlEncode(code))
          append("&redirect_uri=").append(urlEncode(BuildConfig.EBAY_REDIRECT_RU_NAME))
          append("&code_verifier=").append(urlEncode(codeVerifier))
        }

        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

        val codeHttp = conn.responseCode
        val stream = if (codeHttp in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (codeHttp !in 200..299) {
          throw IllegalStateException("Token exchange failed: HTTP $codeHttp $text")
        }

        // Minimal JSON parse without extra deps.
        val access = jsonString(text, "access_token")
        val refresh = jsonString(text, "refresh_token")
        val expiresIn = jsonLong(text, "expires_in") ?: 0L
        val expiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn)

        context.ebayDataStore.edit { p ->
          if (!access.isNullOrBlank()) p[KEY_ACCESS] = access
          if (!refresh.isNullOrBlank()) p[KEY_REFRESH] = refresh
          p[KEY_EXPIRES_AT] = expiresAt
        }
      }.map { }
    }
  }

  private fun randomUrlSafe(nBytes: Int): String {
    val bytes = ByteArray(nBytes)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }

  private fun sha256UrlSafe(s: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.US_ASCII))
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }

  private fun urlEncode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

  // Tiny, gross JSON parsing (sandbox/dev). We can swap to kotlinx.serialization later.
  private fun jsonString(json: String, key: String): String? {
    val idx = json.indexOf('"' + key + '"')
    if (idx == -1) return null
    val colon = json.indexOf(':', idx)
    if (colon == -1) return null
    val firstQuote = json.indexOf('"', colon + 1)
    if (firstQuote == -1) return null
    val secondQuote = json.indexOf('"', firstQuote + 1)
    if (secondQuote == -1) return null
    return json.substring(firstQuote + 1, secondQuote)
  }

  private fun jsonLong(json: String, key: String): Long? {
    val idx = json.indexOf('"' + key + '"')
    if (idx == -1) return null
    val colon = json.indexOf(':', idx)
    if (colon == -1) return null
    val start = colon + 1
    val end = json.indexOfAny(charArrayOf(',', '}', '\n', '\r'), startIndex = start).let { if (it == -1) json.length else it }
    return json.substring(start, end).trim().toLongOrNull()
  }
}
