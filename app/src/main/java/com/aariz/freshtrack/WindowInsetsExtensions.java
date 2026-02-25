package com.aariz.freshtrack;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class WindowInsetsExtensions {

    private WindowInsetsExtensions() { /* static utility class */ }

    /** Applies the status-bar top inset as top padding. */
    public static void applyHeaderInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    insets.top,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return windowInsets;
        });
    }

    /** Applies the navigation-bar bottom inset as bottom padding. */
    public static void applyBottomNavInsets(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    insets.bottom
            );
            return windowInsets;
        });
    }

    /** Applies selected system bar insets as padding. */
    public static void applySystemBarInsets(View view,
                                            boolean top,
                                            boolean bottom,
                                            boolean left,
                                            boolean right) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    left   ? insets.left   : v.getPaddingLeft(),
                    top    ? insets.top    : v.getPaddingTop(),
                    right  ? insets.right  : v.getPaddingRight(),
                    bottom ? insets.bottom : v.getPaddingBottom()
            );
            return windowInsets;
        });
    }

    /** Applies the status-bar top inset as a top margin. */
    public static void applyHeaderInsetsAsMargin(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (lp != null) {
                lp.topMargin = insets.top;
                v.setLayoutParams(lp);
            }
            return windowInsets;
        });
    }

    /** Applies the navigation-bar bottom inset as a bottom margin. */
    public static void applyBottomNavInsetsAsMargin(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (lp != null) {
                lp.bottomMargin = insets.bottom;
                v.setLayoutParams(lp);
            }
            return windowInsets;
        });
    }

    /** Applies the status-bar top inset as top padding and consumes the insets. */
    public static void applyHeaderInsetsAndConsume(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    insets.top,
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Returns the current system bar insets for the given view. */
    public static Insets getSystemBarInsets(View view) {
        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(view);
        if (windowInsets == null) return Insets.NONE;
        return windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
    }
}