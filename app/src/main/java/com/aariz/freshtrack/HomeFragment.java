package com.aariz.freshtrack;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment {

    private RecyclerView recyclerView;
    private RecyclerView recyclerViewGrid;
    private GroceryAdapter listAdapter;
    private GroceryAdapter gridAdapter;
    private LinearLayout emptyState;
    private TextView tvEmptyMessage;
    private TextView greetingText;
    private ImageView profileButton;
    private LinearLayout loadingIndicator;
    private LinearLayout headerSection;
    private AdView adView;

    private ImageView btnListView;
    private ImageView btnGridView;

    private MaterialButton btnAll;
    private MaterialButton btnFresh;
    private MaterialButton btnExpiring;
    private MaterialButton btnExpired;
    private MaterialButton btnUsed;

    private final List<GroceryItem> allGroceryItems = new ArrayList<>();
    private final List<GroceryItem> filteredGroceryItems = new ArrayList<>();
    private String currentFilter = "all";
    private boolean isGridView = false;

    private FirestoreRepository firestoreRepository;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    private boolean hasAskedForNotificationPermission = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private ActivityResultLauncher<Intent> addItemLauncher;
    private ActivityResultLauncher<Intent> itemDetailLauncher;
    private ActivityResultLauncher<Intent> profileLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        Toast.makeText(requireContext(), "Notifications enabled! You'll receive expiry reminders.", Toast.LENGTH_LONG).show();
                        scheduleNotifications();
                    } else {
                        Toast.makeText(requireContext(), "Notifications disabled. You won't receive expiry reminders.", Toast.LENGTH_LONG).show();
                    }
                    saveNotificationPermissionAsked();
                }
        );

        addItemLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        loadGroceryItems();
                    }
                }
        );

        itemDetailLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        boolean itemUpdated = result.getData().getBooleanExtra("item_updated", false);
                        boolean itemDeleted = result.getData().getBooleanExtra("item_deleted", false);
                        if (itemUpdated || itemDeleted) {
                            loadGroceryItems();
                        }
                    }
                }
        );

        profileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (auth.getCurrentUser() == null) {
                        clearUserLoggedInFlag();
                        startActivity(new Intent(requireContext(), LoginActivity.class));
                        requireActivity().finish();
                    } else {
                        setupGreeting();
                        loadProfileImage();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        auth = FirebaseAuth.getInstance();
        firestoreRepository = new FirestoreRepository();
        firestore = FirebaseFirestore.getInstance();

        MobileAds.initialize(requireContext(), initializationStatus -> {});

        initViews(view);
        setupWindowInsets();
        setupAdView();
        setupRecyclerViews();
        setupViewToggle();
        setupFilterButtons();
        setupFab(view);
        setupProfileButton();
        setupGreeting();
        loadGroceryItems();
        checkAndRequestNotificationPermission();
    }

    @Override
    public void onResume() {
        super.onResume();
        adView.resume();
        loadGroceryItems();
        setupGreeting();
        loadProfileImage();
    }

    @Override
    public void onPause() {
        adView.pause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        adView.destroy();
        super.onDestroyView();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recycler_items);
        recyclerViewGrid = view.findViewById(R.id.recycler_items_grid);
        emptyState = view.findViewById(R.id.empty_state);
        tvEmptyMessage = view.findViewById(R.id.tv_empty_message);
        greetingText = view.findViewById(R.id.tv_greeting);
        profileButton = view.findViewById(R.id.iv_profile);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        headerSection = view.findViewById(R.id.header_section);
        adView = view.findViewById(R.id.adView);

        btnListView = view.findViewById(R.id.btn_list_view);
        btnGridView = view.findViewById(R.id.btn_grid_view);

        btnAll = view.findViewById(R.id.btn_all);
        btnFresh = view.findViewById(R.id.btn_fresh);
        btnExpiring = view.findViewById(R.id.btn_expiring);
        btnExpired = view.findViewById(R.id.btn_expired);
        btnUsed = view.findViewById(R.id.btn_used);
    }

    private void setupWindowInsets() {
        InsetUtils.applyHeaderInsets(headerSection);
    }

    private void setupAdView() {
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SharedPreferences prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
            hasAskedForNotificationPermission = prefs.getBoolean("notification_permission_asked", false);

            boolean isGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;

            if (isGranted) {
                scheduleNotifications();
            } else if (!hasAskedForNotificationPermission) {
                showNotificationPermissionDialog();
            }
        } else {
            scheduleNotifications();
        }
    }

    private void showNotificationPermissionDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Enable Notifications")
                .setMessage("Stay informed about items nearing expiry! Enable notifications to receive timely reminders.")
                .setPositiveButton("Enable", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    }
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    saveNotificationPermissionAsked();
                    Toast.makeText(requireContext(), "You can enable notifications later in Settings", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    private void saveNotificationPermissionAsked() {
        requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("notification_permission_asked", true).apply();
    }

    private void scheduleNotifications() {
        NotificationScheduler notificationScheduler = new NotificationScheduler(requireContext());
        notificationScheduler.scheduleExpiryChecks();
    }

    private void setupRecyclerViews() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        listAdapter = new GroceryAdapter(filteredGroceryItems, item -> openItemDetail(item), false);
        recyclerView.setAdapter(listAdapter);

        recyclerViewGrid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        gridAdapter = new GroceryAdapter(filteredGroceryItems, item -> openItemDetail(item), true);
        recyclerViewGrid.setAdapter(gridAdapter);
    }

    private void openItemDetail(GroceryItem item) {
        Intent intent = new Intent(requireContext(), ItemDetailActivity.class);
        intent.putExtra("id", item.getId());
        intent.putExtra("name", item.getName());
        intent.putExtra("category", item.getCategory());
        intent.putExtra("expiryDate", item.getExpiryDate());
        intent.putExtra("purchaseDate", item.getPurchaseDate());
        intent.putExtra("quantity", item.getQuantity());
        intent.putExtra("status", item.getStatus());
        intent.putExtra("daysLeft", item.getDaysLeft());
        intent.putExtra("barcode", item.getBarcode());
        intent.putExtra("imageUrl", item.getImageUrl());
        intent.putExtra("isGS1", item.isGS1());
        itemDetailLauncher.launch(intent);
    }

    private void setupViewToggle() {
        btnListView.setOnClickListener(v -> {
            if (isGridView) {
                isGridView = false;
                updateViewToggleState();
                switchView();
            }
        });

        btnGridView.setOnClickListener(v -> {
            if (!isGridView) {
                isGridView = true;
                updateViewToggleState();
                switchView();
            }
        });

        updateViewToggleState();
    }

    private void updateViewToggleState() {
        if (isGridView) {
            btnGridView.setBackgroundResource(R.drawable.view_toggle_selected_bg);
            btnGridView.setImageTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.white));
            btnListView.setBackgroundResource(android.R.color.transparent);
            btnListView.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.gray_600));
        } else {
            btnListView.setBackgroundResource(R.drawable.view_toggle_selected_bg);
            btnListView.setImageTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.white));
            btnGridView.setBackgroundResource(android.R.color.transparent);
            btnGridView.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.gray_600));
        }
    }

    private void switchView() {
        if (isGridView) {
            recyclerView.setVisibility(View.GONE);
            recyclerViewGrid.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            recyclerViewGrid.setVisibility(View.GONE);
        }
    }

    private void setupFilterButtons() {
        btnAll.setOnClickListener(v -> setFilter("all"));
        btnFresh.setOnClickListener(v -> setFilter("fresh"));
        btnExpiring.setOnClickListener(v -> setFilter("expiring"));
        btnExpired.setOnClickListener(v -> setFilter("expired"));
        btnUsed.setOnClickListener(v -> setFilter("used"));
        updateFilterButtonStates();
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        updateFilterButtonStates();
        applyFilter();
    }

    private void updateFilterButtonStates() {
        resetButtonState(btnAll);
        resetButtonState(btnFresh);
        resetButtonState(btnExpiring);
        resetButtonState(btnExpired);
        resetButtonState(btnUsed);

        switch (currentFilter) {
            case "all": setActiveButtonState(btnAll); break;
            case "fresh": setActiveButtonState(btnFresh); break;
            case "expiring": setActiveButtonState(btnExpiring); break;
            case "expired": setActiveButtonState(btnExpired); break;
            case "used": setActiveButtonState(btnUsed); break;
        }
    }

    private void resetButtonState(MaterialButton button) {
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_800));
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.white));
        button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.green_primary));
    }

    private void setActiveButtonState(MaterialButton button) {
        button.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.green_primary));
        button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.green_primary));
    }

    private void applyFilter() {
        filteredGroceryItems.clear();

        switch (currentFilter) {
            case "all":
                filteredGroceryItems.addAll(allGroceryItems);
                break;
            case "fresh":
                for (GroceryItem item : allGroceryItems) {
                    int daysLeft = calculateDaysLeft(item.getExpiryDate());
                    if ("fresh".equals(determineStatus(daysLeft, item.getStatus()))) {
                        filteredGroceryItems.add(item);
                    }
                }
                break;
            case "expiring":
                for (GroceryItem item : allGroceryItems) {
                    int daysLeft = calculateDaysLeft(item.getExpiryDate());
                    if ("expiring".equals(determineStatus(daysLeft, item.getStatus()))) {
                        filteredGroceryItems.add(item);
                    }
                }
                break;
            case "expired":
                for (GroceryItem item : allGroceryItems) {
                    int daysLeft = calculateDaysLeft(item.getExpiryDate());
                    if ("expired".equals(determineStatus(daysLeft, item.getStatus()))) {
                        filteredGroceryItems.add(item);
                    }
                }
                break;
            case "used":
                for (GroceryItem item : allGroceryItems) {
                    if ("used".equals(item.getStatus())) {
                        filteredGroceryItems.add(item);
                    }
                }
                break;
        }

        sortGroceryItems();
        listAdapter.notifyDataSetChanged();
        gridAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void sortGroceryItems() {
        filteredGroceryItems.sort((a, b) -> {
            String statusA = determineStatus(calculateDaysLeft(a.getExpiryDate()), a.getStatus());
            String statusB = determineStatus(calculateDaysLeft(b.getExpiryDate()), b.getStatus());
            int priorityA = statusPriority(statusA);
            int priorityB = statusPriority(statusB);
            if (priorityA != priorityB) return priorityA - priorityB;
            int daysCompare = Integer.compare(calculateDaysLeft(a.getExpiryDate()), calculateDaysLeft(b.getExpiryDate()));
            if (daysCompare != 0) return daysCompare;
            return a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
        });
    }

    private int statusPriority(String status) {
        switch (status) {
            case "expiring": return 0;
            case "expired": return 1;
            case "fresh": return 2;
            case "used": return 3;
            default: return 4;
        }
    }

    private void setupFab(View view) {
        FloatingActionButton fab = view.findViewById(R.id.fab_add_item);
        if (fab != null) {
            fab.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), AddItemActivity.class);
                addItemLauncher.launch(intent);
            });
        }
    }

    private void setupProfileButton() {
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ProfileActivity.class);
            profileLauncher.launch(intent);
        });
    }

    private void setupGreeting() {
        if (auth.getCurrentUser() == null) return;
        String displayName = auth.getCurrentUser().getDisplayName();
        String email = auth.getCurrentUser().getEmail();

        String greeting;
        if (displayName != null && !displayName.isEmpty()) {
            greeting = "Hello, " + displayName + " 👋";
        } else if (email != null && !email.isEmpty()) {
            greeting = "Hello, " + email.split("@")[0] + " 👋";
        } else {
            greeting = "Hello, User 👋";
        }

        greetingText.setText(greeting);
        loadProfileImage();
    }

    private void loadProfileImage() {
        if (auth.getCurrentUser() == null) return;
        firestore.collection("users").document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String profileImageUrl = document.getString("profileImageUrl");
                        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(profileImageUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.ic_user)
                                    .error(R.drawable.ic_user)
                                    .into(profileButton);
                        } else {
                            setProfileInitial();
                        }
                    } else {
                        setProfileInitial();
                    }
                })
                .addOnFailureListener(e -> setProfileInitial());
    }

    private void setProfileInitial() {
        if (auth.getCurrentUser() == null) return;
        String displayName = auth.getCurrentUser().getDisplayName();
        String email = auth.getCurrentUser().getEmail();

        String initial;
        if (displayName != null && !displayName.isEmpty()) {
            initial = String.valueOf(displayName.charAt(0)).toUpperCase();
        } else if (email != null && !email.isEmpty()) {
            initial = String.valueOf(email.charAt(0)).toUpperCase();
        } else {
            initial = "U";
        }

        int sizeDp = 40;
        int sizePx = (int) (sizeDp * getResources().getDisplayMetrics().density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(ContextCompat.getColor(requireContext(), R.color.green_primary));
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint);

        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(sizePx * 0.5f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        Rect textBounds = new Rect();
        textPaint.getTextBounds(initial, 0, initial.length(), textBounds);
        float textY = sizePx / 2f - textBounds.exactCenterY();

        canvas.drawText(initial, sizePx / 2f, textY, textPaint);
        profileButton.setImageBitmap(bitmap);
    }

    private void loadGroceryItems() {
        showLoading(true);

        executor.execute(() -> {
            try {
                // Using callback-based approach since we're in Java
                firestoreRepository.getUserGroceryItemsAsync(result -> {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        showLoading(false);
                        if (result != null) {
                            allGroceryItems.clear();
                            allGroceryItems.addAll(result);
                            applyFilter();
                        } else {
                            Toast.makeText(requireContext(), "Failed to load items", Toast.LENGTH_LONG).show();
                            updateEmptyState();
                        }
                    });
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    updateEmptyState();
                });
            }
        });
    }

    private void updateEmptyState() {
        if (filteredGroceryItems.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            recyclerViewGrid.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);

            String message;
            switch (currentFilter) {
                case "all": message = "No items added yet"; break;
                case "fresh": message = "No fresh items"; break;
                case "expiring": message = "No items expiring soon"; break;
                case "expired": message = "No expired items"; break;
                case "used": message = "No used items"; break;
                default: message = "No items found"; break;
            }
            tvEmptyMessage.setText(message);
        } else {
            emptyState.setVisibility(View.GONE);
            switchView();
        }
    }

    private void showLoading(boolean show) {
        loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : (!isGridView ? View.VISIBLE : View.GONE));
        recyclerViewGrid.setVisibility(show ? View.GONE : (isGridView ? View.VISIBLE : View.GONE));
        emptyState.setVisibility(View.GONE);
    }

    private int calculateDaysLeft(String expiryDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            sdf.setLenient(false);
            Date expiry = sdf.parse(expiryDate);
            if (expiry == null) return 0;

            Calendar expiryCalendar = Calendar.getInstance();
            expiryCalendar.setTime(expiry);
            expiryCalendar.set(Calendar.HOUR_OF_DAY, 0);
            expiryCalendar.set(Calendar.MINUTE, 0);
            expiryCalendar.set(Calendar.SECOND, 0);
            expiryCalendar.set(Calendar.MILLISECOND, 0);

            Calendar todayCalendar = Calendar.getInstance();
            todayCalendar.set(Calendar.HOUR_OF_DAY, 0);
            todayCalendar.set(Calendar.MINUTE, 0);
            todayCalendar.set(Calendar.SECOND, 0);
            todayCalendar.set(Calendar.MILLISECOND, 0);

            long diffInMillis = expiryCalendar.getTimeInMillis() - todayCalendar.getTimeInMillis();
            return (int) TimeUnit.MILLISECONDS.toDays(diffInMillis);
        } catch (Exception e) {
            return 0;
        }
    }

    private String determineStatus(int daysLeft, String currentStatus) {
        if ("used".equals(currentStatus)) return "used";
        if (daysLeft < 0) return "expired";
        if (daysLeft <= 3) return "expiring";
        return "fresh";
    }

    private void clearUserLoggedInFlag() {
        requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("user_logged_in_before", false).apply();
    }
}