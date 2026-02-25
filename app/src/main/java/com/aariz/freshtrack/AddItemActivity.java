package com.aariz.freshtrack;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AddItemActivity extends AppCompatActivity {

    private int quantity = 1;
    private TextView qtyText;
    private EditText inputName;
    private TextView textCategory;
    private TextView textPurchaseDate;
    private TextView textExpiryDate;
    private EditText inputAmount;
    private EditText inputWeight;
    private TextView textWeightUnit;
    private LinearLayout weightUnitSelector;
    private TextView textStorageLocation;
    private EditText inputNotes;
    private LinearLayout additionalInfoHeader;
    private LinearLayout additionalInfoContent;
    private ImageView additionalInfoChevron;
    private MaterialCardView saveButton;
    private View loadingOverlay;
    private ProgressBar progressBar;
    private ImageView productImageView;
    private MaterialCardView barcodeInfoLayout;
    private TextView barcodeText;
    private MaterialCardView gs1InfoLayout;
    private TextView gs1InfoText;
    private MaterialCardView addImageButton;
    private MaterialCardView removeImageButton;
    private MaterialCardView buttonScan;
    private ImageView buttonDecrement;
    private ImageView buttonIncrement;

    private String selectedCategory = "";
    private String selectedPurchaseDate = "";
    private String selectedExpiryDate = "";
    private String selectedStorageLocation = "";
    private String selectedWeightUnit = "kg";
    private String scannedBarcode = "";
    private String productImageUrl = "";
    private Uri userUploadedImageUri = null;
    private boolean isGS1Code = false;
    private boolean isAdditionalInfoExpanded = false;

    private FirestoreRepository firestoreRepository;
    private FirebaseAuth auth;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) handleImageSelection(uri);
                }
        );

        setContentView(R.layout.screen_add_item);

        auth = FirebaseAuth.getInstance();
        firestoreRepository = new FirestoreRepository();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login to add items", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupClickListeners();
        setupInitialValues();
        setupBackPressedHandler();
        createUserProfileIfNeeded();
        applyWindowInsets();
    }

    private void applyWindowInsets() {
        InsetUtils.applyHeaderInsets(findViewById(R.id.header_section));
        InsetUtils.applyBottomNavInsets(findViewById(R.id.bottom_bar));
    }

    private void createUserProfileIfNeeded() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        String displayName = auth.getCurrentUser().getDisplayName() != null
                ? auth.getCurrentUser().getDisplayName() : "User";
        String email = auth.getCurrentUser().getEmail() != null
                ? auth.getCurrentUser().getEmail() : "";

        executor.execute(() -> {
            try {
                User user = new User(uid, displayName, email);
                firestoreRepository.createUserProfile(user);
            } catch (Exception e) {
                Log.e("AddItemActivity", "Error creating user profile: " + e.getMessage());
            }
        });
    }

    private void initViews() {
        qtyText = findViewById(R.id.input_quantity);
        inputName = findViewById(R.id.input_name);
        textCategory = findViewById(R.id.text_category);
        textPurchaseDate = findViewById(R.id.text_purchase_date);
        textExpiryDate = findViewById(R.id.text_expiry_date);
        inputAmount = findViewById(R.id.input_amount);
        inputWeight = findViewById(R.id.input_weight);
        textWeightUnit = findViewById(R.id.text_weight_unit);
        weightUnitSelector = findViewById(R.id.weight_unit_selector);
        textStorageLocation = findViewById(R.id.text_storage_location);
        inputNotes = findViewById(R.id.input_notes);
        additionalInfoHeader = findViewById(R.id.additional_info_header);
        additionalInfoContent = findViewById(R.id.additional_info_content);
        additionalInfoChevron = findViewById(R.id.additional_info_chevron);
        saveButton = findViewById(R.id.button_save_item);
        loadingOverlay = findViewById(R.id.loading_overlay);
        progressBar = findViewById(R.id.progress_bar);
        productImageView = findViewById(R.id.product_image_view);
        barcodeInfoLayout = findViewById(R.id.barcode_info_layout);
        barcodeText = findViewById(R.id.barcode_text);
        gs1InfoLayout = findViewById(R.id.gs1_info_layout);
        gs1InfoText = findViewById(R.id.gs1_info_text);
        addImageButton = findViewById(R.id.add_image_button);
        removeImageButton = findViewById(R.id.remove_image_button);
        buttonScan = findViewById(R.id.button_scan);
        buttonDecrement = findViewById(R.id.button_decrement);
        buttonIncrement = findViewById(R.id.button_increment);

        inputName.addTextChangedListener(new TextCapitalizationWatcher());
    }

    private void setupClickListeners() {
        MaterialButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        buttonDecrement.setOnClickListener(v -> {
            if (quantity > 1) quantity--;
            qtyText.setText(String.valueOf(quantity));
        });

        buttonIncrement.setOnClickListener(v -> {
            quantity++;
            qtyText.setText(String.valueOf(quantity));
        });

        MaterialCardView categoryContainer = findViewById(R.id.category_container);
        categoryContainer.setOnClickListener(v -> showCategoryDialog());

        MaterialCardView purchaseDateContainer = findViewById(R.id.purchase_date_container);
        purchaseDateContainer.setOnClickListener(v -> showDatePicker(true));

        MaterialCardView expiryDateContainer = findViewById(R.id.expiry_date_container);
        expiryDateContainer.setOnClickListener(v -> showDatePicker(false));

        weightUnitSelector.setOnClickListener(v -> showWeightUnitDialog());

        MaterialCardView storageLocationContainer = findViewById(R.id.storage_location_container);
        storageLocationContainer.setOnClickListener(v -> showStorageLocationDialog());

        additionalInfoHeader.setOnClickListener(v -> toggleAdditionalInfo());

        // Barcode scanner - Coming Soon
        buttonScan.setOnClickListener(v ->
                Toast.makeText(this, "Barcode Scanner — Coming Soon!", Toast.LENGTH_SHORT).show()
        );

        saveButton.setOnClickListener(v -> saveItemToFirestore());

        ImageView clearBarcodeButton = findViewById(R.id.clear_barcode_button);
        clearBarcodeButton.setOnClickListener(v -> clearBarcodeInfo());

        addImageButton.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        removeImageButton.setOnClickListener(v -> removeImage());
    }

    private void toggleAdditionalInfo() {
        isAdditionalInfoExpanded = !isAdditionalInfoExpanded;
        additionalInfoContent.setVisibility(isAdditionalInfoExpanded ? View.VISIBLE : View.GONE);
        additionalInfoChevron.setRotation(isAdditionalInfoExpanded ? 180f : 0f);
    }

    private void showWeightUnitDialog() {
        String[] units = {"kg", "g", "lb", "oz"};
        int currentIndex = java.util.Arrays.asList(units).indexOf(selectedWeightUnit);
        if (currentIndex < 0) currentIndex = 0;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Weight Unit")
                .setSingleChoiceItems(units, currentIndex, (dialog, which) -> {
                    selectedWeightUnit = units[which];
                    textWeightUnit.setText(selectedWeightUnit);
                    dialog.dismiss();
                })
                .show();
    }

    private void showStorageLocationDialog() {
        String[] locations = {"Refrigerator", "Freezer", "Pantry", "Kitchen Cabinet", "Dining Room", "Other"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Storage Location")
                .setItems(locations, (dialog, which) -> {
                    selectedStorageLocation = locations[which];
                    textStorageLocation.setText(selectedStorageLocation);
                    textStorageLocation.setTextColor(getColor(R.color.gray_800));
                })
                .show();
    }

    private void handleImageSelection(Uri uri) {
        try {
            userUploadedImageUri = uri;
            displayImage(uri.toString(), true);
            Toast.makeText(this, "Image added successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("AddItemActivity", "Error loading image: " + e.getMessage());
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeImage() {
        userUploadedImageUri = null;
        productImageUrl = "";
        hideImage();
        Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show();
    }

    private void displayImage(String imageUrl, boolean isUserUploaded) {
        productImageView.setVisibility(View.VISIBLE);
        addImageButton.setVisibility(View.GONE);
        removeImageButton.setVisibility(View.VISIBLE);

        Glide.with(this)
                .load(imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(productImageView);
    }

    private void hideImage() {
        productImageView.setVisibility(View.GONE);
        addImageButton.setVisibility(View.VISIBLE);
        removeImageButton.setVisibility(View.GONE);
    }

    private void displayBarcodeInfo(String barcode, String originalData, String format) {
        barcodeInfoLayout.setVisibility(View.VISIBLE);
        String displayData = (!originalData.equals(barcode) && !originalData.isEmpty())
                ? barcode + "\nOriginal: " + originalData
                : barcode;
        barcodeText.setText(displayData);
    }

    private void displayGS1Info(String expiryDate, String batchLot, String serialNumber,
                                String productionDate, String bestBeforeDate, String gtin) {
        java.util.List<String> gs1Info = new java.util.ArrayList<>();
        if (!expiryDate.isEmpty()) gs1Info.add("Expiry: " + expiryDate);
        if (!bestBeforeDate.isEmpty() && !bestBeforeDate.equals(expiryDate)) gs1Info.add("Best Before: " + bestBeforeDate);
        if (!productionDate.isEmpty()) gs1Info.add("Production: " + productionDate);
        if (!batchLot.isEmpty()) gs1Info.add("Batch: " + batchLot);
        if (!serialNumber.isEmpty()) gs1Info.add("Serial: " + serialNumber);
        if (!gtin.isEmpty()) gs1Info.add("GTIN: " + gtin);

        if (!gs1Info.isEmpty()) {
            gs1InfoLayout.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (String s : gs1Info) sb.append(s).append("\n");
            gs1InfoText.setText(sb.toString().trim());
        } else {
            gs1InfoLayout.setVisibility(View.GONE);
        }
    }

    private void populateProductInfo(String productName, String brands, String suggestedCategory,
                                     String dataSource, boolean isOfflineData) {
        String fullName = !brands.isEmpty() ? productName + " (" + brands + ")" : productName;
        inputName.setText(fullName);

        if (!suggestedCategory.isEmpty() && !"Other".equals(suggestedCategory)) {
            selectedCategory = suggestedCategory;
            textCategory.setText(selectedCategory);
            textCategory.setTextColor(getColor(R.color.gray_800));
        }

        if (isOfflineData) {
            Toast.makeText(this, "Product info loaded from cache (offline mode)", Toast.LENGTH_LONG).show();
        }
    }

    private void clearBarcodeInfo() {
        scannedBarcode = "";
        isGS1Code = false;
        barcodeInfoLayout.setVisibility(View.GONE);
        gs1InfoLayout.setVisibility(View.GONE);

        if (userUploadedImageUri == null) {
            productImageUrl = "";
            hideImage();
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear Scanned Data")
                .setMessage("Do you want to clear all the scanned information as well?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    inputName.setText("");
                    selectedCategory = "";
                    textCategory.setText("Select category");
                    textCategory.setTextColor(getColor(R.color.gray_400));
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void setupInitialValues() {
        qtyText.setText(String.valueOf(quantity));
        String currentDate = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date());
        textPurchaseDate.setText(currentDate);
        selectedPurchaseDate = currentDate;
        textPurchaseDate.setTextColor(getColor(R.color.gray_800));

        barcodeInfoLayout.setVisibility(View.GONE);
        gs1InfoLayout.setVisibility(View.GONE);
        hideImage();
        textWeightUnit.setText(selectedWeightUnit);
    }

    private void showCategoryDialog() {
        String[] categories = {"Dairy", "Meat", "Vegetables", "Fruits", "Bakery", "Frozen", "Beverages", "Cereals", "Sweets", "Other"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Category")
                .setItems(categories, (dialog, which) -> {
                    selectedCategory = categories[which];
                    textCategory.setText(selectedCategory);
                    textCategory.setTextColor(getColor(R.color.gray_800));
                })
                .show();
    }

    private void showDatePicker(boolean isPurchaseDate) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedCalendar = Calendar.getInstance();
                    selectedCalendar.set(year, month, dayOfMonth);
                    String selectedDate = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            .format(selectedCalendar.getTime());

                    if (isPurchaseDate) {
                        selectedPurchaseDate = selectedDate;
                        textPurchaseDate.setText(selectedDate);
                        textPurchaseDate.setTextColor(getColor(R.color.gray_800));
                    } else {
                        selectedExpiryDate = selectedDate;
                        textExpiryDate.setText(selectedDate);
                        textExpiryDate.setTextColor(getColor(R.color.gray_800));
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        if (!isPurchaseDate) {
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
        }
        datePickerDialog.show();
    }

    private void saveItemToFirestore() {
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

        showLoading(true);

        if (userUploadedImageUri != null) {
            CloudinaryManager.uploadProductImage(
                    this,
                    userUploadedImageUri,
                    cloudinaryUrl -> {
                        Log.d("AddItemActivity", "Image uploaded to Cloudinary: " + cloudinaryUrl);
                        saveItemWithImageUrl(cloudinaryUrl);
                    },
                    error -> {
                        Log.e("AddItemActivity", "Cloudinary upload failed: " + error);
                        showLoading(false);
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Image Upload Failed")
                                .setMessage("Failed to upload image: " + error + "\n\nDo you want to save the item without the image?")
                                .setPositiveButton("Save Without Image", (dialog, which) -> {
                                    showLoading(true);
                                    saveItemWithImageUrl(productImageUrl);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
            );
        } else {
            saveItemWithImageUrl(productImageUrl);
        }
    }

    private void saveItemWithImageUrl(String imageUrl) {
        String itemName = inputName.getText().toString().trim();
        if (auth.getCurrentUser() == null) return;

        String amount = inputAmount.getText().toString().trim();
        String weight = inputWeight.getText().toString().trim();
        String notes = inputNotes.getText().toString().trim();

        int daysLeft = calculateDaysLeft(selectedExpiryDate);
        String status = determineStatus(daysLeft);
        Date now = new Date();

        Log.d("AddItemActivity", "Saving item for userId: " + auth.getCurrentUser().getUid());

        GroceryItem groceryItem = new GroceryItem(
                itemName,
                selectedCategory,
                selectedExpiryDate,
                selectedPurchaseDate,
                quantity,
                amount,
                weight,
                !weight.isEmpty() ? selectedWeightUnit : "",
                selectedStorageLocation,
                notes,
                status,
                daysLeft,
                scannedBarcode,
                imageUrl,
                isGS1Code,
                now,
                now
        );

        executor.execute(() -> {
            try {
                firestoreRepository.addGroceryItemAsync(groceryItem, success -> {
                    runOnUiThread(() -> {
                        showLoading(false);
                        if (success) {
                            Log.d("AddItemActivity", "Item saved successfully: " + itemName);
                            Toast.makeText(this, "Item saved successfully!", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(this, "Failed to save item", Toast.LENGTH_LONG).show();
                        }
                    });
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Log.e("AddItemActivity", "Exception while saving item: " + e.getMessage(), e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(!show);
        saveButton.setAlpha(show ? 0.6f : 1f);
    }

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

            Calendar todayCalendar = Calendar.getInstance();
            todayCalendar.set(Calendar.HOUR_OF_DAY, 0);
            todayCalendar.set(Calendar.MINUTE, 0);
            todayCalendar.set(Calendar.SECOND, 0);
            todayCalendar.set(Calendar.MILLISECOND, 0);

            long diffInMillis = expiryCalendar.getTimeInMillis() - todayCalendar.getTimeInMillis();
            int daysLeft = (int) TimeUnit.MILLISECONDS.toDays(diffInMillis);
            Log.d("AddItemActivity", "Expiry: " + expiryDate + ", Days left: " + daysLeft);
            return daysLeft;
        } catch (Exception e) {
            Log.e("AddItemActivity", "Error calculating days left: " + e.getMessage(), e);
            return 0;
        }
    }

    private String determineStatus(int daysLeft) {
        if (daysLeft < 0) return "expired";
        if (daysLeft <= 3) return "expiring";
        return "fresh";
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (hasUnsavedChanges()) showDiscardChangesDialog();
                else finish();
            }
        });
    }

    private boolean hasUnsavedChanges() {
        return !inputName.getText().toString().trim().isEmpty()
                || !selectedCategory.isEmpty()
                || !selectedExpiryDate.isEmpty()
                || quantity != 1
                || !scannedBarcode.isEmpty()
                || userUploadedImageUri != null
                || !inputAmount.getText().toString().trim().isEmpty()
                || !inputWeight.getText().toString().trim().isEmpty()
                || !selectedStorageLocation.isEmpty()
                || !inputNotes.getText().toString().trim().isEmpty();
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