package com.aariz.freshtrack;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;

public class AuthHelper {

    private static final String PREF_NAME = "app_prefs";
    private static final String KEY_USER_LOGGED_IN_BEFORE = "user_logged_in_before";
    private static final String KEY_IS_FIRST_RUN = "is_first_run";

    public static void markUserLoggedIn(Context context) {
        prefs(context).edit().putBoolean(KEY_USER_LOGGED_IN_BEFORE, true).apply();
    }

    public static void clearUserLoggedInFlag(Context context) {
        prefs(context).edit().putBoolean(KEY_USER_LOGGED_IN_BEFORE, false).apply();
    }

    public static boolean hasUserLoggedInBefore(Context context) {
        return prefs(context).getBoolean(KEY_USER_LOGGED_IN_BEFORE, false);
    }

    public static void logoutUser(Context context) {
        FirebaseAuth.getInstance().signOut();
        clearUserLoggedInFlag(context);

        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    public static void resetForTesting(Context context) {
        prefs(context).edit()
                .putBoolean(KEY_IS_FIRST_RUN, true)
                .putBoolean(KEY_USER_LOGGED_IN_BEFORE, false)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}