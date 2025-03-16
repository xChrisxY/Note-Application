package com.example.push_notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra("NOTE_ID") ?: return
        val noteTitle = intent.getStringExtra("NOTE_TITLE") ?: "Recordatorio"
        val noteContent = intent.getStringExtra("NOTE_CONTENT") ?: "Es hora de revisar tu nota"

        // Método directo para mostrar la notificación sin depender del servicio de Firebase
        showNotification(context, noteId.hashCode(), noteTitle, noteContent)
    }

    private fun showNotification(context: Context, notificationId: Int, title: String, content: String) {
        Log.d("Info","Mostrando notificación...")
        val channelId = "note_reminder_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent para abrir la app cuando se toca la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NOTIFICATION_ID", notificationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Crear canal de notificación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Recordatorios de Notas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones para recordatorios de notas"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(channel)
        }

        // Construir la notificación
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        // Mostrar la notificación
        notificationManager.notify(notificationId, notificationBuilder.build())

        // Para depuración
        Log.d("NotificationReceiver", "Notificación mostrada: $title")
    }
}