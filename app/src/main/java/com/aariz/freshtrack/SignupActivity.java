package com.aariz.freshtrack;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupAuth";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private CredentialManager credentialManager;

    private TextView registerButton;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_signup);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);

        EditText firstNameField            = findViewById(R.id.input_first_name);
        EditText lastNameField             = findViewById(R.id.input_last_name);
        EditText emailField                = findViewById(R.id.input_email);
        EditText passwordField             = findViewById(R.id.input_password);
        registerButton                     = findViewById(R.id.button_register);
        ImageView togglePasswordVisibility = findViewById(R.id.toggle_password_visibility);
        LinearLayout googleButton          = findViewById(R.id.button_google);
        TextView signinLink                = findViewById(R.id.text_signin);

        signinLink.setOnClickListener(v -> finish());

        // Toggle password visibility
        togglePasswordVisibility.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_on);
            } else {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_off);
            }
            passwordField.setSelection(passwordField.getText().length());
        });

        registerButton.setOnClickListener(v -> {
            String firstName = firstNameField.getText().toString().trim();
            String lastName  = lastNameField.getText().toString().trim();
            String email     = emailField.getText().toString().trim();
            String password  = passwordField.getText().toString().trim();

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            showEmailGuidanceDialog(firstName, lastName, email, password);
        });

        googleButton.setOnClickListener(v -> signInWithGoogle());
    }

    // --- Google Sign-In via Credential Manager ---

    private void signInWithGoogle() {
        // First try with authorized accounts only (returning users — less friction)
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(getString(R.string.default_web_client_id))
                .setAutoSelectEnabled(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this, request, null, Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleGoogleCredential(result);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        // No returning account found — fall back to show all accounts
                        signInWithGoogleFallback();
                    }
                });
    }

    // Fallback: show all Google accounts for new users
    private void signInWithGoogleFallback() {
        GetSignInWithGoogleOption signInOption = new GetSignInWithGoogleOption
                .Builder(getString(R.string.default_web_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build();

        credentialManager.getCredentialAsync(
                this, request, null, Executors.newSingleThreadExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleGoogleCredential(result);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        Log.e(TAG, "Google Sign-In failed: " + e.getMessage());
                        runOnUiThread(() -> Toast.makeText(SignupActivity.this,
                                "Google Sign-In failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void handleGoogleCredential(GetCredentialResponse response) {
        if (response.getCredential() instanceof CustomCredential) {
            CustomCredential credential = (CustomCredential) response.getCredential();
            if (GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                try {
                    GoogleIdTokenCredential googleCredential =
                            GoogleIdTokenCredential.createFrom(credential.getData());
                    firebaseAuthWithGoogle(googleCredential.getIdToken());
                } catch (Exception e) {
                    Log.e(TAG, "Invalid Google ID token", e);
                    runOnUiThread(() -> Toast.makeText(this,
                            "Google Sign-In failed", Toast.LENGTH_SHORT).show());
                }
            }
        } else {
            Log.w(TAG, "Unexpected credential type");
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInWithCredential:success");
                        FirebaseUser user = auth.getCurrentUser();
                        if (user == null) return;

                        boolean isNewUser = task.getResult().getAdditionalUserInfo() != null
                                && Boolean.TRUE.equals(task.getResult().getAdditionalUserInfo().isNewUser());

                        String displayName = user.getDisplayName() != null ? user.getDisplayName() : "";
                        String[] parts   = displayName.split(" ", 2);
                        String firstName = parts.length > 0 ? parts[0] : "";
                        String lastName  = parts.length > 1 ? parts[1] : "";

                        saveUserToFirestore(
                                user.getUid(), firstName, lastName,
                                user.getEmail() != null ? user.getEmail() : "",
                                () -> proceedToApp(isNewUser));
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(this,
                                "Authentication failed: " + (task.getException() != null
                                        ? task.getException().getMessage() : ""),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --- Email / Password Registration ---

    private void showEmailGuidanceDialog(String firstName, String lastName, String email, String password) {
        new AlertDialog.Builder(this)
                .setTitle("Email Verification Required")
                .setMessage("A verification email will be sent to:\n" + email
                        + "\n\nCheck your Inbox, Spam/Junk, or Promotions tab.\nEmail may take 2–5 minutes to arrive.")
                .setPositiveButton("Continue & Create Account",
                        (d, w) -> proceedWithAccountCreation(firstName, lastName, email, password))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void proceedWithAccountCreation(String firstName, String lastName, String email, String password) {
        setRegisterLoading(true);
        createAccountWithEmail(firstName, lastName, email, password);
    }

    private void createAccountWithEmail(String firstName, String lastName, String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser firebaseUser = auth.getCurrentUser();
                        if (firebaseUser != null) {
                            saveUserToFirestore(
                                    firebaseUser.getUid(), firstName, lastName, email,
                                    () -> sendVerificationEmail(firebaseUser, email));
                        } else {
                            showError("Account creation failed. Please try again.");
                        }
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        showError(mapRegistrationError(task.getException()));
                    }
                });
    }

    private void sendVerificationEmail(FirebaseUser user, String email) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        auth.signOut();
                        showEmailSentDialog(email);
                    } else {
                        Log.w(TAG, "sendEmailVerification failed", task.getException());
                        new AlertDialog.Builder(this)
                                .setTitle("Account Created")
                                .setMessage("Account created but verification email failed to send. Request a new one from the login screen.")
                                .setPositiveButton("Go to Login", (d, w) -> finish())
                                .setCancelable(false)
                                .show();
                    }
                });
    }

    private void showEmailSentDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Account Created Successfully!")
                .setMessage("Verification email sent to:\n" + email
                        + "\n\nCheck your Inbox, Spam/Junk, or Promotions tab.\nEmail may take 2–5 minutes to arrive.")
                .setPositiveButton("Got It!", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    // --- Firestore ---

    private void saveUserToFirestore(String uid, String firstName, String lastName, String email, Runnable onComplete) {
        Map<String, Object> data = new HashMap<>();
        data.put("firstName", firstName);
        data.put("lastName", lastName);
        data.put("email", email);
        data.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    Log.d(TAG, "User profile saved to Firestore");
                    onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save profile: " + e.getMessage());
                    onComplete.run(); // proceed even if Firestore save fails
                });
    }

    private void proceedToApp(boolean isNewUser) {
        SplashScreenActivity.markUserLoggedIn(this);
        String message = isNewUser ? "Account created successfully!" : "Welcome back!";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }

    // --- Helpers ---

    private void setRegisterLoading(boolean loading) {
        registerButton.setEnabled(!loading);
        registerButton.setClickable(!loading);
        registerButton.setAlpha(loading ? 0.6f : 1f);
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        setRegisterLoading(false);
    }

    private String mapRegistrationError(Exception e) {
        if (e == null) return "Registration failed.";
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("email address is already in use"))
            return "This email is already registered. Please login instead.";
        if (msg.contains("email address is badly formatted"))
            return "Please enter a valid email address.";
        if (msg.contains("weak password"))
            return "Password is too weak. Please use a stronger password.";
        if (msg.contains("network error"))
            return "Network error. Please check your internet connection.";
        return "Registration failed: " + msg;
    }

    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
}