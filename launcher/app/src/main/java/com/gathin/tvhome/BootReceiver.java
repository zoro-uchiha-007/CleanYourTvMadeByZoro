package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

/**
 * BootReceiver: Receives BOOT_COMPLETED.
 * Performs a bounded foreground check over at most 30 seconds.
 * If the foreground app is the HDMI/TV input (com.droidlogic.tvinput)
 * or a stock launcher (com.google.android.tvlauncher, com.mitv.tvhome.atv),
 * it brings CleanYourTV's MainActivity to the front.
 * Once brought to front or after 30s (6 checks x 5s), it terminates completely.
 * Zero ongoing background work.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "CleanYourTV_Boot";
    private static final int MAX_ATTEMPTS = 6;
    private static final long CHECK_INTERVAL_MS = 5000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Log.i(TAG, "BOOT_COMPLETED received. Starting bounded foreground check (max 30s).");
        final Context appContext = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            int attempts = 0;

            @Override
            public void run() {
                attempts++;
                boolean handled = checkAndForwardToHome(appContext);
                if (handled) {
                    Log.i(TAG, "Successfully redirected to CleanYourTV on attempt " + attempts + ". Stopping check.");
                    return;
                }
                if (attempts < MAX_ATTEMPTS) {
                    handler.postDelayed(this, CHECK_INTERVAL_MS);
                } else {
                    Log.i(TAG, "Bounded boot check expired after " + attempts + " attempts. Ceasing work.");
                }
            }
        });
    }

    private boolean checkAndForwardToHome(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;

        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (tasks != null && !tasks.isEmpty()) {
            ComponentName top = tasks.get(0).topActivity;
            if (top != null) {
                String pkg = top.getPackageName();
                Log.d(TAG, "Current top task package at boot: " + pkg);
                if ("com.droidlogic.tvinput".equals(pkg)
                        || "com.google.android.tvlauncher".equals(pkg)
                        || "com.mitv.tvhome.atv".equals(pkg)) {
                    Log.i(TAG, "Detected " + pkg + " in front at cold boot. Forwarding to CleanYourTV...");
                    Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                    homeIntent.addCategory(Intent.CATEGORY_HOME);
                    homeIntent.setClass(context, MainActivity.class);
                    homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(homeIntent);
                    return true;
                } else if ("com.gathin.tvhome".equals(pkg)) {
                    // Already in front
                    return true;
                }
            }
        }
        return false;
    }
}

