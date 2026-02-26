package com.aariz.freshtrack;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class CacheCleanupWorker extends Worker {

    private static final String TAG = "CacheCleanupWorker";
    private static final String WORK_NAME = "cache_cleanup_work";
    private static final long CLEANUP_INTERVAL_DAYS = 7L;

    public CacheCleanupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(TAG, "Starting cache cleanup");

            // Clean internal app cache
            deleteRecursively(getApplicationContext().getCacheDir());

            // Clean external cache if available
            java.io.File externalCache = getApplicationContext().getExternalCacheDir();
            if (externalCache != null) deleteRecursively(externalCache);

            Log.d(TAG, "Cache cleanup completed successfully");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Cache cleanup failed", e);
            return Result.failure();
        }
    }

    private void deleteRecursively(java.io.File file) {
        if (file == null) return;
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) for (java.io.File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    public static void schedulePeriodicCleanup(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                CacheCleanupWorker.class, CLEANUP_INTERVAL_DAYS, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
        Log.d(TAG, "Cache cleanup scheduled");
    }
}