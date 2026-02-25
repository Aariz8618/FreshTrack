package com.aariz.freshtrack;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {

    private FirebaseAuth auth;
    private TextView textUserName;
    private TextView textUserEmail;
    private MaterialButton buttonViewProfile;
    private LinearLayout rowNotifications;
    private LinearLayout rowPermissions;
    private LinearLayout rowAbout;
    private LinearLayout rowPrivacy;
    private LinearLayout rowSupport;
    private ImageView iconPermissionsChevron;
    private LinearLayout layoutPermissionsExpanded;
    private boolean isPermissionsExpanded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        auth = FirebaseAuth.getInstance();

        View headerSection = view.findViewById(R.id.header_section);
        if (headerSection != null) {
            WindowInsetsExtensions.applyHeaderInsets(headerSection);
        }

        initViews(view);
        setupClickListeners();
        loadUserInfo();
    }

    private void initViews(View view) {
        textUserName = view.findViewById(R.id.text_user_name);
        textUserEmail = view.findViewById(R.id.text_user_email);
        buttonViewProfile = view.findViewById(R.id.button_view_profile);
        rowNotifications = view.findViewById(R.id.row_notifications);
        rowPermissions = view.findViewById(R.id.row_permissions);
        rowAbout = view.findViewById(R.id.row_about);
        rowPrivacy = view.findViewById(R.id.row_privacy);
        rowSupport = view.findViewById(R.id.row_support);
        iconPermissionsChevron = view.findViewById(R.id.icon_permissions_chevron);
        layoutPermissionsExpanded = view.findViewById(R.id.layout_permissions_expanded);
    }

    private void setupClickListeners() {
        buttonViewProfile.setOnClickListener(v -> {
            if (!isAdded()) return;
            startActivity(new Intent(requireContext(), ProfileActivity.class));
        });

        rowNotifications.setOnClickListener(v -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), "Coming Soon!", Toast.LENGTH_SHORT).show();
        });

        rowPermissions.setOnClickListener(v -> togglePermissionsExpanded());

        rowAbout.setOnClickListener(v -> showAboutDialog());

        rowPrivacy.setOnClickListener(v -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), "Coming Soon!", Toast.LENGTH_SHORT).show();
        });

        rowSupport.setOnClickListener(v -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), "Coming Soon!", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadUserInfo() {
        if (!isAdded()) return;

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            textUserName.setText(currentUser.getDisplayName() != null
                    ? currentUser.getDisplayName() : "User");
            textUserEmail.setText(currentUser.getEmail() != null
                    ? currentUser.getEmail() : "No email");
        } else {
            textUserName.setText("Guest");
            textUserEmail.setText("Not logged in");
        }
    }

    private void togglePermissionsExpanded() {
        if (!isAdded()) return;

        isPermissionsExpanded = !isPermissionsExpanded;

        if (isPermissionsExpanded) {
            layoutPermissionsExpanded.setVisibility(View.VISIBLE);
            iconPermissionsChevron.setRotation(180f);
        } else {
            layoutPermissionsExpanded.setVisibility(View.GONE);
            iconPermissionsChevron.setRotation(0f);
        }
    }

    private void showAboutDialog() {
        if (!isAdded()) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("About Expiry Tracker")
                .setMessage(
                        "Version: 1.0.2\n" +
                                "Developer: TechFlow Solutions\n\n" +
                                "Expiry Tracker helps you reduce food waste by tracking expiration dates " +
                                "and sending timely reminders.\n\n" +
                                "© 2025 TechFlow Solutions"
                )
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserInfo();
    }
}