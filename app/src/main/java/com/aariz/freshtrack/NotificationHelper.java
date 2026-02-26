package com.aariz.freshtrack;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class NotificationHelper {

    public static final String EXPIRY_CHANNEL_ID = "expiry_notifications";
    public static final String EXPIRY_CHANNEL_NAME = "Expiry Reminders";
    public static final String EXPIRY_CHANNEL_DESCRIPTION = "Notifications for items about to expire";
    public static final int EXPIRY_NOTIFICATION_ID = 1001;
    private static final String TAG = "NotificationHelper";

    private final Context context;

    public NotificationHelper(Context context) {
        this.context = context;
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    EXPIRY_CHANNEL_ID,
                    EXPIRY_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(EXPIRY_CHANNEL_DESCRIPTION);
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setShowBadge(true);

            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created");
        }
    }

    public void showExpiryNotification(String itemName, int daysLeft, int totalItems) {
        SharedPreferences prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("notifications_enabled", true)) {
            Log.d(TAG, "Notifications disabled in settings");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission not granted");
                return;
            }
        }

        Intent intent = new Intent(context, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String[] content = buildNotificationContent(itemName, daysLeft, totalItems);
        String title = content[0];
        String message = content[1];

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, EXPIRY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_REMINDER);

        try {
            NotificationManagerCompat.from(context).notify(EXPIRY_NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Notification sent: " + title);
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for notifications", e);
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }

    /** Convenience overload with totalItems = 1 */
    public void showExpiryNotification(String itemName, int daysLeft) {
        showExpiryNotification(itemName, daysLeft, 1);
    }

    /** Returns [title, message] */
    private String[] buildNotificationContent(String itemName, int daysLeft, int totalItems) {
        String title, message;

        if (totalItems == 1 && daysLeft < 0) {
            title = "⚠️ Item Expired!";
            message = itemName + " has expired. Check it now to avoid waste.";
        } else if (totalItems == 1 && daysLeft == 0) {
            title = "⏰ Expires Today!";
            message = itemName + " expires today. Use it soon!";
        } else if (totalItems == 1 && daysLeft == 1) {
            title = "⏰ Expires Tomorrow";
            message = itemName + " expires tomorrow. Plan to use it!";
        } else if (totalItems == 1) {
            title = "⏰ Expiring Soon";
            message = itemName + " expires in " + daysLeft + " days. Don't forget about it!";
        } else if (daysLeft < 0) {
            title = "⚠️ " + totalItems + " Items Expired!";
            message = "You have " + totalItems + " expired items. Check your expiry tracker.";
        } else if (daysLeft == 0) {
            title = "⏰ " + totalItems + " Items Expire Today!";
            message = totalItems + " items expire today. Use them before they go bad!";
        } else if (daysLeft == 1) {
            title = "⏰ " + totalItems + " Items Expire Tomorrow";
            message = totalItems + " items expire tomorrow. Check your tracker!";
        } else {
            title = "⏰ " + totalItems + " Items Expiring Soon";
            message = totalItems + " items expire in " + daysLeft + " days. Plan ahead!";
        }

        return new String[]{title, message};
    }

    public boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Older versions don't need runtime permission
    }

    public boolean areNotificationsEnabled() {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
}