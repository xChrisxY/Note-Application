package com.example.push_notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.google.type.Date

class NotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun scheduleNoteReminder(note: Note) = withContext(Dispatchers.IO) {
        note.reminderTime?.let { reminderTime ->
            if (reminderTime <= System.currentTimeMillis()) {
                Log.w("NotificationScheduler", "Hora programada en el pasado: ${java.util.Date(reminderTime)}")
                // Opcional: Mostrar la notificación inmediatamente si la hora ya pasó
                withContext(Dispatchers.Main) {
                    val receiver = NotificationReceiver()
                    val intent = Intent().apply {
                        putExtra("NOTE_ID", note.id)
                        putExtra("NOTE_TITLE", note.title)
                        putExtra("NOTE_CONTENT", note.content)
                    }
                    receiver.onReceive(context, intent)
                }
                return@withContext
            }

            // Verificar permiso para establecer alarmas exactas
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
                Log.e("NotificationScheduler", "No se puede programar alarma exacta. Falta permiso.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Se requiere permiso para programar recordatorios", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Log.e("NotificationScheduler", "No se puede abrir la configuración de alarmas", e)
                    }
                }
                return@withContext
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("NOTE_ID", note.id)
                putExtra("NOTE_TITLE", note.title)
                putExtra("NOTE_CONTENT", note.content)
            }

            Log.d("NotificationScheduler", "Programando recordatorio para: ${java.util.Date(reminderTime)}")

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                note.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
                )
                Log.d("NotificationScheduler", "Alarma programada con setExactAndAllowWhileIdle")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Recordatorio programado", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("NotificationScheduler", "Error de permisos al programar recordatorio", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al programar recordatorio: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    suspend fun cancelNoteReminder(noteId: String) = withContext(Dispatchers.IO) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.d("NotificationScheduler", "Recordatorio cancelado para nota: $noteId")
    }
}