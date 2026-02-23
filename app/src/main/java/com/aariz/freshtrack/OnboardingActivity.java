package com.aariz.freshtrack;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.viewpager2.widget.ViewPager2;

import com.aariz.freshtrack.OnboardingAdapter;
import com.aariz.freshtrack.OnboardingItem;

import java.util.Arrays;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private CardView btnNext;
    private TextView btnSkip;
    private View dot1, dot2, dot3;

    private final List<OnboardingItem> onboardingItems = Arrays.asList(
            new OnboardingItem(R.drawable.mascot_waving,
                    "Welcome to FreshTrack!",
                    "Your friendly companion for keeping food fresh."),
            new OnboardingItem(R.drawable.mascot_apple,
                    "Log Your Groceries in Seconds",
                    "Add items effortlessly — your kitchen stays organized."),
            new OnboardingItem(R.drawable.mascot_clock,
                    "Never Miss an Expiry Date",
                    "Get timely alerts before your food goes bad.")
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        initViews();
        setupViewPager();
        setupClickListeners();
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewpager_onboarding);
        btnNext   = findViewById(R.id.button_next);
        btnSkip   = findViewById(R.id.text_skip);
        dot1      = findViewById(R.id.dot_1);
        dot2      = findViewById(R.id.dot_2);
        dot3      = findViewById(R.id.dot_3);
    }

    private void setupViewPager() {
        viewPager.setAdapter(new OnboardingAdapter(onboardingItems));
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateDots(position);
                updateButton(position);
            }
        });
    }

    private void setupClickListeners() {
        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() < onboardingItems.size() - 1) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                finishOnboarding();
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void updateDots(int position) {
        int active   = R.drawable.bg_dot_active;
        int inactive = R.drawable.bg_dot_inactive;
        dot1.setBackgroundResource(position == 0 ? active : inactive);
        dot2.setBackgroundResource(position == 1 ? active : inactive);
        dot3.setBackgroundResource(position == 2 ? active : inactive);
    }

    private void updateButton(int position) {
        TextView textView = btnNext.findViewById(R.id.button_next_text);
        if (textView != null) {
            textView.setText(position == onboardingItems.size() - 1 ? "Get Started" : "Next");
        }
    }

    private void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        prefs.edit().putBoolean("onboarding_completed", true).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}