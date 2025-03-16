package com.example.push_notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Maneja los mensajes recibidos aquí
        if (remoteMessage.notification != null) {
            val title = remoteMessage.notification?.title
            val body = remoteMessage.notification?.body
            showNotification(title, body)
        }

        // Maneja datos adicionales (como el ID de una nota)
        remoteMessage.data.let { data ->
            val noteId = data["noteId"]
            if (noteId != null) {
                // Aquí podrías abrir una nota específica
            }
        }
    }

    private fun showNotification(title: String?, body: String?) {
        showLocalNotification(this, 0, title, body)
    }

    fun showLocalNotification(context: Context, notificationId: Int, title: String?, body: String?) {
        val channelId = "push_notification_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear un intent para abrir la app cuando se toca la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NOTIFICATION_ID", notificationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Crear el canal de notificación (requerido para Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Push Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Construir la notificación
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        // Mostrar la notificación
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        // Maneja la actualización del token aquí
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        // Envía el token a tu servidor si es necesario
    }
}
