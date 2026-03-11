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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SavingsTabFragment extends Fragment {

    private FirestoreRepository firestoreRepository;

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

    // FIX: Use a thread-safe flag to prevent duplicate loads
    private boolean isLoading = false;

    private final SimpleDateFormat dateFormatter =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_savings_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dateFormatter.setLenient(false);
        firestoreRepository = new FirestoreRepository();

        initViews(view);
        loadSavingsData();
    }

    // FIX #1: Refresh data every time the tab becomes visible
    @Override
    public void onResume() {
        super.onResume();
        loadSavingsData();
    }

    // FIX #5: No ExecutorService needed — FirestoreRepository already handles threading.
    //         This removes the executor leak entirely.

    private void initViews(View view) {
        tvMoneySaved        = view.findViewById(R.id.tv_money_saved);
        tvMoneyWasted       = view.findViewById(R.id.tv_money_wasted);
        tvLegendSaved       = view.findViewById(R.id.tv_legend_saved);
        tvLegendWasted      = view.findViewById(R.id.tv_legend_wasted);
        tvSavingsSummary    = view.findViewById(R.id.tv_savings_summary);
        tvItemsUsedCount    = view.findViewById(R.id.tv_items_used_count);
        tvValueSaved        = view.findViewById(R.id.tv_value_saved);
        tvItemsExpiredCount = view.findViewById(R.id.tv_items_expired_count);
        tvValueWasted       = view.findViewById(R.id.tv_value_wasted);
        tvAvgItemValue      = view.findViewById(R.id.tv_avg_item_value);
        pieChartSavings     = view.findViewById(R.id.pie_chart_savings);
        barChartSavings     = view.findViewById(R.id.bar_chart_savings);
    }

    private void loadSavingsData() {
        // Guard against concurrent loads
        if (isLoading) return;
        isLoading = true;

        firestoreRepository.getUserGroceryItemsAsync(items -> {
            if (!isAdded()) {
                isLoading = false;
                return;
            }
            requireActivity().runOnUiThread(() -> {
                isLoading = false;
                if (items == null) {
                    Toast.makeText(requireContext(),
                            "Failed to load savings data", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (items.isEmpty()) {
                    showEmptyState();
                } else {
                    calculateAndDisplaySavings(items);
                }
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
        // Recalculate live statuses from expiry dates
        List<GroceryItem> updatedItems = new ArrayList<>();
        for (GroceryItem item : items) {
            int daysLeft = calculateDaysLeft(item.getExpiryDate());
            String actualStatus = determineStatus(daysLeft, item.getStatus());
            updatedItems.add(item.copy(daysLeft, actualStatus));
        }

        // FIX #4: Separate "all-time" totals from "this month" totals
        int usedCountAllTime     = 0;
        int expiredCountAllTime  = 0;
        double savedValueAllTime  = 0;
        double wastedValueAllTime = 0;
        double totalValue         = 0;

        // "This month" counters for the summary sentence
        Calendar nowCal = Calendar.getInstance();
        int thisMonth = nowCal.get(Calendar.MONTH);
        int thisYear  = nowCal.get(Calendar.YEAR);

        int usedThisMonth    = 0;
        double savedThisMonth = 0;

        for (GroceryItem item : updatedItems) {
            // FIX #2: If amount is blank, fall back to quantity × 1 (count-based).
            //         This prevents silent ₹0 stats.
            double amount = parseAmount(item.getAmount());
            if (amount == 0.0) {
                amount = item.getQuantity(); // 1 unit = ₹1 weight as a fallback count
            }

            totalValue += amount;

            if ("used".equals(item.getStatus())) {
                usedCountAllTime++;
                savedValueAllTime += amount;

                // Check if it was created this month for the summary
                Calendar itemCal = Calendar.getInstance();
                itemCal.setTime(item.getUpdatedAt() != null ? item.getUpdatedAt() : item.getCreatedAt());
                if (itemCal.get(Calendar.MONTH) == thisMonth
                        && itemCal.get(Calendar.YEAR) == thisYear) {
                    usedThisMonth++;
                    savedThisMonth += amount;
                }
            }
            if ("expired".equals(item.getStatus())) {
                expiredCountAllTime++;
                wastedValueAllTime += amount;
            }
        }

        double avgValue = updatedItems.isEmpty() ? 0 : totalValue / updatedItems.size();

        // FIX #4: Summary now correctly says "this month" using actual monthly data
        updateSavingsUI(
                usedCountAllTime, expiredCountAllTime,
                savedValueAllTime, wastedValueAllTime,
                avgValue, usedThisMonth, savedThisMonth
        );

        setupPieChart((float) savedValueAllTime, (float) wastedValueAllTime);

        // FIX #3: Bar chart now groups by updatedAt (when item status actually changed)
        setupBarChart(updatedItems);
    }

    /**
     * Parses a rupee/amount string. Returns 0.0 if blank or unparseable.
     * FIX #2: Amount is optional — callers should use a fallback if this returns 0.
     */
    private double parseAmount(String amount) {
        if (amount == null || amount.trim().isEmpty()) return 0.0;
        try {
            // Strip any currency symbols before parsing
            return Double.parseDouble(amount.trim().replaceAll("[^\\d.]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // FIX #4: Added thisMonthUsed + thisMonthSaved to drive the accurate summary sentence
    private void updateSavingsUI(int usedCount, int expiredCount,
                                 double savedValue, double wastedValue,
                                 double avgValue,
                                 int usedThisMonth, double savedThisMonth) {

        String savedFormatted      = String.format("₹%.0f", savedValue);
        String wastedFormatted     = String.format("₹%.0f", wastedValue);
        String avgFormatted        = String.format("₹%.0f", avgValue);
        String savedMonthFormatted = String.format("₹%.0f", savedThisMonth);

        tvMoneySaved.setText(savedFormatted);
        tvMoneyWasted.setText(wastedFormatted);
        tvLegendSaved.setText("Saved (" + savedFormatted + ")");
        tvLegendWasted.setText("Wasted (" + wastedFormatted + ")");

        // FIX #4: Summary is now honest — "this month" reflects actual current-month data
        if (usedThisMonth > 0) {
            tvSavingsSummary.setText(
                    "This month you saved " + savedMonthFormatted +
                            " by using " + usedThisMonth + " item" + (usedThisMonth == 1 ? "" : "s") +
                            " before expiry. All-time: " + usedCount + " used, " + expiredCount + " expired."
            );
        } else {
            tvSavingsSummary.setText(
                    "No items used this month yet. " +
                            "All-time: " + usedCount + " used (" + savedFormatted + " saved), " +
                            expiredCount + " expired (" + wastedFormatted + " wasted)."
            );
        }

        tvItemsUsedCount.setText(String.valueOf(usedCount));
        tvValueSaved.setText(savedFormatted);
        tvItemsExpiredCount.setText(String.valueOf(expiredCount));
        tvValueWasted.setText(wastedFormatted);
        tvAvgItemValue.setText(avgFormatted);
    }

    private void setupPieChart(float savedValue, float wastedValue) {
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors  = new ArrayList<>();

        if (savedValue > 0) {
            entries.add(new PieEntry(savedValue, "Saved"));
            colors.add(Color.parseColor("#4CAF50"));
        }
        if (wastedValue > 0) {
            entries.add(new PieEntry(wastedValue, "Wasted"));
            colors.add(Color.parseColor("#F44336"));
        }
        if (entries.isEmpty()) {
            entries.add(new PieEntry(1f, "No Data"));
            colors.add(Color.parseColor("#E0E0E0"));
        }

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
            int month     = calendar.get(Calendar.MONTH);
            int year      = calendar.get(Calendar.YEAR);
            String monthName = new SimpleDateFormat("MMM", Locale.getDefault())
                    .format(calendar.getTime());

            double savedValue = 0, wastedValue = 0;

            for (GroceryItem item : items) {
                // FIX #3: Group by updatedAt (when status actually changed), not createdAt
                Date relevantDate = item.getUpdatedAt() != null
                        ? item.getUpdatedAt()
                        : item.getCreatedAt();

                Calendar itemCal = Calendar.getInstance();
                itemCal.setTime(relevantDate);

                if (itemCal.get(Calendar.MONTH) == month
                        && itemCal.get(Calendar.YEAR) == year) {

                    double amount = parseAmount(item.getAmount());
                    if (amount == 0.0) amount = item.getQuantity();

                    if ("used".equals(item.getStatus()))    savedValue  += amount;
                    if ("expired".equals(item.getStatus())) wastedValue += amount;
                }
            }
            monthDataList.add(new MonthSavingsData(monthName, savedValue, wastedValue));
        }

        if (monthDataList.isEmpty()) {
            barChartSavings.clear();
            barChartSavings.invalidate();
            return;
        }

        ArrayList<BarEntry>  savedEntries  = new ArrayList<>();
        ArrayList<BarEntry>  wastedEntries = new ArrayList<>();
        ArrayList<String>    labels        = new ArrayList<>();

        for (int i = 0; i < monthDataList.size(); i++) {
            MonthSavingsData d = monthDataList.get(i);
            savedEntries.add(new BarEntry(i, (float) d.savedValue));
            wastedEntries.add(new BarEntry(i, (float) d.wastedValue));
            labels.add(d.month);
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

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int calculateDaysLeft(String expiryDate) {
        try {
            Date expiry = dateFormatter.parse(expiryDate);
            if (expiry == null) return 0;

            Calendar expiryCal = Calendar.getInstance();
            expiryCal.setTime(expiry);
            expiryCal.set(Calendar.HOUR_OF_DAY, 0);
            expiryCal.set(Calendar.MINUTE, 0);
            expiryCal.set(Calendar.SECOND, 0);
            expiryCal.set(Calendar.MILLISECOND, 0);

            Calendar todayCal = Calendar.getInstance();
            todayCal.set(Calendar.HOUR_OF_DAY, 0);
            todayCal.set(Calendar.MINUTE, 0);
            todayCal.set(Calendar.SECOND, 0);
            todayCal.set(Calendar.MILLISECOND, 0);

            return (int) TimeUnit.MILLISECONDS.toDays(
                    expiryCal.getTimeInMillis() - todayCal.getTimeInMillis());
        } catch (Exception e) {
            return 0;
        }
    }

    private String determineStatus(int daysLeft, String currentStatus) {
        if ("used".equals(currentStatus))    return "used";
        if (daysLeft < 0)                    return "expired";
        if (daysLeft <= 3)                   return "expiring";
        return "fresh";
    }

    // ─── Export ───────────────────────────────────────────────────────────────

    public void exportStatistics() {
        firestoreRepository.getUserGroceryItemsAsync(items -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (items == null) {
                    Toast.makeText(requireContext(),
                            "Failed to export statistics", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<GroceryItem> updatedItems = new ArrayList<>();
                for (GroceryItem item : items) {
                    int daysLeft    = calculateDaysLeft(item.getExpiryDate());
                    String status   = determineStatus(daysLeft, item.getStatus());
                    updatedItems.add(item.copy(daysLeft, status));
                }

                int    usedCount    = 0, expiredCount = 0;
                double savedValue   = 0, wastedValue  = 0;

                for (GroceryItem item : updatedItems) {
                    double amount = parseAmount(item.getAmount());
                    if (amount == 0.0) amount = item.getQuantity();

                    if ("used".equals(item.getStatus()))    { usedCount++;    savedValue  += amount; }
                    if ("expired".equals(item.getStatus())) { expiredCount++; wastedValue += amount; }
                }

                double netSavings = savedValue - wastedValue;
                String shareText =
                        "💰 My Money Savings Stats\n" +
                                "Generated on " + new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                .format(new Date()) + "\n\n" +
                                "✅ Items Used: "     + usedCount    + "\n" +
                                "💚 Money Saved: ₹"  + String.format("%.2f", savedValue)  + "\n\n" +
                                "❌ Items Expired: "  + expiredCount + "\n" +
                                "💸 Money Wasted: ₹" + String.format("%.2f", wastedValue) + "\n\n" +
                                "📊 Net Savings: ₹"  + String.format("%.2f", netSavings)  + "\n\n" +
                                "Track your groceries with FreshTrack!";

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
                shareIntent.setType("text/plain");
                startActivity(Intent.createChooser(shareIntent, "Share Savings"));
            });
        });
    }

    // ─── Inner class ──────────────────────────────────────────────────────────

    public static class MonthSavingsData {
        public final String month;
        public final double savedValue;
        public final double wastedValue;

        public MonthSavingsData(String month, double savedValue, double wastedValue) {
            this.month       = month;
            this.savedValue  = savedValue;
            this.wastedValue = wastedValue;
        }
    }
}