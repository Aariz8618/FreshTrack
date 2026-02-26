package com.aariz.freshtrack;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ExpiryCheckWorker extends Worker {

    private static final String TAG = "ExpiryCheckWorker";
    private static final String LAST_NOTIFICATION_KEY = "last_notification_timestamp";
    private static final int MIN_NOTIFICATION_INTERVAL_HOURS = 6;

    private final NotificationHelper notificationHelper;
    private final FirestoreRepository firestoreRepository;
    private final SharedPreferences prefs;

    public ExpiryCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        notificationHelper = new NotificationHelper(context);
        firestoreRepository = new FirestoreRepository();
        prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(TAG, "Starting expiry check work");

            // Check if notifications are enabled
            if (!prefs.getBoolean("notifications_enabled", true)) {
                Log.d(TAG, "Notifications disabled, skipping check");
                return Result.success();
            }

            // Throttle check
            if (shouldThrottleNotifications()) {
                Log.d(TAG, "Throttling notifications - too soon since last notification");
                return Result.success();
            }

            // Auth check
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Log.d(TAG, "User not authenticated, skipping check");
                return Result.success();
            }

            int daysBefore = prefs.getInt("days_before_expiry", 2);
            boolean dailySummaryEnabled = prefs.getBoolean("daily_summary_enabled", true);

            // Fetch items synchronously using CountDownLatch (replaces coroutine await)
            List<GroceryItem> items = fetchItemsSync();
            if (items == null) {
                Log.e(TAG, "Failed to fetch items");
                return Result.retry();
            }

            Log.d(TAG, "Fetched " + items.size() + " items");

            // Filter out items already marked as used/consumed — no need to notify for those
            List<GroceryItem> activeItems = new ArrayList<>();
            for (GroceryItem item : items) {
                String s = item.getStatus();
                if (!"used".equalsIgnoreCase(s) && !"consumed".equalsIgnoreCase(s)) {
                    activeItems.add(item);
                }
            }

            // Filter expiring and expired items
            List<GroceryItem> expiringItems = new ArrayList<>();
            List<GroceryItem> expiredItems = new ArrayList<>();

            for (GroceryItem item : activeItems) {
                int daysLeft = calculateDaysLeft(item.getExpiryDate());
                if (daysLeft < 0) {
                    expiredItems.add(item);
                } else if (daysLeft <= daysBefore) {
                    expiringItems.add(item);
                }
            }

            Log.d(TAG, "Found " + expiringItems.size() + " expiring, " + expiredItems.size() + " expired");

            // Send notifications (expired takes priority; expiring only if daily summary is on)
            if (!expiredItems.isEmpty()) {
                sendNotificationForItems(expiredItems);
            } else if (!expiringItems.isEmpty() && dailySummaryEnabled) {
                sendNotificationForItems(expiringItems);
            }

            if (!expiredItems.isEmpty() || !expiringItems.isEmpty()) {
                updateLastNotificationTimestamp();
            }

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error in expiry check worker", e);
            return Result.retry();
        }
    }

    /**
     * Blocks the worker thread until Firestore returns results.
     * This is the Java equivalent of the Kotlin coroutine await().
     */
    private List<GroceryItem> fetchItemsSync() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<GroceryItem>> resultRef = new AtomicReference<>(null);

        firestoreRepository.getUserGroceryItemsAsync(items -> {
            resultRef.set(items);
            latch.countDown();
        });

        try {
            // Wait up to 15 seconds for Firestore to respond
            boolean completed = latch.await(15, TimeUnit.SECONDS);
            if (!completed) {
                Log.e(TAG, "Firestore fetch timed out");
                return null;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for Firestore", e);
            Thread.currentThread().interrupt();
            return null;
        }

        return resultRef.get();
    }

    private boolean shouldThrottleNotifications() {
        long lastNotificationTime = prefs.getLong(LAST_NOTIFICATION_KEY, 0);
        long hoursSince = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - lastNotificationTime);
        return hoursSince < MIN_NOTIFICATION_INTERVAL_HOURS;
    }

    private void updateLastNotificationTimestamp() {
        prefs.edit().putLong(LAST_NOTIFICATION_KEY, System.currentTimeMillis()).apply();
    }

    private void sendNotificationForItems(List<GroceryItem> items) {
        if (items.isEmpty()) return;

        // Sort by daysLeft ascending so the most urgent item is first
        List<GroceryItem> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) ->
                Integer.compare(calculateDaysLeft(a.getExpiryDate()), calculateDaysLeft(b.getExpiryDate())));

        GroceryItem firstItem = sorted.get(0);
        int daysLeft = calculateDaysLeft(firstItem.getExpiryDate());

        notificationHelper.showExpiryNotification(firstItem.getName(), daysLeft, items.size());
        Log.d(TAG, "Sent notification for " + items.size() + " items");
    }

    private int calculateDaysLeft(String expiryDate) {
        if (expiryDate == null || expiryDate.trim().isEmpty()) {
            return Integer.MAX_VALUE; // Treat missing dates as never expiring
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH);
            sdf.setLenient(false);
            Date expiry = sdf.parse(expiryDate);
            if (expiry == null) return 0;

            Calendar expiryCalendar = Calendar.getInstance();
            expiryCalendar.setTime(expiry);
            expiryCalendar.set(Calendar.HOUR_OF_DAY, 0);
            expiryCalendar.set(Calendar.MINUTE, 0);
            expiryCalendar.set(Calendar.SECOND, 0);
            expiryCalendar.set(Calendar.MILLISECOND, 0);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            return (int) TimeUnit.MILLISECONDS.toDays(
                    expiryCalendar.getTimeInMillis() - today.getTimeInMillis());
        } catch (Exception e) {
            Log.e(TAG, "Error calculating days left: " + e.getMessage(), e);
            return 0;
        }
    }
}