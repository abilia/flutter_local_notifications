package com.dexterous.flutterlocalnotifications;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.core.app.NotificationManagerCompat;

import com.dexterous.flutterlocalnotifications.models.NotificationDetails;
import com.dexterous.flutterlocalnotifications.utils.StringUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.time.LocalDateTime;

/** Created by michaelbui on 24/3/18. */
@Keep
public class ScheduledNotificationReceiver extends BroadcastReceiver {

  private static final String TAG = "ScheduledNotifReceiver";

  @Override
  @SuppressWarnings("deprecation")
  public void onReceive(final Context context, Intent intent) {
    String notificationDetailsJson =
        intent.getStringExtra(FlutterLocalNotificationsPlugin.NOTIFICATION_DETAILS);
    if (StringUtils.isNullOrEmpty(notificationDetailsJson)) {
      // This logic is needed for apps that used the plugin prior to 0.3.4

      Notification notification;
      int notificationId = intent.getIntExtra("notification_id", 0);

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        notification = intent.getParcelableExtra("notification", Notification.class);
      } else {
        notification = intent.getParcelableExtra("notification");
      }

      if (notification == null) {
        // This means the notification is corrupt
        FlutterLocalNotificationsPlugin.removeNotificationFromCache(context, notificationId);
        Log.e(TAG, "Failed to parse a notification from  Intent. ID: " + notificationId);
        return;
      }

      notification.when = System.currentTimeMillis();
      NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
      notificationManager.notify(notificationId, notification);
      boolean repeat = intent.getBooleanExtra("repeat", false);
      if (!repeat) {
        FlutterLocalNotificationsPlugin.removeNotificationFromCache(context, notificationId);
      }
    } else {
      Gson gson = FlutterLocalNotificationsPlugin.buildGson();
      Type type = new TypeToken<NotificationDetails>() {}.getType();
      NotificationDetails notificationDetails = gson.fromJson(notificationDetailsJson, type);
      final boolean locked = FlutterLocalNotificationsPlugin.isKeyguardLocked(context);
      // Decide before notify(): SystemUI may wake the device while delivering the full-screen
      // intent.
      boolean startDirectly = shouldStartDirectly(context, notificationDetails, locked);
      if (notificationDetails.showNotification == null
          || notificationDetails.showNotification
          || locked) {
        FlutterLocalNotificationsPlugin.showNotification(context, notificationDetails);
      }
      FlutterLocalNotificationsPlugin.scheduleNextNotification(context, notificationDetails);

      if (startDirectly) {
        FlutterLocalNotificationsPlugin.startAlarmActivity(context, notificationDetails);
      }
    }
  }

  private static boolean shouldStartDirectly(
      Context context, NotificationDetails details, boolean locked) {
    if (details.startActivityClassName == null) return false;
    if (locked) {
      return LocalDateTime.parse(details.scheduledDateTime).getSecond() != 0;
    }
    if (Boolean.FALSE.equals(details.showNotification)
        || !FlutterLocalNotificationsPlugin.usesDedicatedFullScreenActivity(context, details)) {
      return true;
    }
    PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    return power.isInteractive();
  }
}
