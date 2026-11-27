package com.dexterous.flutterlocalnotifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.PowerManager;
import androidx.test.core.app.ApplicationProvider;
import com.dexterous.flutterlocalnotifications.models.NotificationDetails;
import com.dexterous.flutterlocalnotifications.models.NotificationStyle;
import com.dexterous.flutterlocalnotifications.models.styles.DefaultStyleInformation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ScheduledFullScreenAlarmTest {
  public static class LauncherActivity extends Activity {}

  public static class AlarmActivity extends Activity {}

  private Application application;
  private final NotificationDetails alarm = new NotificationDetails();

  @Before
  public void setUp() {
    application = ApplicationProvider.getApplicationContext();
    ShadowPackageManager packages = shadowOf(application.getPackageManager());
    ComponentName launcher = new ComponentName(application, LauncherActivity.class);
    packages.addActivityIfNotPresent(launcher);
    IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
    filter.addCategory(Intent.CATEGORY_LAUNCHER);
    packages.addIntentFilterForActivity(launcher, filter);
    packages.addActivityIfNotPresent(new ComponentName(application, AlarmActivity.class));

    alarm.id = 17;
    alarm.payload = "scheduled activity";
    alarm.channelName = "Alarms";
    alarm.playSound = false;
    alarm.importance = NotificationManager.IMPORTANCE_HIGH;
    alarm.priority = Notification.PRIORITY_HIGH;
    alarm.iconResourceId = android.R.drawable.ic_lock_idle_alarm;
    alarm.style = NotificationStyle.Default;
    alarm.styleInformation = new DefaultStyleInformation(false, false);
    alarm.scheduledDateTime = "2026-05-01T09:00:00";
    alarm.fullScreenIntent = true;
    alarm.startActivityClassName = AlarmActivity.class.getName();
  }

  @Test
  public void screenOffWithoutKeyguard_deliversOnlyTheDedicatedFullScreenIntent() {
    optInAlarmActivity();
    setDeviceState(false, false);
    Notification posted = fireAlarm();
    assertNotNull(posted);

    assertNotificationIntent(posted.fullScreenIntent, AlarmActivity.class);
    assertNotificationIntent(posted.contentIntent, LauncherActivity.class);
    assertNull(shadowOf(application).getNextStartedActivity());
  }

  @Test
  public void keyguardShowingAtNonzeroSeconds_keepsTheLockedAlarmLaunches() {
    optInAlarmActivity();
    setDeviceState(true, true);
    alarm.scheduledDateTime = "2026-05-01T09:00:15";
    Notification posted = fireAlarm();
    assertNotNull(posted);

    assertNotificationIntent(posted.fullScreenIntent, AlarmActivity.class);
    assertNotificationIntent(posted.contentIntent, AlarmActivity.class);
    assertDirectLaunch(AlarmActivity.class);
  }

  @Test
  public void deviceInUse_keepsTheDirectAlarmLaunch() {
    optInAlarmActivity();
    setDeviceState(true, false);
    fireAlarm();

    assertDirectLaunch(LauncherActivity.class);
  }

  @Test
  public void screenOffWithoutPostedNotification_keepsTheDirectLaunch() {
    optInAlarmActivity();
    setDeviceState(false, false);
    alarm.showNotification = false;
    assertNull(fireAlarm());

    assertDirectLaunch(LauncherActivity.class);
  }

  @Test
  public void withoutOptIn_keepsTheExistingLaunchTargetsAndDirectLaunch() {
    setDeviceState(false, false);
    Notification posted = fireAlarm();
    assertNotNull(posted);

    assertNotificationIntent(posted.contentIntent, LauncherActivity.class);
    assertEquals(posted.contentIntent, posted.fullScreenIntent);
    assertDirectLaunch(LauncherActivity.class);
  }

  private void optInAlarmActivity() {
    ShadowPackageManager packages = shadowOf(application.getPackageManager());
    ActivityInfo activity =
        packages.addActivityIfNotPresent(new ComponentName(application, AlarmActivity.class));
    activity.metaData = new Bundle();
    activity.metaData.putBoolean(
        "com.dexterous.flutterlocalnotifications.DEDICATED_FULL_SCREEN_ACTIVITY", true);
    packages.addOrUpdateActivity(activity);
  }

  private void setDeviceState(boolean interactive, boolean keyguardShowing) {
    shadowOf(application.getSystemService(PowerManager.class)).setIsInteractive(interactive);
    shadowOf(application.getSystemService(KeyguardManager.class))
        .setKeyguardLocked(keyguardShowing);
  }

  private Notification fireAlarm() {
    Intent delivery = new Intent();
    delivery.putExtra(
        FlutterLocalNotificationsPlugin.NOTIFICATION_DETAILS,
        FlutterLocalNotificationsPlugin.buildGson().toJson(alarm));
    new ScheduledNotificationReceiver().onReceive(application, delivery);
    return shadowOf(application.getSystemService(NotificationManager.class))
        .getNotification(alarm.id);
  }

  private void assertDirectLaunch(Class<?> activity) {
    Intent direct = shadowOf(application).getNextStartedActivity();
    assertNotNull(direct);
    assertEquals(new ComponentName(application, activity), direct.getComponent());
    assertEquals(Intent.FLAG_FROM_BACKGROUND, direct.getFlags() & Intent.FLAG_FROM_BACKGROUND);
    assertEquals("SELECT_NOTIFICATION", direct.getAction());
    assertEquals(alarm.payload, direct.getStringExtra(FlutterLocalNotificationsPlugin.PAYLOAD));
    assertNull(shadowOf(application).getNextStartedActivity());
  }

  private void assertNotificationIntent(PendingIntent pendingIntent, Class<?> activity) {
    Intent intent = shadowOf(pendingIntent).getSavedIntent();
    assertEquals(new ComponentName(application, activity), intent.getComponent());
    assertEquals("SELECT_NOTIFICATION", intent.getAction());
    assertEquals(alarm.payload, intent.getStringExtra(FlutterLocalNotificationsPlugin.PAYLOAD));
    assertEquals(
        alarm.id.intValue(),
        intent.getIntExtra(FlutterLocalNotificationsPlugin.NOTIFICATION_ID, -1));
  }
}
