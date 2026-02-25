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
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class SavingsTabFragment extends Fragment {

    private FirestoreRepository firestoreRepository;
    private FirebaseAuth auth;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView tvMoneySaved;
    private TextView tvMoneyWasted;
    private TextView tvLegendSaved;
    private TextView tvLegendWasted;
    private TextView tvSavingsSummary;
    private TextView tvItemsUsedCount;
    private TextView tvValueSaved;
    private TextView tvItemsExpiredCount;
    private TextView tvValueWasted;
    private TextView tvAvgItemValue;
    private PieChart pieChartSavings;
    private BarChart barChartSavings;

    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_savings_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dateFormatter.setLenient(false);
        auth = FirebaseAuth.getInstance();
        firestoreRepository = new FirestoreRepository();

        initViews(view);
        loadSavingsData();
    }

    private void initViews(View view) {
        tvMoneySaved = view.findViewById(R.id.tv_money_saved);
        tvMoneyWasted = view.findViewById(R.id.tv_money_wasted);
        tvLegendSaved = view.findViewById(R.id.tv_legend_saved);
        tvLegendWasted = view.findViewById(R.id.tv_legend_wasted);
        tvSavingsSummary = view.findViewById(R.id.tv_savings_summary);
        tvItemsUsedCount = view.findViewById(R.id.tv_items_used_count);
        tvValueSaved = view.findViewById(R.id.tv_value_saved);
        tvItemsExpiredCount = view.findViewById(R.id.tv_items_expired_count);
        tvValueWasted = view.findViewById(R.id.tv_value_wasted);
        tvAvgItemValue = view.findViewById(R.id.tv_avg_item_value);
        pieChartSavings = view.findViewById(R.id.pie_chart_savings);
        barChartSavings = view.findViewById(R.id.bar_chart_savings);
    }

    private void loadSavingsData() {
        executor.execute(() -> {
            firestoreRepository.getUserGroceryItemsAsync(items -> {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (items != null) {
                        if (items.isEmpty()) {
                            showEmptyState();
                        } else {
                            calculateAndDisplaySavings(items);
                        }
                    } else {
                        Toast.makeText(requireContext(), "Failed to load savings data", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    private void showEmptyState() {
        tvMoneySaved.setText("₹0");
        tvMoneyWasted.setText("₹0");
        tvLegendSaved.setText("Saved (₹0)");
        tvLegendWasted.setText("Wasted (₹0)");
        tvSavingsSummary.setText("Start tracking items to see your savings!");
        tvItemsUsedCount.setText("0");
        tvValueSaved.setText("₹0");
        tvItemsExpiredCount.setText("0");
        tvValueWasted.setText("₹0");
        tvAvgItemValue.setText("₹0");

        setupPieChart(0f, 0f);
        barChartSavings.clear();
        barChartSavings.invalidate();
    }

    private void calculateAndDisplaySavings(List<GroceryItem> items) {
        List<GroceryItem> updatedItems = new ArrayList<>();
        for (GroceryItem item : items) {
            int daysLeft = calculateDaysLeft(item.getExpiryDate());
            String actualStatus = determineStatus(daysLeft, item.getStatus());
            updatedItems.add(item.copy(daysLeft, actualStatus));
        }

        int usedCount = 0;
        int expiredCount = 0;
        double savedValue = 0;
        double wastedValue = 0;
        double totalValue = 0;

        for (GroceryItem item : updatedItems) {
            double amount = parseAmount(item.getAmount());
            totalValue += amount;
            if ("used".equals(item.getStatus())) { usedCount++; savedValue += amount; }
            if ("expired".equals(item.getStatus())) { expiredCount++; wastedValue += amount; }
        }

        double avgValue = updatedItems.isEmpty() ? 0 : totalValue / updatedItems.size();

        updateSavingsUI(usedCount, expiredCount, savedValue, wastedValue, avgValue);
        setupPieChart((float) savedValue, (float) wastedValue);
        setupBarChart(updatedItems);
    }

    private double parseAmount(String amount) {
        if (amount == null || amount.isEmpty()) return 0.0;
        try { return Double.parseDouble(amount); } catch (NumberFormatException e) { return 0.0; }
    }

    private void updateSavingsUI(int usedCount, int expiredCount, double savedValue,
                                 double wastedValue, double avgValue) {
        String savedFormatted = String.format("₹%.0f", savedValue);
        String wastedFormatted = String.format("₹%.0f", wastedValue);
        String avgFormatted = String.format("₹%.0f", avgValue);
        double netSavings = savedValue - wastedValue;

        tvMoneySaved.setText(savedFormatted);
        tvMoneyWasted.setText(wastedFormatted);
        tvLegendSaved.setText("Saved (" + savedFormatted + ")");
        tvLegendWasted.setText("Wasted (" + wastedFormatted + ")");
        tvSavingsSummary.setText("Total savings this month: " + String.format("₹%.0f", netSavings) +
                ". You saved " + savedFormatted + " by using " + usedCount + " items before expiry.");
        tvItemsUsedCount.setText(String.valueOf(usedCount));
        tvValueSaved.setText(savedFormatted);
        tvItemsExpiredCount.setText(String.valueOf(expiredCount));
        tvValueWasted.setText(wastedFormatted);
        tvAvgItemValue.setText(avgFormatted);
    }

    private void setupPieChart(float savedValue, float wastedValue) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        if (savedValue > 0) { entries.add(new PieEntry(savedValue, "Saved")); colors.add(Color.parseColor("#4CAF50")); }
        if (wastedValue > 0) { entries.add(new PieEntry(wastedValue, "Wasted")); colors.add(Color.parseColor("#F44336")); }
        if (entries.isEmpty()) { entries.add(new PieEntry(1f, "No Data")); colors.add(Color.parseColor("#E0E0E0")); }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setSliceSpace(2f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return "₹" + (int) value;
            }
        });

        PieData data = new PieData(dataSet);
        pieChartSavings.setData(data);
        pieChartSavings.getDescription().setEnabled(false);
        pieChartSavings.setDrawHoleEnabled(true);
        pieChartSavings.setHoleColor(Color.WHITE);
        pieChartSavings.setHoleRadius(40f);
        pieChartSavings.setTransparentCircleRadius(45f);
        pieChartSavings.setDrawEntryLabels(false);
        pieChartSavings.getLegend().setEnabled(false);
        pieChartSavings.setTouchEnabled(true);
        pieChartSavings.animateY(1000);
        pieChartSavings.invalidate();
    }

    private void setupBarChart(List<GroceryItem> items) {
        Calendar calendar = Calendar.getInstance();
        List<MonthSavingsData> monthDataList = new ArrayList<>();

        for (int i = 5; i >= 0; i--) {
            calendar.setTime(new Date());
            calendar.add(Calendar.MONTH, -i);
            int month = calendar.get(Calendar.MONTH);
            int year = calendar.get(Calendar.YEAR);
            String monthName = new SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.getTime());

            double savedValue = 0, wastedValue = 0;
            for (GroceryItem item : items) {
                Calendar itemCal = Calendar.getInstance();
                itemCal.setTime(item.getCreatedAt());
                if (itemCal.get(Calendar.MONTH) == month && itemCal.get(Calendar.YEAR) == year) {
                    double amount = parseAmount(item.getAmount());
                    if ("used".equals(item.getStatus())) savedValue += amount;
                    if ("expired".equals(item.getStatus())) wastedValue += amount;
                }
            }
            monthDataList.add(new MonthSavingsData(monthName, savedValue, wastedValue));
        }

        if (monthDataList.isEmpty()) { barChartSavings.clear(); barChartSavings.invalidate(); return; }

        ArrayList<BarEntry> savedEntries = new ArrayList<>();
        ArrayList<BarEntry> wastedEntries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        for (int i = 0; i < monthDataList.size(); i++) {
            MonthSavingsData data = monthDataList.get(i);
            savedEntries.add(new BarEntry(i, (float) data.savedValue));
            wastedEntries.add(new BarEntry(i, (float) data.wastedValue));
            labels.add(data.month);
        }

        ValueFormatter currencyFormatter = new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return value == 0f ? "" : "₹" + (int) value;
            }
        };

        BarDataSet savedDataSet = new BarDataSet(savedEntries, "Saved");
        savedDataSet.setColor(Color.parseColor("#4CAF50"));
        savedDataSet.setValueTextSize(10f);
        savedDataSet.setValueFormatter(currencyFormatter);

        BarDataSet wastedDataSet = new BarDataSet(wastedEntries, "Wasted");
        wastedDataSet.setColor(Color.parseColor("#F44336"));
        wastedDataSet.setValueTextSize(10f);
        wastedDataSet.setValueFormatter(currencyFormatter);

        BarData barData = new BarData(savedDataSet, wastedDataSet);
        barData.setBarWidth(0.35f);

        barChartSavings.setData(barData);
        barChartSavings.getDescription().setEnabled(false);
        barChartSavings.setFitBars(true);
        barChartSavings.animateY(1000);

        float groupSpace = 0.3f, barSpace = 0.05f;
        barChartSavings.groupBars(0f, groupSpace, barSpace);
        barChartSavings.getXAxis().setAxisMinimum(0f);
        barChartSavings.getXAxis().setAxisMaximum(labels.size());

        XAxis xAxis = barChartSavings.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelCount(labels.size());

        barChartSavings.getAxisLeft().setDrawGridLines(true);
        barChartSavings.getAxisRight().setEnabled(false);
        barChartSavings.invalidate();
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

                    int usedCount = 0, expiredCount = 0;
                    double savedValue = 0, wastedValue = 0;
                    for (GroceryItem item : updatedItems) {
                        double amount = parseAmount(item.getAmount());
                        if ("used".equals(item.getStatus())) { usedCount++; savedValue += amount; }
                        if ("expired".equals(item.getStatus())) { expiredCount++; wastedValue += amount; }
                    }

                    double netSavings = savedValue - wastedValue;
                    String shareText = "💰 My Money Savings Stats\n" +
                            "Generated on " + new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date()) + "\n\n" +
                            "✅ Items Used: " + usedCount + "\n" +
                            "💚 Money Saved: ₹" + String.format("%.2f", savedValue) + "\n\n" +
                            "❌ Items Expired: " + expiredCount + "\n" +
                            "💸 Money Wasted: ₹" + String.format("%.2f", wastedValue) + "\n\n" +
                            "📊 Net Savings: ₹" + String.format("%.2f", netSavings) + "\n\n" +
                            "Track your groceries with Expiry Tracker!";

                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                    shareIntent.setType("text/plain");
                    startActivity(Intent.createChooser(shareIntent, "Share Savings"));
                });
            });
        });
    }

    public static class MonthSavingsData {
        public final String month;
        public final double savedValue;
        public final double wastedValue;

        public MonthSavingsData(String month, double savedValue, double wastedValue) {
            this.month = month;
            this.savedValue = savedValue;
            this.wastedValue = wastedValue;
        }
    }
}