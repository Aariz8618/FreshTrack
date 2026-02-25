package com.aariz.freshtrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    private MaterialButton btnBack;
    private TextView tvAvatarInitial;
    private TextView tvUserName;
    private TextView tvUserEmail;
    private TextView tvName;
    private TextView tvPhoneNumber;
    private TextView tvMemberSince;
    private LinearLayout btnEditProfile;
    private LinearLayout btnChangePassword;
    private LinearLayout btnLogout;
    private ImageView profileImageView;

    private ActivityResultLauncher<Intent> editProfileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        loadUserData();
                    }
                }
        );

        setContentView(R.layout.activity_profile);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View headerSection = findViewById(R.id.header_section);
        InsetUtils.applyHeaderInsets(headerSection);

        initViews();
        setupClickListeners();
        loadUserData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserData();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        tvAvatarInitial = findViewById(R.id.tv_avatar_initial);
        tvUserName = findViewById(R.id.tv_user_name);
        tvUserEmail = findViewById(R.id.tv_user_email);
        tvName = findViewById(R.id.tv_name);
        tvPhoneNumber = findViewById(R.id.tv_phone_number);
        tvMemberSince = findViewById(R.id.tv_member_since);
        btnEditProfile = findViewById(R.id.btn_edit_profile);
        btnChangePassword = findViewById(R.id.btn_change_password);
        btnLogout = findViewById(R.id.btn_logout);
        profileImageView = findViewById(R.id.profile_image_view);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            editProfileLauncher.launch(intent);
        });

        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        btnLogout.setOnClickListener(v -> showLogoutConfirmation());
    }

    private void loadUserData() {
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser != null) {
            String email = currentUser.getEmail() != null ? currentUser.getEmail() : "No email";
            String displayName = currentUser.getDisplayName() != null
                    ? currentUser.getDisplayName()
                    : email.split("@")[0];
            Long creationTime = currentUser.getMetadata() != null
                    ? currentUser.getMetadata().getCreationTimestamp()
                    : null;

            tvUserName.setText(displayName);
            tvName.setText(displayName);
            tvAvatarInitial.setText(displayName.isEmpty() ? "U" : String.valueOf(displayName.charAt(0)).toUpperCase());
            tvUserEmail.setText(email);

            if (creationTime != null) {
                Date date = new Date(creationTime);
                SimpleDateFormat formatter = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
                tvMemberSince.setText(formatter.format(date));
            } else {
                tvMemberSince.setText("Recently joined");
            }

            loadFirestoreData(currentUser.getUid());
        } else {
            Toast.makeText(this, "Please login to view profile", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void loadFirestoreData(String userId) {
        firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String phone = document.getString("phone");
                        tvPhoneNumber.setText((phone != null && !phone.isEmpty()) ? phone : "Not provided");

                        String firestoreName = document.getString("name");
                        if (firestoreName != null && !firestoreName.isEmpty()) {
                            tvUserName.setText(firestoreName);
                            tvName.setText(firestoreName);
                            tvAvatarInitial.setText(firestoreName.isEmpty() ? "U" :
                                    String.valueOf(firestoreName.charAt(0)).toUpperCase());
                        }

                        String profileImageUrl = document.getString("profileImageUrl");
                        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                            tvAvatarInitial.setVisibility(View.GONE);
                            profileImageView.setVisibility(View.VISIBLE);
                            Glide.with(this)
                                    .load(profileImageUrl)
                                    .circleCrop()
                                    .into(profileImageView);
                        } else {
                            tvAvatarInitial.setVisibility(View.VISIBLE);
                            profileImageView.setVisibility(View.GONE);
                        }
                    } else {
                        tvPhoneNumber.setText("Not provided");
                    }
                })
                .addOnFailureListener(e -> {
                    tvPhoneNumber.setText("Error loading data");
                    Toast.makeText(this, "Error loading profile data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showChangePasswordDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setMessage("You will receive an email with instructions to reset your password.")
                .setPositiveButton("Send Email", (dialog, which) -> sendPasswordResetEmail())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void sendPasswordResetEmail() {
        FirebaseUser currentUser = auth.getCurrentUser();
        String email = currentUser != null ? currentUser.getEmail() : null;

        if (email != null) {
            auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Password reset email sent to " + email, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Failed to send reset email: " +
                                    (task.getException() != null ? task.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            Toast.makeText(this, "No email address found", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void performLogout() {
        try {
            clearUserLoggedInFlag();
            auth.signOut();
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "Error logging out: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void clearUserLoggedInFlag() {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("user_logged_in_before", false)
                .apply();
    }
}