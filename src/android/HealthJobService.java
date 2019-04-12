package org.apache.cordova.health;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class HealthJobService extends JobService {

    private static final String TAG = "cordova-plugin-health";

    public static void scheduleJob(Context context) {
        Log.i(TAG, "JUPE HealthJobService.scheduleJob()");
        long flexMillis = 59 * 60 * 1000; // wait 59 minutes before executing next job

        ComponentName serviceComponent = new ComponentName(context, HealthJobService.class);
        JobInfo info = new JobInfo.Builder(1, serviceComponent)
                //.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED) // change this later to wifi
                .setPersisted(true)
                .setPeriodic(15 * 60 * 1000) // , flexMillis
                .build();

        JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
        jobScheduler.schedule(info);

        SharedPreferences sharedPref = context.getSharedPreferences("cordova-plugin-health", Context.MODE_PRIVATE);
        String url = sharedPref.getString("url", null);
        String authorization = sharedPref.getString("authorization", null);

        Log.i(TAG, "JUPE HealthJobService.scheduleJob() url=" + url + " authorization=" + authorization);
        Log.i(TAG, "JUPE HealthJobService.scheduleJob() done");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "JUPE HealthJobService onStartJob");
        // Intent service = new Intent(getApplicationContext(), LocalWordService.class);
        // getApplicationContext().startService(service);
        // Util.scheduleJob(getApplicationContext()); // reschedule the job
        jobFinished(params, false); // No needsReschedule with a periodic job
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "JUPE HealthJobService onStopJob");
        return false;
    }
}