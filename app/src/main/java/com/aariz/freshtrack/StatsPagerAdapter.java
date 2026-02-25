package com.aariz.freshtrack;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class StatsPagerAdapter extends FragmentStateAdapter {

    public StatsPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new SavingsTabFragment();
            case 1: return new UsageStatsFragment();
            default: throw new IllegalArgumentException("Invalid position: " + position);
        }
    }
}