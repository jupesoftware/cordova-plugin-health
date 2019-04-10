package org.apache.cordova.health;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "cordova-plugin-health";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "JUPE BootReceiver.onReceive()");
        HealthJobService.scheduleJob(context);
    }
}