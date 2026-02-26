package com.aariz.freshtrack;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

public class NotificationSettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private NotificationHelper notificationHelper;

    private MaterialButton btnBack;
    private MaterialSwitch switchEnableNotifications;
    private MaterialSwitch switchDailySummary;
    private TextInputEditText inputDaysBefore;
    private LinearLayout btnSave;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
                    showPermissionDeniedDialog();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_notification_settings);

        prefs = getSharedPreferences("notification_prefs", MODE_PRIVATE);
        notificationHelper = new NotificationHelper(this);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));
        WindowInsetsExtensions.applyBottomNavInsets(findViewById(R.id.bottom_bar));

        initViews();
        setupClickListeners();
        loadNotificationSettings();
    }

    private void initViews() {
        btnBack = findViewById(R.id.button_back);
        switchEnableNotifications = findViewById(R.id.switch_enable_notifications);
        switchDailySummary = findViewById(R.id.switch_daily_summary);
        inputDaysBefore = findViewById(R.id.input_days_before);
        btnSave = findViewById(R.id.button_save);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveNotificationSettings());
        inputDaysBefore.setOnClickListener(v -> showDaysPickerDialog());

        switchEnableNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
            switchDailySummary.setEnabled(isChecked);
            inputDaysBefore.setEnabled(isChecked);
            if (isChecked && !notificationHelper.hasNotificationPermission()) {
                requestNotificationPermission();
            }
        });
    }

    private void loadNotificationSettings() {
        boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);
        boolean dailySummaryEnabled = prefs.getBoolean("daily_summary_enabled", true);
        int daysBefore = prefs.getInt("days_before_expiry", 2);

        switchEnableNotifications.setChecked(notificationsEnabled);
        switchDailySummary.setChecked(dailySummaryEnabled);
        inputDaysBefore.setText(daysBefore + " days before");

        switchDailySummary.setEnabled(notificationsEnabled);
        inputDaysBefore.setEnabled(notificationsEnabled);
    }

    private void saveNotificationSettings() {
        boolean notificationsEnabled = switchEnableNotifications.isChecked();
        boolean dailySummaryEnabled = switchDailySummary.isChecked();

        String daysText = inputDaysBefore.getText() != null ? inputDaysBefore.getText().toString() : "";
        String digitsOnly = daysText.replaceAll("[^0-9]", "");
        int daysBefore = digitsOnly.isEmpty() ? 2 : Integer.parseInt(digitsOnly);

        prefs.edit()
                .putBoolean("notifications_enabled", notificationsEnabled)
                .putBoolean("daily_summary_enabled", dailySummaryEnabled)
                .putInt("days_before_expiry", daysBefore)
                .apply();

        NotificationScheduler scheduler = new NotificationScheduler(this);
        if (notificationsEnabled) {
            scheduler.scheduleExpiryChecks();
            Toast.makeText(this, "Notification settings saved!", Toast.LENGTH_SHORT).show();
        } else {
            scheduler.cancelExpiryChecks();
            Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    private void showDaysPickerDialog() {
        String[] options = {"1 day before", "2 days before", "3 days before", "5 days before", "7 days before"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remind me before")
                .setItems(options, (dialog, which) -> {
                    inputDaysBefore.setText(options[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Permission Denied")
                .setMessage("Notification permission is required to receive expiry reminders. You can grant it in app settings.")
                .setPositiveButton("Open Settings", (d, w) -> openAppSettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }
}