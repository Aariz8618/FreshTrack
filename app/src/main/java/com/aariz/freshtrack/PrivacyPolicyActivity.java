package com.aariz.freshtrack;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class PrivacyPolicyActivity extends AppCompatActivity {

    private MaterialButton buttonBack;
    private MaterialCardView contactCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_privacy_policy);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        buttonBack = findViewById(R.id.button_back);
        contactCard = findViewById(R.id.contact_card);
    }

    private void setupClickListeners() {
        buttonBack.setOnClickListener(v -> finish());
        contactCard.setOnClickListener(v -> openEmailClient());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void openEmailClient() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"freshtrack@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Privacy Policy Inquiry - Grocery Tracker");

        try {
            startActivity(intent);
        } catch (Exception e) {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("message/rfc822");
            sendIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"freshtrack@gmail.com"});
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Privacy Policy Inquiry - Grocery Tracker");
            try {
                startActivity(Intent.createChooser(sendIntent, "Send Email"));
            } catch (Exception ex) {
                // Handle case where no email app exists
            }
        }
    }
}