package com.aariz.freshtrack;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class StatisticsFragment extends Fragment {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private MaterialButton buttonExport;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View headerSection = view.findViewById(R.id.header_section);
        if (headerSection != null) {
            WindowInsetsExtensions.applyHeaderInsets(headerSection);
        }

        initViews(view);
        setupViewPager();
        setupExportButton();
    }

    private void initViews(View view) {
        viewPager = view.findViewById(R.id.view_pager);
        tabLayout = view.findViewById(R.id.tab_layout);
        buttonExport = view.findViewById(R.id.button_export);
    }

    private void setupViewPager() {
        StatsPagerAdapter adapter = new StatsPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Money Savings"); break;
                case 1: tab.setText("Usage Stats");   break;
            }
        }).attach();
    }

    private void setupExportButton() {
        buttonExport.setOnClickListener(v -> {
            Fragment currentFragment = getChildFragmentManager()
                    .findFragmentByTag("f" + viewPager.getCurrentItem());

            if (currentFragment instanceof SavingsTabFragment) {
                ((SavingsTabFragment) currentFragment).exportStatistics();
            } else if (currentFragment instanceof UsageStatsFragment) {
                ((UsageStatsFragment) currentFragment).exportStatistics();
            }
        });
    }
}