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
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

import com.shaibarack.wifitimer.R;
import com.shaibarack.wifitimer.settings.SettingsActivity;

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

    /** Fired when enabling wifi by user interaction or alarm. */
    private PendingIntent mEnablePending;
    /** Shows countdown text inside notification. */
    private RemoteViews mRemoteViews;
    /** Timer for notification countdown. */
    private CountDownTimer mCountDown;
    /** Notification to show when pending auto-enabling. */
    private Notification mNotification;
    /** Time to trigger alarm in {@link System#currentTimeMillis()}. */
    private long mTriggerAtElapsedMillis;
    /** Registered on wifi enabling/enabled to auto-dismiss notification. */
    private EnabledReceiver mReceiver;

    @Override
    public void onCreate() {
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mReceiver = new EnabledReceiver();

        Intent settingsIntent = new Intent(Intent.ACTION_MAIN, null, this, SettingsActivity.class);
        Intent enableIntent = new Intent(ENABLE_ACTION, null, this, NotificationService.class);
        Intent dismissIntent = new Intent(DISMISS_ACTION, null, this, NotificationService.class);
        mEnablePending = PendingIntent.getService(this, 0, enableIntent, 0);

        mRemoteViews = new RemoteViews(getPackageName(), R.layout.notification_view);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.disabled_notification)
                .setContentTitle(getString(R.string.notification_title))
                // Tapping the notification goes to settings
                .setContentIntent(PendingIntent.getActivity(this, 0, settingsIntent, 0))
                // Tapping the notification doesn't dismiss it
                .setAutoCancel(false)
                // Listen on dismissal
                .setDeleteIntent(PendingIntent.getService(this, 0, dismissIntent, 0))
                .setPriority(Notification.PRIORITY_LOW)
                .setContent(mRemoteViews);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            builder.setLocalOnly(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        if (mPrefs.getBoolean("show_enable_now", false)) {
            builder.addAction(R.drawable.enabled_notification, getString(R.string.enable_now), mEnablePending);
        }

        if (mPrefs.getBoolean("show_snooze", false)) {
            Intent snoozeIntent = new Intent(SNOOZE_ACTION, null, this, NotificationService.class);
            builder.addAction(R.drawable.snooze_notification, getString(R.string.snooze),
                    PendingIntent.getService(this, 0, snoozeIntent, 0));
        }

        mNotification = builder.build();
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
        mTriggerAtElapsedMillis = SystemClock.elapsedRealtime() + enableSec * 1000;

        mNotificationManager.notify(NOTIFICATION_ID, mNotification);

        setAlarm(mTriggerAtElapsedMillis);
        setCountDown(mTriggerAtElapsedMillis);

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
        mTriggerAtElapsedMillis += snoozeSec * 1000;
        setAlarm(mTriggerAtElapsedMillis);
        setCountDown(mTriggerAtElapsedMillis);
    }

    private void cancelLastAlarm() {
        mAlarmManager.cancel(mEnablePending);
    }

    private void setAlarm(long elapsedRealtimeMillis) {
        cancelLastAlarm();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mAlarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedRealtimeMillis, mEnablePending);
        } else {
            mAlarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedRealtimeMillis, mEnablePending);
        }
    }

    private void setCountDown(long mTriggerAtElapsedMillis) {
        if (mCountDown != null) {
            mCountDown.cancel();
        }

        long millisInFuture = mTriggerAtElapsedMillis - SystemClock.elapsedRealtime();
        mCountDown = new MyCountDownTimer(millisInFuture, 1000);
        mCountDown.start();
    }

    /** For counting down in the notification. */
    private class MyCountDownTimer extends CountDownTimer {

        public MyCountDownTimer(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            int seconds = (int) (millisUntilFinished / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            mRemoteViews.setTextViewText(R.id.countdown,
                    String.format("%02d:%02d", minutes, seconds));
        }

        @Override
        public void onFinish() {
        }
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
