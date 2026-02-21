package com.listlens.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "listlens")

object Prefs {
  private fun conditionKey(isbn13: String) = stringPreferencesKey("cond_$isbn13")
  private fun notesKey(isbn13: String) = stringPreferencesKey("notes_$isbn13")

  private val recentIsbnsKey = stringPreferencesKey("recent_isbns")

  fun conditionFlow(context: Context, isbn13: String): Flow<String?> =
    context.dataStore.data.map { it[conditionKey(isbn13)] }

  fun notesFlow(context: Context, isbn13: String): Flow<String?> =
    context.dataStore.data.map { it[notesKey(isbn13)] }

  suspend fun setCondition(context: Context, isbn13: String, condition: String) {
    context.dataStore.edit { prefs ->
      prefs[conditionKey(isbn13)] = condition
    }
  }

  suspend fun setNotes(context: Context, isbn13: String, notes: String) {
    context.dataStore.edit { prefs ->
      prefs[notesKey(isbn13)] = notes
    }
  }

  fun recentIsbnsFlow(context: Context, limit: Int = 10): Flow<List<String>> =
    context.dataStore.data.map { prefs ->
      val raw = prefs[recentIsbnsKey].orEmpty()
      raw.split("|")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(limit)
    }

  suspend fun addRecentIsbn(context: Context, isbn13: String, limit: Int = 20) {
    val normalized = Isbn.extractIsbn13(isbn13) ?: return
    context.dataStore.edit { prefs ->
      val existing = prefs[recentIsbnsKey].orEmpty()
        .split("|")
        .map { it.trim() }
        .filter { it.isNotBlank() }

      val next = buildList {
        add(normalized)
        existing.forEach { if (it != normalized) add(it) }
      }.distinct().take(limit)

      prefs[recentIsbnsKey] = next.joinToString("|")
    }
  }
}
