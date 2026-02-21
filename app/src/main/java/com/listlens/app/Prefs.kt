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
}
