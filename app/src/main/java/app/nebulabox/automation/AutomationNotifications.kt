package app.nebulabox.automation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.nebulabox.Application
import app.nebulabox.MainActivity
import app.nebulabox.R

object AutomationNotifications {
    fun show(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        openUrl: String? = null,
    ) {
        val contentIntent = if (openUrl.isNullOrBlank()) {
            PendingIntent.getActivity(
                context,
                notificationId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else {
            PendingIntent.getActivity(
                context,
                notificationId,
                Intent(Intent.ACTION_VIEW, Uri.parse(openUrl)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, Application.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
