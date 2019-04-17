package org.apache.cordova.health;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HealthJobService extends JobService {

    private static final String TAG = "cordova-plugin-health";

    // actual Google API client
    private GoogleApiClient mClient;

    public static void scheduleJob(Context context) {
        Log.i(TAG, "JUPE HealthJobService.scheduleJob()");
        long flexMillis = 59 * 60 * 1000; // wait 59 minutes before executing next job

        ComponentName serviceComponent = new ComponentName(context, HealthJobService.class);
        JobInfo info = new JobInfo.Builder(1, serviceComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
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

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                lightConnect(HealthJobService.this);
                steps();
                jobFinished(params, false); // No needsReschedule with a periodic job
            }
        });

        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "JUPE HealthJobService onStopJob");
        return false;
    }

    // helper function, connects to fitness APIs assuming that authorisation was granted
    private boolean lightConnect(Context context) {

        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(context);
        builder.addApi(Fitness.HISTORY_API);
        builder.addApi(Fitness.CONFIG_API);
        builder.addApi(Fitness.SESSIONS_API);

        mClient = builder.build();
        mClient.blockingConnect();
        if (mClient.isConnected()) {
            Log.i(TAG, "Google Fit connected (light)");
            return true;
        } else {
            return false;
        }
    }

    private Date startOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private int steps() {
        DataReadRequest.Builder readRequestBuilder = new DataReadRequest.Builder();
        long st = startOfDay(new Date()).getTime();
        readRequestBuilder.setTimeRange(st, new Date().getTime(), TimeUnit.MILLISECONDS);

        DataSource filteredStepsSource = new DataSource.Builder()
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setType(DataSource.TYPE_DERIVED)
                .setStreamName("estimated_steps")
                .setAppPackageName("com.google.android.gms")
                .build();

        readRequestBuilder.read(filteredStepsSource);

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequestBuilder.build()).await();

        int totalSteps = 0;

        if (dataReadResult.getStatus().isSuccess()) {
            Log.i(TAG, "JUPE dataReadResult success");
            List<DataSet> datasets = dataReadResult.getDataSets();
            Log.i(TAG, "JUPE dataReadResult datasets " + datasets);
            for (DataSet dataset : datasets) {
                for (DataPoint datapoint : dataset.getDataPoints()) {
//                    JSONObject obj = new JSONObject();
//                    obj.put("startDate", datapoint.getStartTime(TimeUnit.MILLISECONDS));
//                    obj.put("endDate", datapoint.getEndTime(TimeUnit.MILLISECONDS));
//                    DataSource dataSource = datapoint.getOriginalDataSource();
//                    if (dataSource != null) {
//                        String sourceName = dataSource.getName();
//                        if (sourceName != null) obj.put("sourceName", sourceName);
//                        String sourceBundleId = dataSource.getAppPackageName();
//                        if (sourceBundleId != null) obj.put("sourceBundleId", sourceBundleId);
//                    }

                    //reference for fields: https://developers.google.com/android/reference/com/google/android/gms/fitness/data/Field.html
                    //if (DT.equals(DataType.TYPE_STEP_COUNT_DELTA)) {
                    int steps = datapoint.getValue(Field.FIELD_STEPS).asInt();
                    totalSteps += steps;
                    Log.i(TAG, "JUPE HealthJobService steps " + steps);
                    //}
                }
            }

            Log.i(TAG, "JUPE HealthJobService total steps " + totalSteps);

            SharedPreferences sharedPref = context.getSharedPreferences("cordova-plugin-health", Context.MODE_PRIVATE);
            String url = sharedPref.getString("url", null);
            String authorization = sharedPref.getString("authorization", null);
            sendHealthToServer(url, authorization, totalSteps);
        } else {
            Log.i(TAG, "JUPE dataReadResult no success " + dataReadResult.getStatus());
        }

        return totalSteps;
    }

    private void sendHealthToServer(String urlString, String authorization, int steps) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000);
        conn.setConnectTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoInput(true);
        conn.setDoOutput(true);

        if (authorization != null) {
            conn.setRequestProperty("Authorization", authorization);
        }
        conn.setRequestProperty("Content-Type", "application/json");

        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(os, "UTF-8"));
        String json = "{ \"steps\": \"" + steps + "}";
        Log.i(GeofencePlugin.TAG, "Sending health to server: " + json);
        writer.write(json);
        writer.flush();
        writer.close();
        os.close();

        conn.connect();
        int responseCode = conn.getResponseCode();
        Log.i(GeofencePlugin.TAG, "Send health to server: " + responseCode);
    }
}