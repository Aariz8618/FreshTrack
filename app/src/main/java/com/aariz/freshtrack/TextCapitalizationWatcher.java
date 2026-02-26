package com.aariz.freshtrack;

import android.text.Editable;
import android.text.TextWatcher;

public class TextCapitalizationWatcher implements TextWatcher {

    private boolean isFormatting = false;

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // No action needed
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // No action needed
    }

    @Override
    public void afterTextChanged(Editable s) {
        if (isFormatting || s == null || s.length() == 0) return;

        isFormatting = true;

        char firstChar = s.charAt(0);
        if (Character.isLowerCase(firstChar)) {
            s.replace(0, 1, String.valueOf(Character.toUpperCase(firstChar)));
        }

        isFormatting = false;
    }
}