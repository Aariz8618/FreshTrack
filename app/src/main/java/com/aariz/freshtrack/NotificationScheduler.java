package com.aariz.freshtrack;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class NotificationScheduler {

    private static final String EXPIRY_CHECK_WORK = "expiry_check_work";
    private static final long CHECK_INTERVAL_HOURS = 12L;

    private final Context context;

    public NotificationScheduler(Context context) {
        this.context = context;
    }

    public void scheduleExpiryChecks() {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ExpiryCheckWorker.class, CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                EXPIRY_CHECK_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    public void cancelExpiryChecks() {
        WorkManager.getInstance(context).cancelUniqueWork(EXPIRY_CHECK_WORK);
    }

    public void scheduleImmediateCheck() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ExpiryCheckWorker.class)
                .build();

        WorkManager.getInstance(context).enqueue(request);
    }
}