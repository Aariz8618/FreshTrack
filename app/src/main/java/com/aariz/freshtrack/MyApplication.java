package com.aariz.freshtrack;

import android.app.Application;

import com.google.firebase.FirebaseApp;

public class MyApplication extends Application {

    private NotificationScheduler notificationScheduler;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // Initialize notification scheduler
        notificationScheduler = new NotificationScheduler(this);

        // Schedule expiry checks
        notificationScheduler.scheduleExpiryChecks();

        // Schedule periodic cache cleanup
        CacheCleanupWorker.schedulePeriodicCleanup(this);
    }
}