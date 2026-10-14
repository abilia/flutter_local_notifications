package com.dexterous.flutterlocalnotifications;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import androidx.core.app.NotificationCompat;
import androidx.test.core.app.ApplicationProvider;
import com.dexterous.flutterlocalnotifications.models.NotificationDetails;
import com.dexterous.flutterlocalnotifications.models.NotificationStyle;
import com.dexterous.flutterlocalnotifications.models.styles.DefaultStyleInformation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(
    manifest = Config.NONE,
    sdk = {24, 25})
public class NotificationVibrationTest {
  private static final String CHANNEL_ID = "vibration";
  private static final long[] CUSTOM_PATTERN = {0, 500, 250, 500};

  private Context context;

  @Before
  public void before() {
    context = ApplicationProvider.getApplicationContext();
    ResolveInfo launcherActivity = new ResolveInfo();
    launcherActivity.activityInfo = new ActivityInfo();
    launcherActivity.activityInfo.packageName = context.getPackageName();
    launcherActivity.activityInfo.name = "TestActivity";
    Intent launchIntent =
        new Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.getPackageName());
    shadowOf(context.getPackageManager()).addResolveInfoForIntent(launchIntent, launcherActivity);
  }

  @Test
  public void enabledWithoutPattern_usesDefaultVibrationWithoutSound() {
    Notification notification = createNotification(true, null);

    assertEquals(Notification.DEFAULT_VIBRATE, notification.defaults);
    assertNull(notification.vibrate);
    assertNull(notification.sound);
  }

  @Test
  public void enabledWithEmptyPattern_usesDefaultVibrationWithoutSound() {
    Notification notification = createNotification(true, new long[0]);

    assertEquals(Notification.DEFAULT_VIBRATE, notification.defaults);
    assertNull(notification.vibrate);
    assertNull(notification.sound);
  }

  @Test
  public void enabledWithCustomPattern_preservesPatternWithoutDefaults() {
    Notification notification = createNotification(true, CUSTOM_PATTERN);

    assertEquals(0, notification.defaults);
    assertArrayEquals(CUSTOM_PATTERN, notification.vibrate);
    assertNull(notification.sound);
  }

  @Test
  public void disabledWithoutPattern_doesNotEnableDefaultVibration() {
    Notification notification = createNotification(false, null);

    assertEquals(0, notification.defaults);
    assertArrayEquals(new long[] {0}, notification.vibrate);
    assertNull(notification.sound);
  }

  @Test
  public void disabledWithCustomPattern_doesNotVibrate() {
    Notification notification = createNotification(false, CUSTOM_PATTERN);

    assertEquals(0, notification.defaults);
    assertArrayEquals(new long[] {0}, notification.vibrate);
    assertNull(notification.sound);
  }

  @Test
  @Config(sdk = 26)
  public void enabledWithoutPattern_onAndroidO_usesChannelVibration() {
    Notification notification = createNotification(true, null);

    assertEquals(0, notification.defaults);
    assertNull(notification.vibrate);
    NotificationChannel channel = getNotificationChannel();
    assertTrue(channel.shouldVibrate());
    assertNull(channel.getVibrationPattern());
    assertNull(channel.getSound());
  }

  @Test
  @Config(sdk = 26)
  public void enabledWithEmptyPattern_onAndroidO_usesChannelVibration() {
    Notification notification = createNotification(true, new long[0]);

    assertEquals(0, notification.defaults);
    assertNull(notification.vibrate);
    NotificationChannel channel = getNotificationChannel();
    assertTrue(channel.shouldVibrate());
    assertNull(channel.getVibrationPattern());
    assertNull(channel.getSound());
  }

  @Test
  @Config(sdk = 26)
  public void enabledWithCustomPattern_onAndroidO_preservesChannelPattern() {
    Notification notification = createNotification(true, CUSTOM_PATTERN);

    assertEquals(0, notification.defaults);
    NotificationChannel channel = getNotificationChannel();
    assertTrue(channel.shouldVibrate());
    assertArrayEquals(CUSTOM_PATTERN, channel.getVibrationPattern());
    assertNull(channel.getSound());
  }

  @Test
  @Config(sdk = 26)
  public void disabledWithoutPattern_onAndroidO_disablesChannelVibration() {
    Notification notification = createNotification(false, null);

    assertEquals(0, notification.defaults);
    NotificationChannel channel = getNotificationChannel();
    assertFalse(channel.shouldVibrate());
    assertNull(channel.getVibrationPattern());
    assertNull(channel.getSound());
  }

  private Notification createNotification(boolean enableVibration, long[] vibrationPattern) {
    NotificationDetails details = new NotificationDetails();
    details.id = 1;
    details.title = "Vibration-only alarm";
    details.channelId = CHANNEL_ID;
    details.channelName = "Vibration";
    details.importance = NotificationManager.IMPORTANCE_HIGH;
    details.priority = NotificationCompat.PRIORITY_HIGH;
    details.iconResourceId = android.R.drawable.ic_dialog_info;
    details.style = NotificationStyle.Default;
    details.styleInformation = new DefaultStyleInformation(false, false);
    details.playSound = false;
    details.enableVibration = enableVibration;
    details.vibrationPattern = vibrationPattern;
    return FlutterLocalNotificationsPlugin.createNotification(context, details);
  }

  private NotificationChannel getNotificationChannel() {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    return notificationManager.getNotificationChannel(CHANNEL_ID);
  }
}
