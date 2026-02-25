package com.aariz.freshtrack;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class UsageStatsFragment extends Fragment {

    private FirestoreRepository firestoreRepository;
    private FirebaseAuth auth;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView tvItemsUsed;
    private TextView tvItemsExpired;
    private TextView tvLegendUsed;
    private TextView tvLegendExpired;
    private TextView tvLegendFresh;
    private TextView tvMonthSummary;
    private TextView tvEfficiencyRate;
    private TextView tvTotalItems;
    private TextView tvWasteSaved;
    private PieChart pieChart;
    private BarChart barChart;

    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_usage_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dateFormatter.setLenient(false);
        auth = FirebaseAuth.getInstance();
        firestoreRepository = new FirestoreRepository();

        initViews(view);
        loadStatistics();
    }

    private void initViews(View view) {
        tvItemsUsed = view.findViewById(R.id.tv_items_used);
        tvItemsExpired = view.findViewById(R.id.tv_items_expired);
        tvLegendUsed = view.findViewById(R.id.tv_legend_used);
        tvLegendExpired = view.findViewById(R.id.tv_legend_expired);
        tvLegendFresh = view.findViewById(R.id.tv_legend_fresh);
        tvMonthSummary = view.findViewById(R.id.tv_month_summary);
        tvEfficiencyRate = view.findViewById(R.id.tv_efficiency_rate);
        tvTotalItems = view.findViewById(R.id.tv_total_items);
        tvWasteSaved = view.findViewById(R.id.tv_waste_saved);
        pieChart = view.findViewById(R.id.pie_chart);
        barChart = view.findViewById(R.id.bar_chart);
    }

    private void loadStatistics() {
        executor.execute(() -> {
            firestoreRepository.getUserGroceryItemsAsync(items -> {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (items != null) {
                        if (items.isEmpty()) {
                            showEmptyState();
                        } else {
                            calculateAndDisplayStatistics(items);
                        }
                    } else {
                        Toast.makeText(requireContext(), "Failed to load statistics", Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    private void showEmptyState() {
        tvItemsUsed.setText("0");
        tvItemsExpired.setText("0");
        tvLegendUsed.setText("Used (0)");
        tvLegendExpired.setText("Expired (0)");
        tvLegendFresh.setText("Fresh (0)");
        tvMonthSummary.setText("Start tracking items to see your statistics!");
        tvEfficiencyRate.setText("0%");
        tvTotalItems.setText("0");
        tvWasteSaved.setText("0");

        setupPieChart(0, 0, 0);
        barChart.clear();
        barChart.invalidate();
    }

    private void calculateAndDisplayStatistics(List<GroceryItem> items) {
        List<GroceryItem> updatedItems = new ArrayList<>();
        for (GroceryItem item : items) {
            int daysLeft = calculateDaysLeft(item.getExpiryDate());
            String actualStatus = determineStatus(daysLeft, item.getStatus());
            updatedItems.add(item.copy(daysLeft, actualStatus));
        }

        int usedCount = 0, expiredCount = 0, freshCount = 0;
        for (GroceryItem item : updatedItems) {
            if ("used".equals(item.getStatus())) usedCount++;
            else if ("expired".equals(item.getStatus())) expiredCount++;
            else freshCount++;
        }

        int totalItems = updatedItems.size();
        int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        int monthUsedCount = 0, monthExpiredCount = 0;
        for (GroceryItem item : updatedItems) {
            if (isFromCurrentMonth(item.getCreatedAt(), currentMonth, currentYear)) {
                if ("used".equals(item.getStatus())) monthUsedCount++;
                if ("expired".equals(item.getStatus())) monthExpiredCount++;
            }
        }

        int nonFreshItems = usedCount + expiredCount;
        int efficiencyRate = nonFreshItems > 0 ? (int) ((float) usedCount / nonFreshItems * 100) : 0;

        List<MonthData> monthlyTrends = calculateMonthlyTrends(updatedItems);

        updateUI(usedCount, expiredCount, freshCount, totalItems,
                monthUsedCount, monthExpiredCount, efficiencyRate, usedCount);
        setupPieChart(usedCount, expiredCount, freshCount);
        setupBarChart(monthlyTrends);
    }

    private List<MonthData> calculateMonthlyTrends(List<GroceryItem> items) {
        Calendar calendar = Calendar.getInstance();
        List<MonthData> monthDataList = new ArrayList<>();

        for (int i = 5; i >= 0; i--) {
            calendar.setTime(new Date());
            calendar.add(Calendar.MONTH, -i);
            int month = calendar.get(Calendar.MONTH);
            int year = calendar.get(Calendar.YEAR);
            String monthName = new SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.getTime());

            int used = 0, expired = 0;
            for (GroceryItem item : items) {
                Calendar itemCal = Calendar.getInstance();
                itemCal.setTime(item.getCreatedAt());
                if (itemCal.get(Calendar.MONTH) == month && itemCal.get(Calendar.YEAR) == year) {
                    if ("used".equals(item.getStatus())) used++;
                    if ("expired".equals(item.getStatus())) expired++;
                }
            }
            monthDataList.add(new MonthData(monthName, used, expired));
        }

        return monthDataList;
    }

    private void setupPieChart(int usedCount, int expiredCount, int freshCount) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        if (usedCount > 0) { entries.add(new PieEntry(usedCount, "Used")); colors.add(Color.parseColor("#FF9800")); }
        if (expiredCount > 0) { entries.add(new PieEntry(expiredCount, "Expired")); colors.add(Color.parseColor("#F44336")); }
        if (freshCount > 0) { entries.add(new PieEntry(freshCount, "Fresh")); colors.add(Color.parseColor("#4CAF50")); }
        if (entries.isEmpty()) { entries.add(new PieEntry(1f, "No Data")); colors.add(Color.parseColor("#E0E0E0")); }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setSliceSpace(2f);

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart));

        pieChart.setData(data);
        pieChart.getDescription().setEnabled(false);
        pieChart.getLegend().setEnabled(false);
        pieChart.setDrawEntryLabels(false);
        pieChart.setUsePercentValues(true);
        pieChart.setRotationEnabled(false);
        pieChart.setHoleColor(Color.TRANSPARENT);
        pieChart.setHoleRadius(0f);
        pieChart.setTransparentCircleRadius(0f);
        pieChart.animateY(1000);
        pieChart.invalidate();
    }

    private void setupBarChart(List<MonthData> monthlyData) {
        if (monthlyData.isEmpty()) { barChart.clear(); barChart.invalidate(); return; }

        ArrayList<BarEntry> usedEntries = new ArrayList<>();
        ArrayList<BarEntry> expiredEntries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        for (int i = 0; i < monthlyData.size(); i++) {
            MonthData data = monthlyData.get(i);
            usedEntries.add(new BarEntry(i, data.used));
            expiredEntries.add(new BarEntry(i, data.expired));
            labels.add(data.month);
        }

        ValueFormatter intFormatter = new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value == 0f ? "" : String.valueOf((int) value);
            }
        };

        BarDataSet usedDataSet = new BarDataSet(usedEntries, "Used");
        usedDataSet.setColor(Color.parseColor("#FF9800"));
        usedDataSet.setValueTextSize(10f);
        usedDataSet.setValueFormatter(intFormatter);

        BarDataSet expiredDataSet = new BarDataSet(expiredEntries, "Expired");
        expiredDataSet.setColor(Color.parseColor("#F44336"));
        expiredDataSet.setValueTextSize(10f);
        expiredDataSet.setValueFormatter(intFormatter);

        BarData barData = new BarData(usedDataSet, expiredDataSet);
        barData.setBarWidth(0.30f);

        barChart.setData(barData);
        barChart.getDescription().setEnabled(false);
        barChart.setFitBars(false);
        barChart.animateY(1000);

        float groupSpace = 0.30f, barSpace = 0.05f;
        if (!labels.isEmpty()) {
            barChart.getXAxis().setAxisMinimum(0f);
            barChart.groupBars(0f, groupSpace, barSpace);
            float groupWidth = barChart.getBarData().getGroupWidth(groupSpace, barSpace);
            barChart.getXAxis().setAxisMaximum(0f + groupWidth * labels.size());
        }

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setGranularity(1f);
        xAxis.setCenterAxisLabels(true);
        xAxis.setDrawGridLines(false);
        xAxis.setTextSize(10f);

        barChart.getAxisLeft().setDrawGridLines(true);
        barChart.getAxisLeft().setGranularity(1f);
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisLeft().setValueFormatter(intFormatter);
        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(true);
        barChart.getLegend().setTextSize(12f);
        barChart.invalidate();
    }

    private void updateUI(int usedCount, int expiredCount, int freshCount, int totalItems,
                          int monthUsedCount, int monthExpiredCount, int efficiencyRate, int itemsSaved) {
        tvItemsUsed.setText(String.valueOf(usedCount));
        tvItemsExpired.setText(String.valueOf(expiredCount));
        tvLegendUsed.setText("Used (" + usedCount + ")");
        tvLegendExpired.setText("Expired (" + expiredCount + ")");
        tvLegendFresh.setText("Fresh (" + freshCount + ")");
        tvMonthSummary.setText("This month: " + monthUsedCount + " items used, " + monthExpiredCount + " expired.");

        String efficiencyText;
        if (efficiencyRate >= 80) efficiencyText = efficiencyRate + "% 🌟";
        else if (efficiencyRate >= 60) efficiencyText = efficiencyRate + "% 👍";
        else if (efficiencyRate >= 40) efficiencyText = efficiencyRate + "% ⚠️";
        else efficiencyText = efficiencyRate + "%";

        tvEfficiencyRate.setText(efficiencyText);
        tvTotalItems.setText(String.valueOf(totalItems));
        tvWasteSaved.setText(String.valueOf(itemsSaved));
    }

    private int calculateDaysLeft(String expiryDate) {
        try {
            Date expiry = dateFormatter.parse(expiryDate);
            if (expiry == null) return 0;

            Calendar expiryCal = Calendar.getInstance();
            expiryCal.setTime(expiry);
            expiryCal.set(Calendar.HOUR_OF_DAY, 0); expiryCal.set(Calendar.MINUTE, 0);
            expiryCal.set(Calendar.SECOND, 0); expiryCal.set(Calendar.MILLISECOND, 0);

            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0); todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0); todayCal.set(Calendar.MILLISECOND, 0);

            return (int) TimeUnit.MILLISECONDS.toDays(expiryCal.getTimeInMillis() - todayCal.getTimeInMillis());
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

    private boolean isFromCurrentMonth(Date date, int currentMonth, int currentYear) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.MONTH) == currentMonth && calendar.get(Calendar.YEAR) == currentYear;
    }

    public void exportStatistics() {
        executor.execute(() -> {
            firestoreRepository.getUserGroceryItemsAsync(items -> {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (items == null) {
                        Toast.makeText(requireContext(), "Failed to export statistics", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<GroceryItem> updatedItems = new ArrayList<>();
                    for (GroceryItem item : items) {
                        int daysLeft = calculateDaysLeft(item.getExpiryDate());
                        String actualStatus = determineStatus(daysLeft, item.getStatus());
                        updatedItems.add(item.copy(daysLeft, actualStatus));
                    }

                    int usedCount = 0, expiredCount = 0, freshCount = 0;
                    double savedValue = 0, wastedValue = 0;
                    for (GroceryItem item : updatedItems) {
                        double amount = item.getAmount() != null ? parseAmount(item.getAmount()) : 0.0;
                        if ("used".equals(item.getStatus())) { usedCount++; savedValue += amount; }
                        else if ("expired".equals(item.getStatus())) { expiredCount++; wastedValue += amount; }
                        else freshCount++;
                    }

                    int nonFreshItems = usedCount + expiredCount;
                    StringBuilder shareText = new StringBuilder();
                    shareText.append("📊 My Food Tracking Stats\n")
                            .append("Generated on ")
                            .append(new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date())).append("\n\n")
                            .append("🍽️ Total Items: ").append(updatedItems.size()).append("\n")
                            .append("✅ Items Used: ").append(usedCount).append("\n")
                            .append("❌ Items Expired: ").append(expiredCount).append("\n")
                            .append("🌱 Fresh Items: ").append(freshCount).append("\n\n");

                    if (nonFreshItems > 0) {
                        int efficiency = (int) ((float) usedCount / nonFreshItems * 100);
                        shareText.append("⚡ Efficiency Rate: ").append(efficiency).append("%\n");
                    }
                    if (savedValue > 0) shareText.append("💰 Value Saved: ₹").append(String.format("%.2f", savedValue)).append("\n");
                    if (wastedValue > 0) shareText.append("⚠️ Value Wasted: ₹").append(String.format("%.2f", wastedValue)).append("\n");
                    shareText.append("\nTrack your groceries with Expiry Tracker!");

                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
                    shareIntent.setType("text/plain");
                    startActivity(Intent.createChooser(shareIntent, "Share Statistics"));
                });
            });
        });
    }

    private double parseAmount(String amount) {
        try { return Double.parseDouble(amount); } catch (NumberFormatException e) { return 0.0; }
    }

    public static class MonthData {
        public final String month;
        public final int used;
        public final int expired;

        public MonthData(String month, int used, int expired) {
            this.month = month;
            this.used = used;
            this.expired = expired;
        }
    }
}