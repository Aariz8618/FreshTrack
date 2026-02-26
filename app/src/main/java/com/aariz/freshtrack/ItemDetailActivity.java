package com.aariz.freshtrack;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ItemDetailActivity extends AppCompatActivity {

    private FirestoreRepository firestoreRepository;
    private View loadingOverlay;
    private ProgressBar progressBar;
    private String itemId = "";

    // Countdown timer views
    private TextView countdownDays;
    private TextView countdownHours;
    private TextView countdownMinutes;
    private LinearLayout countdownSection;
    private TextView statusChip;
    private TextView statusText;
    private ImageView itemIcon;

    // Timeline views
    private View timelinePurchaseDot;
    private View timelineExpiryDot;
    private View timelineProgress;
    private View timelineLine;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_item_detail);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));
        WindowInsetsExtensions.applyBottomNavInsets(findViewById(R.id.bottom_bar));

        firestoreRepository = new FirestoreRepository();

        // Loading views
        loadingOverlay = findViewById(R.id.loading_overlay);
        progressBar = findViewById(R.id.progress_bar);

        // Countdown views
        countdownDays = findViewById(R.id.text_countdown_days);
        countdownHours = findViewById(R.id.text_countdown_hours);
        countdownMinutes = findViewById(R.id.text_countdown_minutes);
        countdownSection = findViewById(R.id.countdown_section);
        statusChip = findViewById(R.id.text_status_chip);
        statusText = findViewById(R.id.text_status);
        itemIcon = findViewById(R.id.img_item_icon);

        // Timeline views
        timelinePurchaseDot = findViewById(R.id.timeline_purchase_dot);
        timelineExpiryDot = findViewById(R.id.timeline_expiry_dot);
        timelineProgress = findViewById(R.id.timeline_progress);
        timelineLine = findViewById(R.id.timeline_line);

        // Read intent extras
        itemId = getIntent().getStringExtra("id") != null ? getIntent().getStringExtra("id") : "";
        String name = getIntent().getStringExtra("name") != null ? getIntent().getStringExtra("name") : "";
        String category = getIntent().getStringExtra("category") != null ? getIntent().getStringExtra("category") : "";
        String expiry = getIntent().getStringExtra("expiryDate") != null ? getIntent().getStringExtra("expiryDate") : "";
        String purchase = getIntent().getStringExtra("purchaseDate") != null ? getIntent().getStringExtra("purchaseDate") : "";
        int quantity = getIntent().getIntExtra("quantity", 0);
        String status = getIntent().getStringExtra("status") != null ? getIntent().getStringExtra("status") : "fresh";

        // Recalculate days left and status in real-time
        int actualDaysLeft = calculateDaysLeft(expiry);
        String actualStatus = determineStatus(actualDaysLeft, status);

        ((TextView) findViewById(R.id.text_name)).setText(name);
        ((TextView) findViewById(R.id.text_category_chip)).setText(category);
        ((TextView) findViewById(R.id.text_expiry)).setText(expiry);
        ((TextView) findViewById(R.id.text_purchase)).setText(purchase);
        ((TextView) findViewById(R.id.text_quantity)).setText(String.valueOf(quantity));

        setItemIcon(category);
        updateStatusDisplay(actualStatus, actualDaysLeft);
        updateTimelineVisuals(purchase, expiry, actualStatus);
        startCountdownTimer(expiry, actualStatus);
        setupClickListeners(actualStatus);
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────

    private int calculateDaysLeft(String expiryDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
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
            return 0;
        }
    }

    private String determineStatus(int daysLeft, String currentStatus) {
        if ("used".equals(currentStatus)) return "used";
        if (daysLeft < 0) return "expired";
        if (daysLeft <= 3) return "expiring";
        return "fresh";
    }

    private void setItemIcon(String category) {
        int iconRes;
        switch (category.toLowerCase()) {
            case "fruits":
            case "fruit":
                iconRes = R.drawable.fruits; break;
            case "dairy":
                iconRes = R.drawable.milk; break;
            case "vegetables":
            case "vegetable":
                iconRes = R.drawable.vegetables; break;
            case "meat":
                iconRes = R.drawable.meat; break;
            case "bakery":
                iconRes = R.drawable.bread; break;
            case "frozen":
                iconRes = R.drawable.frozen; break;
            case "beverages":
                iconRes = R.drawable.beverages; break;
            case "cereals":
                iconRes = R.drawable.cereals; break;
            case "sweets":
                iconRes = R.drawable.sweets; break;
            default:
                iconRes = R.drawable.ic_grocery; break;
        }
        itemIcon.setImageResource(iconRes);
    }

    private void updateStatusDisplay(String status, int daysLeft) {
        switch (status) {
            case "fresh":
                statusChip.setText("Fresh (" + daysLeft + " days left)");
                statusChip.setTextColor(Color.parseColor("#2E7D32"));
                statusText.setText("Fresh");
                statusText.setTextColor(Color.parseColor("#2E7D32"));
                break;

            case "expiring":
                String daysLabel;
                if (daysLeft == 0) daysLabel = "Today";
                else if (daysLeft == 1) daysLabel = "1 day";
                else daysLabel = daysLeft + " days";
                statusChip.setText("Expires in " + daysLabel);
                statusChip.setTextColor(Color.parseColor("#FFC107"));
                statusText.setText("Expiring");
                statusText.setTextColor(Color.parseColor("#FFC107"));
                break;

            case "expired":
                int daysAgo = Math.abs(daysLeft);
                String expiredText;
                if (daysAgo == 0) expiredText = "Expired today";
                else if (daysAgo == 1) expiredText = "Expired 1 day ago";
                else expiredText = "Expired " + daysAgo + " days ago";
                statusChip.setText(expiredText);
                statusChip.setTextColor(Color.parseColor("#B71C1C"));
                statusText.setText("Expired");
                statusText.setTextColor(Color.parseColor("#B71C1C"));
                break;

            case "used":
                statusChip.setText("Used");
                statusChip.setTextColor(Color.parseColor("#FF9800"));
                statusText.setText("Used");
                statusText.setTextColor(Color.parseColor("#FF9800"));
                break;
        }
    }

    private void updateTimelineVisuals(String purchaseDateStr, String expiryDateStr, String status) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            Date purchaseDate = sdf.parse(purchaseDateStr);
            Date expiryDate = sdf.parse(expiryDateStr);
            Date now = Calendar.getInstance().getTime();

            if (purchaseDate != null && expiryDate != null) {
                long totalDuration = expiryDate.getTime() - purchaseDate.getTime();
                long elapsed = now.getTime() - purchaseDate.getTime();
                float progress = Math.max(0f, Math.min(1f, (float) elapsed / totalDuration));

                int lineHeight = timelineLine.getLayoutParams().height;
                int progressHeight = (int) (lineHeight * progress);
                timelineProgress.getLayoutParams().height = progressHeight;
                timelineProgress.requestLayout();

                switch (status) {
                    case "fresh":
                        timelineProgress.setBackgroundColor(Color.parseColor("#4CAF50"));
                        break;
                    case "expiring":
                        timelineProgress.setBackgroundColor(Color.parseColor("#FFC107"));
                        break;
                    case "expired":
                        timelineProgress.setBackgroundColor(Color.parseColor("#F44336"));
                        timelineExpiryDot.setBackgroundResource(R.drawable.circle_icon_bg);
                        break;
                    case "used":
                        timelineProgress.setBackgroundColor(Color.parseColor("#FF9800"));
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startCountdownTimer(String expiryDateStr, String status) {
        if ("expired".equals(status) || "used".equals(status)) {
            countdownSection.setVisibility(View.GONE);
            return;
        }

        countdownSection.setVisibility(View.VISIBLE);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    Date expiryDate = sdf.parse(expiryDateStr);

                    if (expiryDate != null) {
                        Calendar expiryCalendar = Calendar.getInstance();
                        expiryCalendar.setTime(expiryDate);
                        expiryCalendar.set(Calendar.HOUR_OF_DAY, 23);
                        expiryCalendar.set(Calendar.MINUTE, 59);
                        expiryCalendar.set(Calendar.SECOND, 59);
                        expiryCalendar.set(Calendar.MILLISECOND, 999);

                        long diffInMillis = expiryCalendar.getTimeInMillis()
                                - Calendar.getInstance().getTimeInMillis();

                        if (diffInMillis <= 0) {
                            countdownDays.setText("00");
                            countdownHours.setText("00");
                            countdownMinutes.setText("00");
                            countdownSection.setVisibility(View.GONE);
                            return;
                        }

                        long days = TimeUnit.MILLISECONDS.toDays(diffInMillis);
                        long hours = TimeUnit.MILLISECONDS.toHours(diffInMillis) % 24;
                        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis) % 60;

                        countdownDays.setText(String.format(Locale.getDefault(), "%02d", days));
                        countdownHours.setText(String.format(Locale.getDefault(), "%02d", hours));
                        countdownMinutes.setText(String.format(Locale.getDefault(), "%02d", minutes));

                        int color;
                        if (days < 1) color = Color.parseColor("#B71C1C");
                        else if (days <= 3) color = Color.parseColor("#F57C00");
                        else color = Color.parseColor("#2E7D32");

                        countdownDays.setTextColor(color);
                        countdownHours.setTextColor(color);
                        countdownMinutes.setTextColor(color);

                        // Update every minute
                        handler.postDelayed(this, 60000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        handler.post(countdownRunnable);
    }

    // ─────────────────────────────────────────────────────────
    //  Click listeners
    // ─────────────────────────────────────────────────────────

    private void setupClickListeners(String actualStatus) {
        ((MaterialButton) findViewById(R.id.btn_back)).setOnClickListener(v -> finish());

        MaterialCardView editButton = findViewById(R.id.button_edit);
        MaterialCardView markUsedButton = findViewById(R.id.button_mark_used);

        if ("used".equals(actualStatus)) {
            editButton.setVisibility(View.GONE);
            markUsedButton.setVisibility(View.GONE);
        } else {
            editButton.setOnClickListener(v -> editItem());
            markUsedButton.setOnClickListener(v -> showMarkAsUsedConfirmation());
        }

        ((MaterialCardView) findViewById(R.id.button_delete))
                .setOnClickListener(v -> showDeleteConfirmation());
    }

    private void editItem() {
        Intent intent = new Intent(this, EditItemActivity.class);
        intent.putExtra("id", itemId);
        intent.putExtra("name", getIntent().getStringExtra("name"));
        intent.putExtra("category", getIntent().getStringExtra("category"));
        intent.putExtra("expiryDate", getIntent().getStringExtra("expiryDate"));
        intent.putExtra("purchaseDate", getIntent().getStringExtra("purchaseDate"));
        intent.putExtra("quantity", getIntent().getIntExtra("quantity", 1));
        intent.putExtra("status", getIntent().getStringExtra("status"));
        intent.putExtra("barcode", getIntent().getStringExtra("barcode") != null
                ? getIntent().getStringExtra("barcode") : "");
        intent.putExtra("imageUrl", getIntent().getStringExtra("imageUrl") != null
                ? getIntent().getStringExtra("imageUrl") : "");
        intent.putExtra("isGS1", getIntent().getBooleanExtra("isGS1", false));
        startActivity(intent);
        finish();
    }

    private void showMarkAsUsedConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Mark as Used")
                .setMessage("Mark this item as used? This will update its status and the item card will turn green.")
                .setPositiveButton("Mark as Used", (dialog, which) -> markItemAsUsed())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markItemAsUsed() {
        if (itemId.isEmpty()) {
            Toast.makeText(this, "Error: Invalid item ID", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        GroceryItem updatedItem = new GroceryItem();
        updatedItem.setId(itemId);
        updatedItem.setName(getIntent().getStringExtra("name") != null
                ? getIntent().getStringExtra("name") : "");
        updatedItem.setCategory(getIntent().getStringExtra("category") != null
                ? getIntent().getStringExtra("category") : "");
        updatedItem.setExpiryDate(getIntent().getStringExtra("expiryDate") != null
                ? getIntent().getStringExtra("expiryDate") : "");
        updatedItem.setPurchaseDate(getIntent().getStringExtra("purchaseDate") != null
                ? getIntent().getStringExtra("purchaseDate") : "");
        updatedItem.setQuantity(getIntent().getIntExtra("quantity", 0));
        updatedItem.setStatus("used");
        updatedItem.setDaysLeft(getIntent().getIntExtra("daysLeft", 0));
        updatedItem.setBarcode(getIntent().getStringExtra("barcode") != null
                ? getIntent().getStringExtra("barcode") : "");
        updatedItem.setImageUrl(getIntent().getStringExtra("imageUrl") != null
                ? getIntent().getStringExtra("imageUrl") : "");
        updatedItem.setGS1(getIntent().getBooleanExtra("isGS1", false));
        updatedItem.setCreatedAt(new Date());
        updatedItem.setUpdatedAt(new Date());

        firestoreRepository.updateGroceryItem(updatedItem, success -> {
            showLoading(false);
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(this, "Item marked as used!", Toast.LENGTH_SHORT).show();
                Intent result = new Intent();
                result.putExtra("item_updated", true);
                setResult(RESULT_OK, result);
                finish();
            } else {
                Toast.makeText(this, "Failed to update item. Please try again.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDeleteConfirmation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to delete this item? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteItem())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteItem() {
        if (itemId.isEmpty()) {
            Toast.makeText(this, "Error: Invalid item ID", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        firestoreRepository.deleteGroceryItem(itemId, success -> {
            showLoading(false);
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(this, "Item deleted successfully!", Toast.LENGTH_SHORT).show();
                Intent result = new Intent();
                result.putExtra("item_deleted", true);
                setResult(RESULT_OK, result);
                finish();
            } else {
                Toast.makeText(this, "Failed to delete item. Please try again.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
        }
    }
}