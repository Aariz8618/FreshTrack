package com.aariz.freshtrack;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class DashboardActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_container);

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        auth = FirebaseAuth.getInstance();

        // Check authentication
        if (auth.getCurrentUser() == null) {
            clearUserLoggedInFlag();
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        initViews();

        // Load home fragment by default
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }
    }

    private void initViews() {
        bottomNav = findViewById(R.id.bottom_navigation);
        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
                return true;
            } else if (id == R.id.nav_stats) {
                loadFragment(new StatisticsFragment());
                return true;
            } else if (id == R.id.nav_recipes) {
                loadFragment(new RecipesFragment());
                return true;
            } else if (id == R.id.nav_settings) {
                loadFragment(new SettingsFragment());
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth.getCurrentUser() == null) {
            clearUserLoggedInFlag();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void clearUserLoggedInFlag() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("user_logged_in_before", false).apply();
    }
}