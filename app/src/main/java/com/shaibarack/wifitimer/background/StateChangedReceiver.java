package com.shaibarack.wifitimer.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;

/**
 * Receives state change broadcasts.
 */
public class StateChangedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        // Filter irrelevant intents
        if (!WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
            throw new IllegalArgumentException("Unexpected start intent " + intent);
        }

        if (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)
                != WifiManager.WIFI_STATE_DISABLED) {
            return;
        }
        if (!PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("enabled", true)) {
            return;
        }

        // Forward to NotificationService
        context.startService(new Intent(
                WifiManager.WIFI_STATE_CHANGED_ACTION, null, context, NotificationService.class));
    }
}
