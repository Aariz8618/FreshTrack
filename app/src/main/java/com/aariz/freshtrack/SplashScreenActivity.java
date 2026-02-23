package com.aariz.freshtrack;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashScreenActivity extends AppCompatActivity {

    private static final String TAG = "SplashScreenActivity";
    private static final long SPLASH_DELAY = 3500L;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_splash);

        auth = FirebaseAuth.getInstance();

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isFirstRun          = prefs.getBoolean("is_first_run", true);
        boolean hasUserLoggedInBefore = prefs.getBoolean("user_logged_in_before", false);

        if (isFirstRun) {
            auth.signOut();
            prefs.edit().putBoolean("is_first_run", false).apply();
            Log.d(TAG, "First run detected - clearing auth state");
        } else if (!hasUserLoggedInBefore) {
            auth.signOut();
            Log.d(TAG, "No previous successful login - clearing auth state");
        }

        new Handler(Looper.getMainLooper()).postDelayed(
                this::navigateToNextScreen, SPLASH_DELAY);
    }

    private void navigateToNextScreen() {
        SharedPreferences onboardingPrefs = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        boolean hasCompletedOnboarding = onboardingPrefs.getBoolean("onboarding_completed", false);

        if (!hasCompletedOnboarding) {
            Log.d(TAG, "First time user - navigating to onboarding");
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        SharedPreferences prefs  = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean hasUserLoggedInBefore = prefs.getBoolean("user_logged_in_before", false);

        if (currentUser != null && currentUser.isEmailVerified() && hasUserLoggedInBefore) {
            Log.d(TAG, "User authenticated: " + currentUser.getEmail());
            startActivity(new Intent(this, DashboardActivity.class));
        } else {
            if (currentUser != null && !hasUserLoggedInBefore) {
                auth.signOut();
            }
            Log.d(TAG, "No authenticated user - going to login");
            startActivity(new Intent(this, LoginActivity.class));
        }
        finish();
    }

    /** Call this after every successful login to persist the logged-in state. */
    public static void markUserLoggedIn(android.content.Context context) {
        context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("user_logged_in_before", true)
                .apply();
    }
}