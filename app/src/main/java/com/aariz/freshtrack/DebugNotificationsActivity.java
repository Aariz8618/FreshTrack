package com.aariz.freshtrack;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DebugNotificationsActivity extends AppCompatActivity {

    private TextView tvPermissionStatus;
    private TextView tvNotificationEnabled;
    private TextView tvThrottleStatus;
    private TextView tvExpiringItems;
    private TextView tvLastNotification;
    private MaterialButton btnRefresh;
    private MaterialButton btnRequestPermission;
    private MaterialButton btnOpenSettings;
    private MaterialButton btnTriggerWorker;
    private MaterialButton btnSendTest;
    private MaterialButton btnClearThrottle;
    private MaterialButton btnBack;

    private NotificationHelper notificationHelper;
    private FirestoreRepository firestoreRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_notifications);

        notificationHelper = new NotificationHelper(this);
        firestoreRepository = new FirestoreRepository();

        initViews();
        setupListeners();
        refreshStatus();
    }

    private void initViews() {
        tvPermissionStatus = findViewById(R.id.tv_permission_status);
        tvNotificationEnabled = findViewById(R.id.tv_notification_enabled);
        tvThrottleStatus = findViewById(R.id.tv_throttle_status);
        tvExpiringItems = findViewById(R.id.tv_expiring_items);
        tvLastNotification = findViewById(R.id.tv_last_notification);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnRequestPermission = findViewById(R.id.btn_request_permission);
        btnOpenSettings = findViewById(R.id.btn_open_settings);
        btnTriggerWorker = findViewById(R.id.btn_trigger_worker);
        btnSendTest = findViewById(R.id.btn_send_test);
        btnClearThrottle = findViewById(R.id.btn_clear_throttle);
        btnBack = findViewById(R.id.btn_back);
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> refreshStatus());

        btnRequestPermission.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        });

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        });

        btnTriggerWorker.setOnClickListener(v -> triggerWorkerNow());

        btnSendTest.setOnClickListener(v -> {
            notificationHelper.showExpiryNotification("Debug Test Item", 2, 1);
            Toast.makeText(this, "Test notification sent!", Toast.LENGTH_SHORT).show();
        });

        btnClearThrottle.setOnClickListener(v -> {
            getSharedPreferences("notification_prefs", MODE_PRIVATE)
                    .edit().remove("last_notification_timestamp").apply();
            Toast.makeText(this, "Throttle cleared!", Toast.LENGTH_SHORT).show();
            refreshStatus();
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void refreshStatus() {
        // Permission status
        boolean hasPermission = notificationHelper.hasNotificationPermission();
        tvPermissionStatus.setText(hasPermission ? "✓ GRANTED" : "✗ NOT GRANTED");
        tvPermissionStatus.setTextColor(getColor(hasPermission ? R.color.green_primary : R.color.red_500));

        // Notifications enabled status
        boolean areEnabled = notificationHelper.areNotificationsEnabled();
        tvNotificationEnabled.setText(areEnabled ? "✓ ENABLED" : "✗ DISABLED");
        tvNotificationEnabled.setTextColor(getColor(areEnabled ? R.color.green_primary : R.color.red_500));

        // Throttle status
        SharedPreferences prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE);
        long lastNotificationTime = prefs.getLong("last_notification_timestamp", 0);
        long currentTime = System.currentTimeMillis();
        long hoursSince = TimeUnit.MILLISECONDS.toHours(currentTime - lastNotificationTime);

        if (lastNotificationTime == 0L) {
            tvThrottleStatus.setText("Never sent");
            tvThrottleStatus.setTextColor(getColor(R.color.gray_600));
        } else if (hoursSince < 6) {
            tvThrottleStatus.setText("⏱ THROTTLED (" + (6 - hoursSince) + "h remaining)");
            tvThrottleStatus.setTextColor(getColor(R.color.orange_500));
        } else {
            tvThrottleStatus.setText("✓ Ready to send");
            tvThrottleStatus.setTextColor(getColor(R.color.green_primary));
        }

        // Last notification time
        if (lastNotificationTime == 0L) {
            tvLastNotification.setText("Never");
        } else {
            String date = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    .format(new Date(lastNotificationTime));
            tvLastNotification.setText(date);
        }

        checkExpiringItems();
    }

    private void checkExpiringItems() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            tvExpiringItems.setText("Not logged in");
            return;
        }

        firestoreRepository.getUserGroceryItemsAsync(items -> {
            if (items == null) {
                runOnUiThread(() -> tvExpiringItems.setText("Error fetching items"));
                return;
            }

            SharedPreferences prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE);
            int daysBefore = prefs.getInt("days_before_expiry", 2);

            int expiringCount = 0, expiredCount = 0;
            for (GroceryItem item : items) {
                int daysLeft = calculateDaysLeft(item.getExpiryDate());
                if (daysLeft < 0) expiredCount++;
                else if (daysLeft <= daysBefore) expiringCount++;
            }

            final int finalExpiring = expiringCount;
            final int finalExpired = expiredCount;

            runOnUiThread(() -> {
                tvExpiringItems.setText(finalExpiring + " expiring, " + finalExpired + " expired");
                int color;
                if (finalExpired > 0) color = R.color.red_500;
                else if (finalExpiring > 0) color = R.color.orange_500;
                else color = R.color.green_primary;
                tvExpiringItems.setTextColor(getColor(color));
            });
        });
    }

    private void triggerWorkerNow() {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ExpiryCheckWorker.class).build();
        WorkManager.getInstance(this).enqueue(request);
        Toast.makeText(this, "Worker triggered! Check status in 5-10 seconds", Toast.LENGTH_LONG).show();
        btnRefresh.postDelayed(this::refreshStatus, 10_000);
    }

    private int calculateDaysLeft(String expiryDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
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
            return 0;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }
}