package com.aariz.freshtrack;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class GroceryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_LIST = 0;
    private static final int VIEW_TYPE_GRID = 1;

    public interface OnItemClickListener {
        void onItemClick(GroceryItem item);
    }

    private final List<GroceryItem> items;
    private final OnItemClickListener onItemClick;
    private final boolean isGridView;

    public GroceryAdapter(List<GroceryItem> items, OnItemClickListener onItemClick, boolean isGridView) {
        this.items = items;
        this.onItemClick = onItemClick;
        this.isGridView = isGridView;
    }

    @Override
    public int getItemViewType(int position) {
        return isGridView ? VIEW_TYPE_GRID : VIEW_TYPE_LIST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_GRID) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_grocery_grid, parent, false);
            return new GroceryGridViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_grocery, parent, false);
            return new GroceryListViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof GroceryListViewHolder) {
            ((GroceryListViewHolder) holder).bind(items.get(position));
        } else if (holder instanceof GroceryGridViewHolder) {
            ((GroceryGridViewHolder) holder).bind(items.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ─── List ViewHolder ──────────────────────────────────────────────────────

    class GroceryListViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardView;
        private final TextView itemName;
        private final TextView quantity;
        private final TextView location;
        private final TextView expiryStatus;
        private final LinearLayout expiryStatusBadge;
        private final View statusBorder;
        private final View statusDot;
        private final FrameLayout iconContainer;
        private final TextView itemEmoji;
        private final ImageView itemImage;

        GroceryListViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_grocery_item);
            itemName = itemView.findViewById(R.id.tv_item_name);
            quantity = itemView.findViewById(R.id.tv_quantity);
            location = itemView.findViewById(R.id.tv_location);
            expiryStatus = itemView.findViewById(R.id.tv_expiry_status);
            expiryStatusBadge = itemView.findViewById(R.id.expiry_status_badge);
            statusBorder = itemView.findViewById(R.id.status_border);
            statusDot = itemView.findViewById(R.id.status_dot);
            iconContainer = itemView.findViewById(R.id.icon_container);
            itemEmoji = itemView.findViewById(R.id.tv_item_emoji);
            itemImage = itemView.findViewById(R.id.iv_item_image);
        }

        void bind(GroceryItem item) {
            int actualDaysLeft = calculateDaysLeft(item.getExpiryDate());
            String actualStatus = determineStatus(actualDaysLeft, item.getStatus());

            // Item name with strikethrough if used
            itemName.setText(item.getName());
            if ("used".equals(actualStatus)) {
                itemName.setPaintFlags(itemName.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                itemName.setPaintFlags(itemName.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }

            // Quantity
            String weightUnit = item.getWeightUnit();
            quantity.setText(item.getQuantity() + (!weightUnit.isEmpty() ? " " + weightUnit : " pcs"));

            // Location badge
            String store = item.getStore().isEmpty() ? "Storage" : item.getStore();
            location.setText(store);
            location.setBackgroundResource(getLocationBadgeDrawable(item.getStore()));
            location.setTextColor(ContextCompat.getColor(itemView.getContext(), getLocationTextColor(item.getStore())));

            // Status styling
            switch (actualStatus) {
                case "used":
                    expiryStatus.setText("Used");
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.orange_700));
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_orange);
                    statusDot.setBackgroundResource(R.drawable.status_dot_orange);
                    statusBorder.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.orange_500));
                    iconContainer.setBackgroundResource(R.drawable.icon_bg_orange);
                    break;

                case "expired":
                    int daysAgo = Math.abs(actualDaysLeft);
                    String expiredText;
                    if (daysAgo == 0) expiredText = "Expired today";
                    else if (daysAgo == 1) expiredText = "Expired 1 day ago";
                    else expiredText = "Expired " + daysAgo + " days ago";
                    expiryStatus.setText(expiredText);
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_700));
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_red);
                    statusDot.setBackgroundResource(R.drawable.status_dot_red);
                    statusBorder.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.red_500));
                    iconContainer.setBackgroundResource(R.drawable.icon_bg_red);
                    break;

                case "expiring":
                    String expiringText;
                    if (actualDaysLeft == 0) expiringText = "Expires today";
                    else if (actualDaysLeft == 1) expiringText = "1 day left";
                    else expiringText = actualDaysLeft + " days left";
                    expiryStatus.setText(expiringText);
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.amber_700));
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_amber);
                    statusDot.setBackgroundResource(R.drawable.status_dot_amber);
                    statusBorder.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.amber_500));
                    iconContainer.setBackgroundResource(R.drawable.icon_bg_amber);
                    break;

                default: // fresh
                    expiryStatus.setText(actualDaysLeft + " days left");
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_700));
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_green);
                    statusDot.setBackgroundResource(R.drawable.status_dot_green);
                    statusBorder.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.green_500));
                    iconContainer.setBackgroundResource(R.drawable.icon_bg_green);
                    break;
            }

            setItemIconOrImage(item);

            GroceryItem clickItem = item.copy(actualDaysLeft, actualStatus);
            cardView.setOnClickListener(v -> onItemClick.onItemClick(clickItem));
        }

        private void setItemIconOrImage(GroceryItem item) {
            if (!item.getImageUrl().isEmpty()) {
                itemEmoji.setVisibility(View.GONE);
                itemImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(item.getImageUrl())
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(itemImage);
            } else {
                itemImage.setVisibility(View.GONE);
                itemEmoji.setVisibility(View.VISIBLE);
                itemEmoji.setText(getCategoryEmoji(item.getCategory()));
            }
        }
    }

    // ─── Grid ViewHolder ──────────────────────────────────────────────────────

    class GroceryGridViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardView;
        private final TextView itemName;
        private final TextView quantity;
        private final TextView location;
        private final TextView expiryStatus;
        private final LinearLayout expiryStatusBadge;
        private final View statusBorderTop;
        private final View statusDot;
        private final View topSection;
        private final TextView itemEmoji;
        private final ImageView itemImage;

        GroceryGridViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_grocery_item);
            itemName = itemView.findViewById(R.id.tv_item_name);
            quantity = itemView.findViewById(R.id.tv_quantity);
            location = itemView.findViewById(R.id.tv_location);
            expiryStatus = itemView.findViewById(R.id.tv_expiry_status);
            expiryStatusBadge = itemView.findViewById(R.id.tv_expiry_status_badge);
            statusBorderTop = itemView.findViewById(R.id.status_border_top);
            statusDot = itemView.findViewById(R.id.status_dot);
            topSection = itemView.findViewById(R.id.top_section);
            itemEmoji = itemView.findViewById(R.id.tv_item_emoji);
            itemImage = itemView.findViewById(R.id.iv_item_image);
        }

        void bind(GroceryItem item) {
            int actualDaysLeft = calculateDaysLeft(item.getExpiryDate());
            String actualStatus = determineStatus(actualDaysLeft, item.getStatus());

            // Item name with strikethrough if used
            itemName.setText(item.getName());
            if ("used".equals(actualStatus)) {
                itemName.setPaintFlags(itemName.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                itemName.setPaintFlags(itemName.getPaintFlags() & ~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }

            // Quantity
            String weightUnit = item.getWeightUnit();
            quantity.setText(item.getQuantity() + (!weightUnit.isEmpty() ? " " + weightUnit : " pcs"));

            // Location badge
            String store = item.getStore().isEmpty() ? "Storage" : item.getStore();
            location.setText(store);
            location.setBackgroundResource(getLocationBadgeDrawable(item.getStore()));
            location.setTextColor(ContextCompat.getColor(itemView.getContext(), getLocationTextColor(item.getStore())));

            // Status styling
            switch (actualStatus) {
                case "used":
                    expiryStatus.setText("Used");
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.orange_700));
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_orange);
                    statusDot.setBackgroundResource(R.drawable.status_dot_orange);
                    statusBorderTop.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.orange_500));
                    topSection.setBackgroundResource(R.drawable.top_section_bg_orange);
                    break;

                case "expired":
                    int daysAgo = Math.abs(actualDaysLeft);
                    String expiredText;
                    if (daysAgo == 0) expiredText = "Expired today";
                    else if (daysAgo == 1) expiredText = "Expired 1 day ago";
                    else expiredText = "Expired " + daysAgo + " days ago";
                    expiryStatus.setText(expiredText);
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.red_700));
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_red);
                    statusDot.setBackgroundResource(R.drawable.status_dot_red);
                    statusBorderTop.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.red_500));
                    topSection.setBackgroundResource(R.drawable.top_section_bg_red);
                    break;

                case "expiring":
                    String expiringText;
                    if (actualDaysLeft == 0) expiringText = "Expires today";
                    else if (actualDaysLeft == 1) expiringText = "1 day left";
                    else expiringText = actualDaysLeft + " days left";
                    expiryStatus.setText(expiringText);
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.amber_700));
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_amber);
                    statusDot.setBackgroundResource(R.drawable.status_dot_amber);
                    statusBorderTop.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.amber_500));
                    topSection.setBackgroundResource(R.drawable.top_section_bg_amber);
                    break;

                default: // fresh
                    expiryStatus.setText(actualDaysLeft + " days left");
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.green_700));
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_green);
                    statusDot.setBackgroundResource(R.drawable.status_dot_green);
                    statusBorderTop.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), R.color.green_500));
                    topSection.setBackgroundResource(R.drawable.top_section_bg_green);
                    break;
            }

            setItemIconOrImage(item);

            GroceryItem clickItem = item.copy(actualDaysLeft, actualStatus);
            cardView.setOnClickListener(v -> onItemClick.onItemClick(clickItem));
        }

        private void setItemIconOrImage(GroceryItem item) {
            if (!item.getImageUrl().isEmpty()) {
                itemEmoji.setVisibility(View.GONE);
                itemImage.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(item.getImageUrl())
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(itemImage);
            } else {
                itemImage.setVisibility(View.GONE);
                itemEmoji.setVisibility(View.VISIBLE);
                itemEmoji.setText(getCategoryEmoji(item.getCategory()));
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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

    private int getLocationBadgeDrawable(String location) {
        switch (location.toLowerCase()) {
            case "fridge":   return R.drawable.location_badge_blue;
            case "freezer":  return R.drawable.location_badge_cyan;
            case "pantry":   return R.drawable.location_badge_orange;
            case "counter":  return R.drawable.location_badge_purple;
            default:         return R.drawable.location_badge_blue;
        }
    }

    private int getLocationTextColor(String location) {
        switch (location.toLowerCase()) {
            case "fridge":   return R.color.blue_700;
            case "freezer":  return R.color.cyan_700;
            case "pantry":   return R.color.orange_700;
            case "counter":  return R.color.purple_700;
            default:         return R.color.blue_700;
        }
    }

    private String getCategoryEmoji(String category) {
        switch (category.toLowerCase()) {
            case "fruits":
            case "fruit":      return "🍎";
            case "dairy":      return "🥛";
            case "vegetables":
            case "vegetable":  return "🥕";
            case "meat":       return "🥩";
            case "bakery":     return "🍞";
            case "frozen":     return "🧊";
            case "beverages":  return "🥤";
            case "cereals":    return "🌾";
            case "sweets":     return "🍬";
            default:           return "🛒";
        }
    }
}