package com.shaibarack.wifitimer.background;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.shaibarack.wifitimer.R;
import com.shaibarack.wifitimer.settings.SettingsActivity;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Manages the timer notification and timeout functionality.
 */
public class NotificationService extends Service {

    private static final String ENABLE_ACTION = "enable";
    private static final String DISMISS_ACTION = "dismiss";
    private static final String SNOOZE_ACTION = "snooze";
    private static final int NOTIFICATION_ID = 1;

    private NotificationManager mNotificationManager;
    private AlarmManager mAlarmManager;
    private WifiManager mWifiManager;
    private SharedPreferences mPrefs;
    private Calendar mCalendar;

    /** Fired when enabling wifi by user interaction or alarm. */
    private PendingIntent mEnablePending;
    /** Template for notification. Update title when setting and when snoozing. */
    private Notification.Builder mNotification;
    /** Time to trigger alarm in {@link System#currentTimeMillis()}. */
    private long mTriggerAtMillis;
    /** Registered on wifi enabling/enabled to auto-dismiss notification. */
    private EnabledReceiver mReceiver;

    @Override
    public void onCreate() {
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mReceiver = new EnabledReceiver();
        mCalendar = new GregorianCalendar();

        Intent settingsIntent = new Intent(Intent.ACTION_MAIN, null, this, SettingsActivity.class);
        Intent enableIntent = new Intent(ENABLE_ACTION, null, this, NotificationService.class);
        Intent dismissIntent = new Intent(DISMISS_ACTION, null, this, NotificationService.class);
        mEnablePending = PendingIntent.getService(this, 0, enableIntent, 0);

        mNotification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.disabled)
                // Tapping the notification goes to settings
                .setContentIntent(PendingIntent.getActivity(this, 0, settingsIntent, 0))
                // Tapping the notification doesn't dismiss it
                .setAutoCancel(false)
                // Listen on dismissal
                .setDeleteIntent(PendingIntent.getService(this, 0, dismissIntent, 0))
                .setPriority(Notification.PRIORITY_LOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            mNotification.setLocalOnly(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mNotification.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        if (mPrefs.getBoolean("show_enable_now", false)) {
            mNotification.addAction(
                    R.drawable.enabled, getString(R.string.enable_now), mEnablePending);
        }

        if (mPrefs.getBoolean("show_snooze", false)) {
            Intent snoozeIntent = new Intent(SNOOZE_ACTION, null, this, NotificationService.class);
            mNotification.addAction(R.drawable.snooze, getString(R.string.snooze),
                    PendingIntent.getService(this, 0, snoozeIntent, 0));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                if (mPrefs.getBoolean("enabled", true)) {
                    showNotification();
                }
                break;

            case DISMISS_ACTION:
                mNotificationManager.cancel(NOTIFICATION_ID);
                dismiss();
                break;

            case ENABLE_ACTION:
                mNotificationManager.cancel(NOTIFICATION_ID);
                enable();
                break;

            case SNOOZE_ACTION:
                snooze();
                break;

            default:
                throw new IllegalArgumentException("Unexpected start intent " + intent);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
    }

    /** Show initial notification after wifi is disabled. */
    private void showNotification() {
        int enableSec = mPrefs.getInt("sec_until_enable", 60);
        mTriggerAtMillis = System.currentTimeMillis() + enableSec * 1000;
        mNotification.setContentTitle(
                // "Enabling wifi at hh:mm:ss"
                getString(R.string.notification_title) + " " + toWallTime(mTriggerAtMillis));

        // Show notification
        Notification notification = mNotification.build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);

        // Set timeout alarm
        setAlarm(mTriggerAtMillis);

        // Dismiss when user enables wifi manually
        registerReceiver(mReceiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
    }

    /** Enable wifi now. */
    private void enable() {
        cancelLastAlarm();
        mWifiManager.setWifiEnabled(true);
        stopSelf();
    }

    /** Dismiss alarm. */
    private void dismiss() {
        cancelLastAlarm();
        stopSelf();
    }

    /** Snooze alarm. */
    private void snooze() {
        cancelLastAlarm();
        int snoozeSec = mPrefs.getInt("snooze_sec", 60);
        mTriggerAtMillis += snoozeSec * 1000;
        setAlarm(mTriggerAtMillis);
        // Update notification
        Notification notification = mNotification.setContentTitle(
                getString(R.string.notification_title) + " " + toWallTime(mTriggerAtMillis))
                .build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void cancelLastAlarm() {
        mAlarmManager.cancel(mEnablePending);
    }

    private void setAlarm(long triggerAtMillis) {
        cancelLastAlarm();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, mEnablePending);
        } else {
            mAlarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, mEnablePending);
        }
    }

    /** Returns a timestamp obtained via {@link System#currentTimeMillis()} as hh:mm:ss. */
    private String toWallTime(long timeMillis) {
        mCalendar.setTimeInMillis(timeMillis);
        return String.format("%02d:%02d:%02d",
                mCalendar.get(Calendar.HOUR),
                mCalendar.get(Calendar.MINUTE),
                mCalendar.get(Calendar.SECOND));
    }

    /** For auto-dismissing when wifi is enabled by the user. */
    private class EnabledReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Ignore everything except wifi enabling or enabled
            if (!WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                return;
            }

            switch (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)) {
                case WifiManager.WIFI_STATE_ENABLING:
                case WifiManager.WIFI_STATE_ENABLED:
                    mNotificationManager.cancel(NOTIFICATION_ID);
                    dismiss();

                default:
                    break;
            }
        }

    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
