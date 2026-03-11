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

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginAuth";

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private CredentialManager credentialManager;

    private TextView loginButton;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_login);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
        credentialManager = CredentialManager.create(this);

        EditText emailField                = findViewById(R.id.input_email);
        EditText passwordField             = findViewById(R.id.input_password);
        loginButton                        = findViewById(R.id.button_login);
        TextView forgotPassword            = findViewById(R.id.text_forgot_password);
        ImageView togglePasswordVisibility = findViewById(R.id.toggle_password_visibility);
        LinearLayout googleButton          = findViewById(R.id.button_google);
        TextView signupLink                = findViewById(R.id.text_signup);

        signupLink.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));

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

        loginButton.setOnClickListener(v -> {
            String email    = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }
            setLoginLoading(true);
            signInWithEmail(email, password);
        });

        forgotPassword.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }
            sendPasswordResetEmail(email);
        });

        googleButton.setOnClickListener(v -> signInWithGoogle());
    }

    // --- Email / Password Sign-In ---

    private void signInWithEmail(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null && user.isEmailVerified()) {
                            Log.d(TAG, "signInWithEmail:success");
                            SplashScreenActivity.markUserLoggedIn(this);
                            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(this, DashboardActivity.class));
                            finish();
                        } else {
                            Toast.makeText(this,
                                    "Please verify your email first. Check your inbox for the verification link.",
                                    Toast.LENGTH_LONG).show();
                            auth.signOut();
                            if (user != null) showResendVerificationDialog(email, password);
                            setLoginLoading(false);
                        }
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(this, mapAuthError(task.getException()), Toast.LENGTH_LONG).show();
                        setLoginLoading(false);
                    }
                });
    }

    private void showResendVerificationDialog(String email, String password) {
        new AlertDialog.Builder(this)
                .setTitle("Email Not Verified")
                .setMessage("Would you like us to resend the verification email to " + email + "?")
                .setPositiveButton("Resend", (d, w) -> resendEmailVerification(email, password))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void resendEmailVerification(String email, String password) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            user.sendEmailVerification()
                                    .addOnCompleteListener(emailTask -> {
                                        if (emailTask.isSuccessful()) {
                                            Toast.makeText(this,
                                                    "Verification email resent to " + email + ". Check your inbox and spam folder.",
                                                    Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(this,
                                                    "Failed to resend: " + emailTask.getException().getMessage(),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                        auth.signOut();
                                    });
                        }
                    } else {
                        Toast.makeText(this,
                                "Failed to authenticate. Please check your password.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendPasswordResetEmail(String email) {
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Password reset email sent to " + email + ". Check your inbox.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "";
                        String error = (msg != null && msg.contains("no user record"))
                                ? "No account found with this email address."
                                : "Failed to send reset email: " + msg;
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    }
                });
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

    // Fallback: show all Google accounts on device for new users
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
                        runOnUiThread(() -> Toast.makeText(LoginActivity.this,
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

                        if (isNewUser) {
                            String displayName = user.getDisplayName() != null ? user.getDisplayName() : "";
                            String[] parts   = displayName.split(" ", 2);
                            String firstName = parts.length > 0 ? parts[0] : "";
                            String lastName  = parts.length > 1 ? parts[1] : "";

                            // Force token refresh before writing to Firestore to avoid
                            // PERMISSION_DENIED race condition on fresh account creation
                            user.getIdToken(true)
                                    .addOnSuccessListener(tokenResult -> {
                                        Log.d(TAG, "Token refreshed successfully, saving to Firestore");
                                        saveUserToFirestore(
                                                user.getUid(), firstName, lastName,
                                                user.getEmail() != null ? user.getEmail() : "",
                                                this::proceedToApp);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.w(TAG, "Token refresh failed, attempting Firestore write anyway: " + e.getMessage());
                                        saveUserToFirestore(
                                                user.getUid(), firstName, lastName,
                                                user.getEmail() != null ? user.getEmail() : "",
                                                this::proceedToApp);
                                    });
                        } else {
                            proceedToApp();
                        }
                    } else {
                        Log.w(TAG, "signInWithCredential:failure", task.getException());
                        Toast.makeText(this,
                                "Authentication failed: " + (task.getException() != null
                                        ? task.getException().getMessage() : ""),
                                Toast.LENGTH_SHORT).show();
                    }
                });
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

    // --- Helpers ---

    private void proceedToApp() {
        SplashScreenActivity.markUserLoggedIn(this);
        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }

    private void setLoginLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "Signing In..." : "Log In");
        loginButton.setAlpha(loading ? 0.6f : 1f);
    }

    private String mapAuthError(Exception e) {
        if (e == null) return "Login failed.";
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("no user record") || msg.contains("INVALID_LOGIN_CREDENTIALS"))
            return "Invalid email or password. Please try again.";
        if (msg.contains("password is invalid") || msg.contains("wrong password"))
            return "Incorrect password. Please try again.";
        if (msg.contains("email address is badly formatted"))
            return "Please enter a valid email address.";
        if (msg.contains("too many requests"))
            return "Too many failed attempts. Please try again later.";
        if (msg.contains("network error"))
            return "Network error. Please check your internet connection.";
        return "Login failed: " + msg;
    }

    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }
}