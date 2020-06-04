package co.wangun.notifcovid.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import co.wangun.notifcovid.R

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        createNotificationChannel(context)
        Log.d("BBB", "Received!")

//        when (intent.action) {
//            Intent.ACTION_BOOT_COMPLETED -> {
//                alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
//                alarmIntent = Intent(context, BootReceiver::class.java).let {
//                    PendingIntent.getBroadcast(context, 0, it, 0)
//                }
//
//                // Set the alarm to start at approximately 6:00 a.m.
//                val calendar: Calendar = Calendar.getInstance().apply {
//                    timeInMillis = System.currentTimeMillis()
//                    set(Calendar.HOUR_OF_DAY, 6)
//                }
//
//                // With setInexactRepeating(), you have to use one of the AlarmManager interval
//                // constants--in this case, AlarmManager.INTERVAL_DAY.
//                alarmMgr.setInexactRepeating(
//                    AlarmManager.RTC_WAKEUP,
////                calendar.timeInMillis,
////                AlarmManager.INTERVAL_DAY,
//                    System.currentTimeMillis(),
//                    60000,
//                    alarmIntent
//                )
//
//                createNotificationChannel(context)
//                createNotificationBoot(context)
//                Toast.makeText(context,"Boot completed",
//                    Toast.LENGTH_SHORT).show()
//            }
//        }
    }

    private fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "NC"
            val descriptionText = "NCD"
            val id = "NCC"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(id, name, importance).apply {
                    description = descriptionText
                }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        // create notification
        createNotification(context)
    }

    private fun createNotification(context: Context) {
        Log.d("BBB", "Notif!")
        val builder = NotificationCompat.Builder(context, "NCC")
            .setSmallIcon(R.drawable.ic_info_outline)
            .setContentTitle("NOTIF COVID")
            .setContentText("This notification will be shown every 1 minute")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(0, builder.build())
        }
    }
}