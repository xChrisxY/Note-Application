package com.example.push_notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val secureNoteManager = SecureNoteManager(application)

    private val _notes = MutableLiveData<List<Note>>()
    val notes: LiveData<List<Note>> = _notes

    init {
        loadNotes()
    }

    fun loadNotes() {
        viewModelScope.launch {
            _notes.value = secureNoteManager.getAllNotes()
        }
    }

    fun saveNote(note: Note) {
        viewModelScope.launch {
            secureNoteManager.saveNote(note)
            loadNotes()
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            secureNoteManager.deleteNote(noteId)
            loadNotes()
        }
    }

    fun getNoteById(noteId: String): Note? {
        var note: Note? = null
        viewModelScope.launch {
            note = secureNoteManager.getNoteById(noteId)
        }
        return note
    }
}
