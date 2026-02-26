package com.aariz.freshtrack;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    // UI components
    private MaterialButton btnBack;
    private TextView tvAvatarInitial;
    private ImageView profileImageView;
    private TextView btnChangePhoto;
    private EditText etName;
    private EditText etEmail;
    private EditText etPhone;
    private Button btnSave;
    private Button btnCancel;

    // Original data
    private String originalName = "";
    private String originalEmail = "";
    private String originalPhone = "";
    private String currentProfileImageUrl = "";

    // Email verification state
    private boolean emailVerificationSent = false;
    private String newEmailPending = "";

    // Image handling
    private Uri selectedImageUri = null;
    private String currentPhotoPath = "";

    // ─────────────────────────────────────────────────────────
    //  Permission & activity launchers
    // ─────────────────────────────────────────────────────────

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openGallery();
                } else {
                    Toast.makeText(this, "Storage permission is required", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    File file = new File(currentPhotoPath);
                    if (file.exists()) {
                        selectedImageUri = Uri.fromFile(file);
                        displaySelectedImage(selectedImageUri);
                    }
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        selectedImageUri = uri;
                        displaySelectedImage(uri);
                    }
                }
            });

    // ─────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_edit_profile);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));

        CloudinaryManager.init(this);

        initViews();
        setupClickListeners();
        loadUserData();
        setupAuthStateListener();
    }

    // ─────────────────────────────────────────────────────────
    //  Init
    // ─────────────────────────────────────────────────────────

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        tvAvatarInitial = findViewById(R.id.tv_avatar_initial);
        btnChangePhoto = findViewById(R.id.btn_change_photo);
        etName = findViewById(R.id.et_name);
        etEmail = findViewById(R.id.et_email);
        etPhone = findViewById(R.id.et_phone);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
        profileImageView = findViewById(R.id.profile_image_view);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());
        btnChangePhoto.setOnClickListener(v -> showImagePickerDialog());
        btnSave.setOnClickListener(v -> saveProfile());
    }

    // ─────────────────────────────────────────────────────────
    //  Image picker
    // ─────────────────────────────────────────────────────────

    private void showImagePickerDialog() {
        String[] options = {"Take Photo", "Choose from Gallery", "Remove Photo"};
        new AlertDialog.Builder(this)
                .setTitle("Change Profile Picture")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) checkCameraPermissionAndOpen();
                    else if (which == 1) checkStoragePermissionAndOpen();
                    else removeProfilePicture();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void checkStoragePermissionAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                storagePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    private void openCamera() {
        try {
            File photoFile = createImageFile();
            Uri photoUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    photoFile
            );
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            cameraLauncher.launch(intent);
        } catch (IOException e) {
            Toast.makeText(this, "Error creating image file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private File createImageFile() throws IOException {
        String timeStamp = String.valueOf(System.currentTimeMillis());
        File storageDir = getExternalCacheDir();
        File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void displaySelectedImage(Uri uri) {
        tvAvatarInitial.setVisibility(View.GONE);
        profileImageView.setVisibility(View.VISIBLE);
        Glide.with(this).load(uri).circleCrop().into(profileImageView);
    }

    private void removeProfilePicture() {
        selectedImageUri = null;
        currentProfileImageUrl = "";
        tvAvatarInitial.setVisibility(View.VISIBLE);
        profileImageView.setVisibility(View.GONE);
        Toast.makeText(this, "Profile picture will be removed", Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────
    //  Load user data
    // ─────────────────────────────────────────────────────────

    private void setupAuthStateListener() {
        auth.addAuthStateListener(firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null && emailVerificationSent && !newEmailPending.isEmpty()) {
                user.reload().addOnCompleteListener(reloadTask -> {
                    if (reloadTask.isSuccessful() && user.isEmailVerified()) {
                        Toast.makeText(this, "Email verified! Updating profile...", Toast.LENGTH_SHORT).show();
                        completeProfileUpdate("", "", "", currentProfileImageUrl);
                    }
                });
            }
        });
    }

    private void loadUserData() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            originalEmail = currentUser.getEmail() != null ? currentUser.getEmail() : "";
            originalName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "";

            etEmail.setText(originalEmail);
            etName.setText(originalName);
            updateAvatarInitial(originalName);

            loadFirestoreData(currentUser.getUid());
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadFirestoreData(String userId) {
        firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        originalPhone = document.getString("phone") != null
                                ? document.getString("phone") : "";
                        etPhone.setText(originalPhone);

                        currentProfileImageUrl = document.getString("profileImageUrl") != null
                                ? document.getString("profileImageUrl") : "";

                        if (!currentProfileImageUrl.isEmpty()) {
                            tvAvatarInitial.setVisibility(View.GONE);
                            profileImageView.setVisibility(View.VISIBLE);
                            Glide.with(this).load(currentProfileImageUrl)
                                    .circleCrop().into(profileImageView);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error loading profile data: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void updateAvatarInitial(String name) {
        String initial = (!name.isEmpty())
                ? String.valueOf(Character.toUpperCase(name.charAt(0)))
                : "U";
        tvAvatarInitial.setText(initial);
    }

    // ─────────────────────────────────────────────────────────
    //  Save profile flow
    // ─────────────────────────────────────────────────────────

    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            etName.requestFocus();
            return;
        }
        if (email.isEmpty()) {
            Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show();
            etEmail.requestFocus();
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            etEmail.requestFocus();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            if (selectedImageUri != null) {
                uploadProfileImage(name, email, phone);
            } else if (!email.equals(originalEmail)) {
                showEmailChangeConfirmation(name, email, phone, currentProfileImageUrl);
            } else {
                updateProfile(name, email, phone, currentProfileImageUrl);
            }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadProfileImage(String name, String email, String phone) {
        btnSave.setText("Uploading image...");
        btnSave.setEnabled(false);

        CloudinaryManager.uploadImage(this, selectedImageUri, new CloudinaryManager.UploadListener() {
            @Override
            public void onSuccess(String imageUrl) {
                runOnUiThread(() -> {
                    if (!email.equals(originalEmail)) {
                        showEmailChangeConfirmation(name, email, phone, imageUrl);
                    } else {
                        updateProfile(name, email, phone, imageUrl);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(EditProfileActivity.this,
                            "Failed to upload image: " + error, Toast.LENGTH_SHORT).show();
                    resetSaveButton();
                });
            }
        });
    }

    private void showEmailChangeConfirmation(String name, String email, String phone, String imageUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Email Verification Required")
                .setMessage("You're changing your email address to: " + email
                        + "\n\nA verification email will be sent to this new address. "
                        + "You must verify it before the changes can be saved.\n\nProceed?")
                .setPositiveButton("Send Verification", (dialog, which) ->
                        sendEmailVerification(name, email, phone, imageUrl))
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    resetSaveButton();
                })
                .show();
    }

    private void sendEmailVerification(String name, String email, String phone, String imageUrl) {
        btnSave.setText("Sending verification...");
        btnSave.setEnabled(false);

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            newEmailPending = email;
            currentUser.verifyBeforeUpdateEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            emailVerificationSent = true;
                            showVerificationPendingDialog(name, email, phone, imageUrl);
                        } else {
                            String msg = task.getException() != null
                                    ? task.getException().getMessage() : "Unknown error";
                            Toast.makeText(this, "Failed to send verification: " + msg,
                                    Toast.LENGTH_LONG).show();
                            resetSaveButton();
                        }
                    });
        }
    }

    private void showVerificationPendingDialog(String name, String email, String phone, String imageUrl) {
        new AlertDialog.Builder(this)
                .setTitle("Verification Email Sent")
                .setMessage("A verification email has been sent to: " + email
                        + "\n\nPlease check your email and click the verification link. "
                        + "Once verified, return to this screen and tap 'Save Changes' again.")
                .setPositiveButton("I've Verified", (dialog, which) ->
                        checkEmailVerificationAndSave(name, email, phone, imageUrl))
                .setNegativeButton("Cancel", (dialog, which) -> {
                    etEmail.setText(originalEmail);
                    newEmailPending = "";
                    emailVerificationSent = false;
                    resetSaveButton();
                })
                .setCancelable(false)
                .show();
    }

    private void checkEmailVerificationAndSave(String name, String email, String phone, String imageUrl) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            btnSave.setText("Verifying...");
            btnSave.setEnabled(false);

            currentUser.reload().addOnCompleteListener(reloadTask -> {
                if (reloadTask.isSuccessful()) {
                    if (currentUser.isEmailVerified()) {
                        completeProfileUpdate(name, email, phone, imageUrl);
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("Email Not Verified")
                                .setMessage("Your email address has not been verified yet. "
                                        + "Please check your email and click the verification link, then try again.")
                                .setPositiveButton("Try Again", (d, w) ->
                                        checkEmailVerificationAndSave(name, email, phone, imageUrl))
                                .setNegativeButton("Cancel", (d, w) -> resetSaveButton())
                                .show();
                    }
                } else {
                    Toast.makeText(this, "Failed to check verification status", Toast.LENGTH_SHORT).show();
                    resetSaveButton();
                }
            });
        }
    }

    private void completeProfileUpdate(String name, String email, String phone, String imageUrl) {
        String finalName = name.isEmpty() ? etName.getText().toString().trim() : name;
        String finalEmail = email.isEmpty() ? etEmail.getText().toString().trim() : email;
        String finalPhone = phone.isEmpty() ? etPhone.getText().toString().trim() : phone;
        updateProfile(finalName, finalEmail, finalPhone, imageUrl);
    }

    private void updateProfile(String name, String email, String phone, String imageUrl) {
        btnSave.setText("Saving...");
        btnSave.setEnabled(false);

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            resetSaveButton();
            return;
        }

        UserProfileChangeRequest.Builder builder = new UserProfileChangeRequest.Builder()
                .setDisplayName(name);
        if (!imageUrl.isEmpty()) {
            builder.setPhotoUri(Uri.parse(imageUrl));
        }

        currentUser.updateProfile(builder.build())
                .addOnCompleteListener(profileTask -> {
                    if (profileTask.isSuccessful()) {
                        updateFirestoreProfile(currentUser.getUid(), name, email, phone, imageUrl);
                    } else {
                        String msg = profileTask.getException() != null
                                ? profileTask.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, "Failed to update profile: " + msg,
                                Toast.LENGTH_SHORT).show();
                        resetSaveButton();
                    }
                });
    }

    private void updateFirestoreProfile(String userId, String name, String email,
                                        String phone, String imageUrl) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("name", name);
        userProfile.put("email", email);
        userProfile.put("phone", phone);
        userProfile.put("profileImageUrl", imageUrl);
        userProfile.put("updatedAt", Timestamp.now());

        firestore.collection("users").document(userId)
                .set(userProfile, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();

                    originalName = name;
                    originalEmail = email;
                    originalPhone = phone;
                    currentProfileImageUrl = imageUrl;
                    emailVerificationSent = false;
                    newEmailPending = "";

                    updateAvatarInitial(name);
                    resetSaveButton();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    resetSaveButton();
                });
    }

    private void resetSaveButton() {
        btnSave.setText("Save Changes");
        btnSave.setEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        emailVerificationSent = false;
        newEmailPending = "";
    }
}