package com.aariz.freshtrack;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aariz.freshtrack.R;
import com.aariz.freshtrack.OnboardingItem;

import java.util.List;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder> {

    private final List<OnboardingItem> items;

    public OnboardingAdapter(List<OnboardingItem> items) {
        this.items = items;
    }

    public static class OnboardingViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title;
        TextView description;

        public OnboardingViewHolder(@NonNull View view) {
            super(view);
            image       = view.findViewById(R.id.onboarding_image);
            title       = view.findViewById(R.id.onboarding_title);
            description = view.findViewById(R.id.onboarding_description);
        }
    }

    @NonNull
    @Override
    public OnboardingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding_page, parent, false);
        return new OnboardingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OnboardingViewHolder holder, int position) {
        OnboardingItem item = items.get(position);
        holder.image.setImageResource(item.getImage());
        holder.title.setText(item.getTitle());
        holder.description.setText(item.getDescription());

        animateMascotEntrance(holder.image);
        animateMascotPulse(holder.image);
        animateText(holder.title, 200);
        animateText(holder.description, 400);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // Scale + fade entrance animation
    private void animateMascotEntrance(ImageView imageView) {
        ObjectAnimator scaleX    = ObjectAnimator.ofFloat(imageView, "scaleX", 0.7f, 1f);
        ObjectAnimator scaleY    = ObjectAnimator.ofFloat(imageView, "scaleY", 0.7f, 1f);
        ObjectAnimator fadeIn    = ObjectAnimator.ofFloat(imageView, "alpha", 0f, 1f);
        ObjectAnimator translateY = ObjectAnimator.ofFloat(imageView, "translationY", -50f, 0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, fadeIn, translateY);
        set.setDuration(700);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
    }

    // Subtle continuous pulse animation
    private void animateMascotPulse(ImageView imageView) {
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(imageView, "scaleX", 1f, 1.05f, 1f);
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(imageView, "scaleY", 1f, 1.05f, 1f);

        pulseX.setRepeatCount(ObjectAnimator.INFINITE);
        pulseX.setRepeatMode(ObjectAnimator.RESTART);
        pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        pulseY.setRepeatMode(ObjectAnimator.RESTART);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(pulseX, pulseY);
        set.setDuration(2000);
        set.setStartDelay(800);
        set.start();
    }

    // Fade + slide-up text animation
    private void animateText(TextView textView, long delay) {
        textView.setAlpha(0f);
        textView.setTranslationY(30f);
        textView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(delay)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }
}