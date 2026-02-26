package com.aariz.freshtrack;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FeedbackActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    // UI components
    private MaterialButton buttonBack;
    private ImageView star1, star2, star3, star4, star5;
    private TextView ratingLabel;
    private RadioGroup radioGroupCategory;
    private RadioButton radioBug, radioSuggestion, radioFeedback;
    private EditText inputMessage, inputEmail;
    private LinearLayout buttonAttachScreenshot;
    private ImageView screenshotPreview;
    private LinearLayout buttonRemoveScreenshot;
    private LinearLayout buttonSubmit;
    private FrameLayout loadingOverlay;

    private int rating = 0;
    private Uri screenshotUri = null;
    private final List<ImageView> stars = new ArrayList<>();

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        screenshotUri = uri;
                        showScreenshotPreview(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_feedback);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));
        WindowInsetsExtensions.applyBottomNavInsets(findViewById(R.id.bottom_bar));

        CloudinaryManager.init(this);

        initViews();
        setupClickListeners();
        loadUserEmail();
    }

    private void initViews() {
        buttonBack = findViewById(R.id.button_back);
        star1 = findViewById(R.id.star_1);
        star2 = findViewById(R.id.star_2);
        star3 = findViewById(R.id.star_3);
        star4 = findViewById(R.id.star_4);
        star5 = findViewById(R.id.star_5);
        ratingLabel = findViewById(R.id.rating_label);
        radioGroupCategory = findViewById(R.id.radio_group_category);
        radioBug = findViewById(R.id.radio_bug);
        radioSuggestion = findViewById(R.id.radio_suggestion);
        radioFeedback = findViewById(R.id.radio_feedback);
        inputMessage = findViewById(R.id.input_message);
        inputEmail = findViewById(R.id.input_email);
        buttonAttachScreenshot = findViewById(R.id.button_attach_screenshot);
        screenshotPreview = findViewById(R.id.screenshot_preview);
        buttonRemoveScreenshot = findViewById(R.id.button_remove_screenshot);
        buttonSubmit = findViewById(R.id.button_submit);
        loadingOverlay = findViewById(R.id.loading_overlay);

        stars.add(star1);
        stars.add(star2);
        stars.add(star3);
        stars.add(star4);
        stars.add(star5);
    }

    private void setupClickListeners() {
        buttonBack.setOnClickListener(v -> finish());

        for (int i = 0; i < stars.size(); i++) {
            final int index = i;
            stars.get(i).setOnClickListener(v -> setRating(index + 1));
        }

        buttonAttachScreenshot.setOnClickListener(v -> openImagePicker());
        buttonRemoveScreenshot.setOnClickListener(v -> removeScreenshot());
        buttonSubmit.setOnClickListener(v -> submitFeedback());
    }

    private void loadUserEmail() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String email = currentUser.getEmail() != null ? currentUser.getEmail() : "";
            inputEmail.setText(email);
        } else {
            inputEmail.setHint("Not logged in");
        }
    }

    private void setRating(int value) {
        rating = value;
        updateStarDisplay();
        updateRatingLabel();
    }

    private void updateStarDisplay() {
        for (int i = 0; i < stars.size(); i++) {
            ImageView star = stars.get(i);
            if (i < rating) {
                star.setImageResource(R.drawable.ic_star_filled);
                star.setImageTintList(getColorStateList(R.color.yellow_500));
            } else {
                star.setImageResource(R.drawable.ic_star_outline);
                star.setImageTintList(getColorStateList(R.color.gray_400));
            }
        }
    }

    private void updateRatingLabel() {
        switch (rating) {
            case 1: ratingLabel.setText("Poor");         break;
            case 2: ratingLabel.setText("Fair");         break;
            case 3: ratingLabel.setText("Good");         break;
            case 4: ratingLabel.setText("Very Good");    break;
            case 5: ratingLabel.setText("Excellent");    break;
            default: ratingLabel.setText("Tap to rate"); break;
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void showScreenshotPreview(Uri uri) {
        screenshotPreview.setImageURI(uri);
        screenshotPreview.setVisibility(View.VISIBLE);
        buttonRemoveScreenshot.setVisibility(View.VISIBLE);
    }

    private void removeScreenshot() {
        screenshotUri = null;
        screenshotPreview.setImageURI(null);
        screenshotPreview.setVisibility(View.GONE);
        buttonRemoveScreenshot.setVisibility(View.GONE);
    }

    private void submitFeedback() {
        if (rating == 0) {
            Toast.makeText(this, "Please provide a rating", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedCategoryId = radioGroupCategory.getCheckedRadioButtonId();
        if (selectedCategoryId == -1) {
            Toast.makeText(this, "Please select a feedback type", Toast.LENGTH_SHORT).show();
            return;
        }

        String message = inputMessage.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter your message", Toast.LENGTH_SHORT).show();
            return;
        }

        String category;
        if (selectedCategoryId == R.id.radio_bug) {
            category = "Bug Report";
        } else if (selectedCategoryId == R.id.radio_suggestion) {
            category = "Suggestion";
        } else {
            category = "General Feedback";
        }

        loadingOverlay.setVisibility(View.VISIBLE);

        if (screenshotUri != null) {
            uploadScreenshotToCloudinary(category, message);
        } else {
            saveFeedbackToFirestore(category, message, null);
        }
    }

    private void uploadScreenshotToCloudinary(String category, String message) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            loadingOverlay.setVisibility(View.GONE);
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        if (screenshotUri == null) return;

        CloudinaryManager.uploadFeedbackScreenshot(
                this,
                screenshotUri,
                new CloudinaryManager.UploadListener() {
                    @Override
                    public void onSuccess(String url) {
                        runOnUiThread(() -> saveFeedbackToFirestore(category, message, url));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            loadingOverlay.setVisibility(View.GONE);
                            Toast.makeText(FeedbackActivity.this,
                                    "Error uploading screenshot: " + errorMessage,
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                }
        );
    }

    private void saveFeedbackToFirestore(String category, String message, String screenshotUrl) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            loadingOverlay.setVisibility(View.GONE);
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        long timestamp = System.currentTimeMillis();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        String formattedDate = dateFormat.format(new Date(timestamp));

        Map<String, Object> feedbackData = new HashMap<>();
        feedbackData.put("userId", currentUser.getUid());
        feedbackData.put("userEmail", currentUser.getEmail() != null ? currentUser.getEmail() : "");
        feedbackData.put("userName", currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Anonymous");
        feedbackData.put("rating", rating);
        feedbackData.put("category", category);
        feedbackData.put("message", message);
        feedbackData.put("screenshotUrl", screenshotUrl != null ? screenshotUrl : "");
        feedbackData.put("timestamp", timestamp);
        feedbackData.put("dateFormatted", formattedDate);
        feedbackData.put("status", "pending");

        firestore.collection("feedback")
                .add(feedbackData)
                .addOnSuccessListener(documentReference -> {
                    loadingOverlay.setVisibility(View.GONE);
                    showSuccessDialog();
                })
                .addOnFailureListener(e -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Error submitting feedback: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showSuccessDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Thank You!")
                .setMessage("Your feedback has been submitted successfully. We appreciate your input and will review it shortly.")
                .setPositiveButton("OK", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }
}