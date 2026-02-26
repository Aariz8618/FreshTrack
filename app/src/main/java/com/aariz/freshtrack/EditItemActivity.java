package com.aariz.freshtrack;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class EditItemActivity extends AppCompatActivity {

    private static final String TAG = "EditItemActivity";

    private int quantity = 1;
    private TextView qtyText;
    private EditText inputName;
    private TextView textCategory;
    private TextView textPurchaseDate;
    private TextView textExpiryDate;
    private LinearLayout saveButton;
    private View loadingOverlay;
    private ProgressBar progressBar;
    private ImageView productImageView;
    private LinearLayout barcodeInfoLayout;
    private TextView barcodeText;

    private String selectedCategory = "";
    private String selectedPurchaseDate = "";
    private String selectedExpiryDate = "";
    private String itemId = "";
    private String scannedBarcode = "";
    private String productImageUrl = "";
    private boolean isGS1Code = false;
    private String originalStatus = "fresh";
    private Date createdAt = new Date();

    private FirestoreRepository firestoreRepository;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_edit_item);

        auth = FirebaseAuth.getInstance();
        firestoreRepository = new FirestoreRepository();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));
        WindowInsetsExtensions.applyBottomNavInsets(findViewById(R.id.bottom_bar));

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login to edit items", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadItemData();
        setupClickListeners();
        setupBackPressedHandler();
    }

    // ─────────────────────────────────────────────────────────
    //  Init
    // ─────────────────────────────────────────────────────────

    private void initViews() {
        qtyText = findViewById(R.id.input_quantity);
        inputName = findViewById(R.id.input_name);
        textCategory = findViewById(R.id.text_category);
        textPurchaseDate = findViewById(R.id.text_purchase_date);
        textExpiryDate = findViewById(R.id.text_expiry_date);
        saveButton = findViewById(R.id.button_save_item);
        loadingOverlay = findViewById(R.id.loading_overlay);
        progressBar = findViewById(R.id.progress_bar);
        productImageView = findViewById(R.id.product_image_view);
        barcodeInfoLayout = findViewById(R.id.barcode_info_layout);
        barcodeText = findViewById(R.id.barcode_text);

        inputName.addTextChangedListener(new TextCapitalizationWatcher());
    }

    private void loadItemData() {
        itemId = getIntent().getStringExtra("id") != null ? getIntent().getStringExtra("id") : "";
        String name = getIntent().getStringExtra("name") != null ? getIntent().getStringExtra("name") : "";
        String category = getIntent().getStringExtra("category") != null ? getIntent().getStringExtra("category") : "";
        String expiryDate = getIntent().getStringExtra("expiryDate") != null ? getIntent().getStringExtra("expiryDate") : "";
        String purchaseDate = getIntent().getStringExtra("purchaseDate") != null ? getIntent().getStringExtra("purchaseDate") : "";
        quantity = getIntent().getIntExtra("quantity", 1);
        originalStatus = getIntent().getStringExtra("status") != null ? getIntent().getStringExtra("status") : "fresh";
        scannedBarcode = getIntent().getStringExtra("barcode") != null ? getIntent().getStringExtra("barcode") : "";
        productImageUrl = getIntent().getStringExtra("imageUrl") != null ? getIntent().getStringExtra("imageUrl") : "";
        isGS1Code = getIntent().getBooleanExtra("isGS1", false);

        inputName.setText(name);
        qtyText.setText(String.valueOf(quantity));

        selectedCategory = category;
        textCategory.setText(category);
        textCategory.setTextColor(getColor(R.color.gray_800));

        selectedPurchaseDate = purchaseDate;
        textPurchaseDate.setText(purchaseDate);
        textPurchaseDate.setTextColor(getColor(R.color.gray_800));

        selectedExpiryDate = expiryDate;
        textExpiryDate.setText(expiryDate);
        textExpiryDate.setTextColor(getColor(R.color.gray_800));

        if (!scannedBarcode.isEmpty()) {
            displayBarcodeInfo(scannedBarcode);
        }

        if (!productImageUrl.isEmpty()) {
            productImageView.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(productImageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(productImageView);
        } else {
            productImageView.setVisibility(View.GONE);
        }
    }

    private void displayBarcodeInfo(String barcode) {
        barcodeInfoLayout.setVisibility(View.VISIBLE);
        barcodeText.setText("Barcode: " + barcode);
    }

    // ─────────────────────────────────────────────────────────
    //  Click listeners
    // ─────────────────────────────────────────────────────────

    private void setupClickListeners() {
        ((MaterialButton) findViewById(R.id.btn_back)).setOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());

        ((LinearLayout) findViewById(R.id.button_decrement)).setOnClickListener(v -> {
            if (quantity > 1) quantity--;
            qtyText.setText(String.valueOf(quantity));
        });

        ((LinearLayout) findViewById(R.id.button_increment)).setOnClickListener(v -> {
            quantity++;
            qtyText.setText(String.valueOf(quantity));
        });

        ((LinearLayout) findViewById(R.id.category_container)).setOnClickListener(v ->
                showCategoryDialog());

        ((LinearLayout) findViewById(R.id.purchase_date_container)).setOnClickListener(v ->
                showDatePicker(true));

        ((LinearLayout) findViewById(R.id.expiry_date_container)).setOnClickListener(v ->
                showDatePicker(false));

        saveButton.setOnClickListener(v -> updateItemInFirestore());
    }

    private void showCategoryDialog() {
        String[] categories = {"Dairy", "Meat", "Vegetables", "Fruits", "Bakery",
                "Frozen", "Beverages", "Cereals", "Sweets", "Other"};

        int currentIndex = -1;
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equals(selectedCategory)) {
                currentIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Category")
                .setSingleChoiceItems(categories, currentIndex, (dialog, which) -> {
                    selectedCategory = categories[which];
                    textCategory.setText(selectedCategory);
                    textCategory.setTextColor(getColor(R.color.gray_800));
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDatePicker(boolean isPurchaseDate) {
        Calendar calendar = Calendar.getInstance();

        // Parse current date to set initial picker position
        try {
            String currentDate = isPurchaseDate ? selectedPurchaseDate : selectedExpiryDate;
            if (!currentDate.isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                Date parsed = sdf.parse(currentDate);
                if (parsed != null) calendar.setTime(parsed);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing date", e);
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (datePicker, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth);
                    String formatted = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            .format(selected.getTime());

                    if (isPurchaseDate) {
                        selectedPurchaseDate = formatted;
                        textPurchaseDate.setText(formatted);
                        textPurchaseDate.setTextColor(getColor(R.color.gray_800));
                    } else {
                        selectedExpiryDate = formatted;
                        textExpiryDate.setText(formatted);
                        textExpiryDate.setTextColor(getColor(R.color.gray_800));
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        // Only restrict min date for expiry date picker
        if (!isPurchaseDate) {
            dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        }

        dialog.show();
    }

    // ─────────────────────────────────────────────────────────
    //  Save to Firestore
    // ─────────────────────────────────────────────────────────

    private void updateItemInFirestore() {
        String itemName = inputName.getText().toString().trim();

        if (itemName.isEmpty()) {
            Toast.makeText(this, "Please enter item name", Toast.LENGTH_SHORT).show();
            inputName.requestFocus();
            return;
        }
        if (selectedCategory.isEmpty()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedExpiryDate.isEmpty()) {
            Toast.makeText(this, "Please select expiry date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        int daysLeft = calculateDaysLeft(selectedExpiryDate);
        String status = "used".equals(originalStatus) ? "used" : determineStatus(daysLeft);

        Log.d(TAG, "Updating item: " + itemName + " with id: " + itemId);

        GroceryItem groceryItem = new GroceryItem();
        groceryItem.setId(itemId);
        groceryItem.setName(itemName);
        groceryItem.setCategory(selectedCategory);
        groceryItem.setExpiryDate(selectedExpiryDate);
        groceryItem.setPurchaseDate(selectedPurchaseDate);
        groceryItem.setQuantity(quantity);
        groceryItem.setStatus(status);
        groceryItem.setDaysLeft(daysLeft);
        groceryItem.setBarcode(scannedBarcode);
        groceryItem.setImageUrl(productImageUrl);
        groceryItem.setGS1(isGS1Code);
        groceryItem.setCreatedAt(createdAt);
        groceryItem.setUpdatedAt(new Date());

        showLoading(true);

        firestoreRepository.updateGroceryItem(groceryItem, success -> {
            showLoading(false);
            if (Boolean.TRUE.equals(success)) {
                Log.d(TAG, "Item updated successfully: " + itemName);
                Toast.makeText(this, "Item updated successfully!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Log.e(TAG, "Failed to update item");
                Toast.makeText(this, "Failed to update item. Please try again.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!show);
        saveButton.setAlpha(show ? 0.6f : 1f);
    }

    private int calculateDaysLeft(String expiryDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            Date expiry = sdf.parse(expiryDate);

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            long diff = expiry.getTime() - today.getTime().getTime();
            return (int) TimeUnit.MILLISECONDS.toDays(diff);
        } catch (Exception e) {
            return 0;
        }
    }

    private String determineStatus(int daysLeft) {
        if (daysLeft < 0) return "expired";
        if (daysLeft <= 3) return "expiring";
        return "fresh";
    }

    // ─────────────────────────────────────────────────────────
    //  Back press — unsaved changes guard
    // ─────────────────────────────────────────────────────────

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    showDiscardChangesDialog();
                } else {
                    finish();
                }
            }
        });
    }

    private boolean hasUnsavedChanges() {
        String originalName = getIntent().getStringExtra("name") != null
                ? getIntent().getStringExtra("name") : "";
        String originalCategory = getIntent().getStringExtra("category") != null
                ? getIntent().getStringExtra("category") : "";
        String originalExpiry = getIntent().getStringExtra("expiryDate") != null
                ? getIntent().getStringExtra("expiryDate") : "";
        String originalPurchase = getIntent().getStringExtra("purchaseDate") != null
                ? getIntent().getStringExtra("purchaseDate") : "";
        int originalQuantity = getIntent().getIntExtra("quantity", 1);

        return !inputName.getText().toString().trim().equals(originalName)
                || !selectedCategory.equals(originalCategory)
                || !selectedExpiryDate.equals(originalExpiry)
                || !selectedPurchaseDate.equals(originalPurchase)
                || quantity != originalQuantity;
    }

    private void showDiscardChangesDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Discard Changes")
                .setMessage("You have unsaved changes. Are you sure you want to discard them?")
                .setPositiveButton("Discard", (dialog, which) -> finish())
                .setNegativeButton("Cancel", null)
                .show();
    }
}