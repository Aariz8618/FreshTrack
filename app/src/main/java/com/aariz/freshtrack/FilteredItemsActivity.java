package com.aariz.freshtrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class FilteredItemsActivity extends AppCompatActivity {

    private FirestoreRepository firestoreRepository;
    private FirebaseAuth auth;
    private RecyclerView recyclerView;
    private GroceryAdapter adapter;
    private LinearLayout emptyState;
    private LinearLayout loadingIndicator;
    private TextView titleText;

    private final List<GroceryItem> groceryItems = new ArrayList<>();
    private String filterStatus = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_filtered_items);

        auth = FirebaseAuth.getInstance();
        firestoreRepository = new FirestoreRepository();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));

        filterStatus = getIntent().getStringExtra("filter_status");
        if (filterStatus == null) filterStatus = "all";

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        loadFilteredItems();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_filtered_items);
        emptyState = findViewById(R.id.empty_state_filtered);
        loadingIndicator = findViewById(R.id.loading_indicator_filtered);
        titleText = findViewById(R.id.tv_filtered_title);

        String title;
        switch (filterStatus) {
            case "used":    title = "Used Items ✓";      break;
            case "expired": title = "Expired Items ⚠️"; break;
            case "fresh":   title = "Fresh Items 🌱";    break;
            default:        title = "All Items";          break;
        }
        titleText.setText(title);

        Button buttonBack = findViewById(R.id.button_back_filtered);
        buttonBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new GroceryAdapter(
                groceryItems,
                item -> {
                    Intent intent = new Intent(this, ItemDetailActivity.class);
                    intent.putExtra("id", item.getId());
                    intent.putExtra("name", item.getName());
                    intent.putExtra("category", item.getCategory());
                    intent.putExtra("expiryDate", item.getExpiryDate());
                    intent.putExtra("purchaseDate", item.getPurchaseDate());
                    intent.putExtra("quantity", item.getQuantity());
                    intent.putExtra("status", item.getStatus());
                    intent.putExtra("daysLeft", item.getDaysLeft());
                    intent.putExtra("barcode", item.getBarcode());
                    intent.putExtra("imageUrl", item.getImageUrl());
                    intent.putExtra("isGS1", item.isGS1());
                    startActivity(intent);
                },
                false
        );

        recyclerView.setAdapter(adapter);
    }

    private void loadFilteredItems() {
        showLoading(true);

        firestoreRepository.getUserGroceryItemsAsync(allItems -> {
            runOnUiThread(() -> {
                showLoading(false);

                if (allItems == null) {
                    Toast.makeText(FilteredItemsActivity.this, "Failed to load items", Toast.LENGTH_LONG).show();
                    updateEmptyState();
                    return;
                }

                List<GroceryItem> itemsWithUpdatedStatus = new ArrayList<>();
                for (GroceryItem item : allItems) {
                    int daysLeft = calculateDaysLeft(item.getExpiryDate());
                    String actualStatus = determineStatus(daysLeft, item.getStatus());
                    item.setDaysLeft(daysLeft);
                    item.setStatus(actualStatus);
                    itemsWithUpdatedStatus.add(item);
                }

                List<GroceryItem> filteredItems = new ArrayList<>();
                for (GroceryItem item : itemsWithUpdatedStatus) {
                    switch (filterStatus) {
                        case "used":
                            if ("used".equals(item.getStatus())) filteredItems.add(item);
                            break;
                        case "expired":
                            if ("expired".equals(item.getStatus())) filteredItems.add(item);
                            break;
                        case "fresh":
                            if ("fresh".equals(item.getStatus()) || "expiring".equals(item.getStatus()))
                                filteredItems.add(item);
                            break;
                        default:
                            filteredItems.add(item);
                            break;
                    }
                }

                groceryItems.clear();
                groceryItems.addAll(filteredItems);
                adapter.notifyDataSetChanged();
                updateEmptyState();
            });
        });
    }

    private int calculateDaysLeft(String expiryDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            sdf.setLenient(false);

            java.util.Date expiry = sdf.parse(expiryDate);
            if (expiry == null) return 0;

            Calendar expiryCalendar = Calendar.getInstance();
            expiryCalendar.setTime(expiry);
            expiryCalendar.set(Calendar.HOUR_OF_DAY, 0);
            expiryCalendar.set(Calendar.MINUTE, 0);
            expiryCalendar.set(Calendar.SECOND, 0);
            expiryCalendar.set(Calendar.MILLISECOND, 0);

            Calendar todayCalendar = Calendar.getInstance();
            todayCalendar.set(Calendar.HOUR_OF_DAY, 0);
            todayCalendar.set(Calendar.MINUTE, 0);
            todayCalendar.set(Calendar.SECOND, 0);
            todayCalendar.set(Calendar.MILLISECOND, 0);

            long diffInMillis = expiryCalendar.getTimeInMillis() - todayCalendar.getTimeInMillis();
            return (int) TimeUnit.MILLISECONDS.toDays(diffInMillis);
        } catch (Exception e) {
            return 0;
        }
    }

    private String determineStatus(int daysLeft, String currentStatus) {
        if ("used".equals(currentStatus)) return "used";

        if (daysLeft < 0) return "expired";
        if (daysLeft == 0) return "expiring";
        if (daysLeft <= 3) return "expiring";
        return "fresh";
    }

    private void updateEmptyState() {
        if (groceryItems.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);

            TextView emptyMessage = findViewById(R.id.tv_empty_message);
            switch (filterStatus) {
                case "used":    emptyMessage.setText("No used items yet"); break;
                case "expired": emptyMessage.setText("No expired items");  break;
                case "fresh":   emptyMessage.setText("No fresh items");    break;
                default:        emptyMessage.setText("No items found");    break;
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean show) {
        loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFilteredItems();
    }
}