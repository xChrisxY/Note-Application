package com.example.push_notifications

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reminderTime: Long? = null
)

class SecureNoteManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_notes",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun saveNote(note: Note) = withContext(Dispatchers.IO) {
        val notesJson = sharedPreferences.getString("notes", "[]")
        val notesArray = JSONArray(notesJson)

        // Verificar si la nota ya existe
        var existingIndex = -1
        for (i in 0 until notesArray.length()) {
            val noteObject = notesArray.getJSONObject(i)
            if (noteObject.getString("id") == note.id) {
                existingIndex = i
                break
            }
        }

        val noteJson = JSONObject().apply {
            put("id", note.id)
            put("title", note.title)
            put("content", note.content)
            put("timestamp", note.timestamp)
            put("reminderTime", note.reminderTime ?: JSONObject.NULL)
        }

        if (existingIndex != -1) {
            notesArray.put(existingIndex, noteJson)
        } else {
            notesArray.put(noteJson)
        }

        sharedPreferences.edit()
            .putString("notes", notesArray.toString())
            .apply()
    }

    suspend fun getAllNotes(): List<Note> = withContext(Dispatchers.IO) {
        val notesJson = sharedPreferences.getString("notes", "[]")
        val notesArray = JSONArray(notesJson)
        val notes = mutableListOf<Note>()

        for (i in 0 until notesArray.length()) {
            val noteObject = notesArray.getJSONObject(i)
            val reminderTime = if (noteObject.has("reminderTime") && !noteObject.isNull("reminderTime")) {
                noteObject.getLong("reminderTime")
            } else null

            notes.add(
                Note(
                    id = noteObject.getString("id"),
                    title = noteObject.getString("title"),
                    content = noteObject.getString("content"),
                    timestamp = noteObject.getLong("timestamp"),
                    reminderTime = reminderTime
                )
            )
        }

        notes.sortByDescending { it.timestamp }
        notes
    }

    suspend fun deleteNote(noteId: String) = withContext(Dispatchers.IO) {
        val notesJson = sharedPreferences.getString("notes", "[]")
        val notesArray = JSONArray(notesJson)
        val updatedArray = JSONArray()

        for (i in 0 until notesArray.length()) {
            val noteObject = notesArray.getJSONObject(i)
            if (noteObject.getString("id") != noteId) {
                updatedArray.put(noteObject)
            }
        }

        sharedPreferences.edit()
            .putString("notes", updatedArray.toString())
            .apply()
    }

    suspend fun getNoteById(noteId: String): Note? = withContext(Dispatchers.IO) {
        getAllNotes().find { it.id == noteId }
    }
}
