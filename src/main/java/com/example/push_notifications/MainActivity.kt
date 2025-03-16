package com.example.push_notifications

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: NoteViewModel by viewModels()

    // Registra un launcher para solicitar permisos (más moderno que requestPermissions)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("Notifications", "Permiso concedido")
        } else {
            Log.d("Notifications", "Permiso denegado")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar y solicitar permisos
        requestNotificationPermission()
        requestAlarmPermission()

        // Obtener token FCM
        getFCMToken()

        // Configurar la UI con Compose
        setContent {
            MaterialTheme {
                NoteApp(viewModel)
            }
        }

        testNotification()
    }

    private fun testNotification() {
        val notificationReceiver = NotificationReceiver()
        val intent = Intent().apply {
            putExtra("NOTE_ID", "test123")
            putExtra("NOTE_TITLE", "Prueba de Notificación")
            putExtra("NOTE_CONTENT", "Si ves esto, las notificaciones funcionan correctamente")
        }
        notificationReceiver.onReceive(this, intent)
    }

    private fun requestAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Log.e("MainActivity", "No se puede abrir la configuración de alarmas", e)
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun getFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM Token", token ?: "Token is null")
            } else {
                Log.e("FCM Token", "Error getting token", task.exception)
            }
        }
    }
}

// Componentes de UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteApp(viewModel: NoteViewModel = viewModel()) {
    val notes by viewModel.notes.observeAsState(initial = emptyList())
    val showDialog = remember { mutableStateOf(false) }
    val noteToEdit = remember { mutableStateOf<Note?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationScheduler = remember { NotificationScheduler(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notas Seguras") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    noteToEdit.value = null
                    showDialog.value = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Nota")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay notas. Crea una nueva nota con el botón +",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes) { note ->
                        NoteCard(
                            note = note,
                            onEdit = {
                                noteToEdit.value = note
                                showDialog.value = true
                            },
                            onDelete = {
                                scope.launch {
                                    notificationScheduler.cancelNoteReminder(note.id)
                                    viewModel.deleteNote(note.id)
                                }
                            }
                        )
                    }
                }
            }

            if (showDialog.value) {
                NoteDialog(
                    note = noteToEdit.value,
                    onDismiss = { showDialog.value = false },
                    onSave = { title, content, reminderTime ->
                        val note = noteToEdit.value?.copy(
                            title = title,
                            content = content,
                            reminderTime = reminderTime
                        ) ?: Note(
                            title = title,
                            content = content,
                            reminderTime = reminderTime
                        )

                        scope.launch {
                            viewModel.saveNote(note)
                            if (reminderTime != null) {
                                notificationScheduler.scheduleNoteReminder(note)
                            }
                            showDialog.value = false
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCard(note: Note, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(note.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall
                )

                if (note.reminderTime != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Recordatorio",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dateFormat.format(Date(note.reminderTime)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDialog(
    note: Note?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, reminderTime: Long?) -> Unit
) {
    val title = remember { mutableStateOf(note?.title ?: "") }
    val content = remember { mutableStateOf(note?.content ?: "") }
    val setReminder = remember { mutableStateOf(note?.reminderTime != null) }
    val reminderDate = remember { mutableStateOf(Calendar.getInstance().apply {
        note?.reminderTime?.let { timeInMillis = it }
    }) }
    val showDatePicker = remember { mutableStateOf(false) }
    val showTimePicker = remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (note == null) "Nueva Nota" else "Editar Nota") },
        text = {
            Column {
                TextField(
                    value = title.value,
                    onValueChange = { title.value = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = content.value,
                    onValueChange = { content.value = it },
                    label = { Text("Contenido") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = setReminder.value,
                        onCheckedChange = { setReminder.value = it }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text("Establecer recordatorio")
                }

                if (setReminder.value) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(onClick = { showDatePicker.value = true }) {
                            Text(dateFormat.format(reminderDate.value.time))
                        }

                        Button(onClick = { showTimePicker.value = true }) {
                            Text(timeFormat.format(reminderDate.value.time))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val reminderTime = if (setReminder.value) {
                        reminderDate.value.timeInMillis
                    } else null

                    onSave(title.value, content.value, reminderTime)
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )

    // Date Picker Dialog
    if (showDatePicker.value) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = reminderDate.value.timeInMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker.value = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            val newDate = Calendar.getInstance().apply {
                                timeInMillis = selectedMillis
                                set(Calendar.HOUR_OF_DAY, reminderDate.value.get(Calendar.HOUR_OF_DAY))
                                set(Calendar.MINUTE, reminderDate.value.get(Calendar.MINUTE))
                            }
                            reminderDate.value = newDate
                        }
                        showDatePicker.value = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePicker.value = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time Picker Dialog
    if (showTimePicker.value) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderDate.value.get(Calendar.HOUR_OF_DAY),
            initialMinute = reminderDate.value.get(Calendar.MINUTE)
        )

        AlertDialog(
            onDismissRequest = { showTimePicker.value = false },
            title = { Text("Seleccionar hora") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newDate = Calendar.getInstance().apply {
                            timeInMillis = reminderDate.value.timeInMillis
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                        }
                        reminderDate.value = newDate
                        showTimePicker.value = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTimePicker.value = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
