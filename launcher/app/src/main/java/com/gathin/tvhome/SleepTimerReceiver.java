package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public class SleepTimerReceiver extends BroadcastReceiver {
    public static final String ACTION_SLEEP_EXPIRE = "com.gathin.tvhome.ACTION_SLEEP_EXPIRE";
    public static final String ACTION_SLEEP_WARNING = "com.gathin.tvhome.ACTION_SLEEP_WARNING";

    public static final String PREF_SLEEP_EXPIRE_TIME = "sleep_timer_expire_time";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (ACTION_SLEEP_WARNING.equals(action)) {
            Toast.makeText(context, "Sleep timer: TV sleeping in 1 minute", Toast.LENGTH_LONG).show();
        } else if (ACTION_SLEEP_EXPIRE.equals(action)) {
            clearTimer(context);

            // 1. Audio Focus & Mute
            try {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                    am.setStreamMute(AudioManager.STREAM_MUSIC, true);
                }
            } catch (Exception e) {
                Log.w("SleepTimer", "Error muting audio", e);
            }

            // 2. Activate black screen overlay fallback in MainActivity (hardware standby shuts down this TV)
            Log.d("SleepTimer", "Activating black screen overlay fallback");
            if (MainActivity.instance != null) {
                MainActivity.instance.runOnUiThread(() -> MainActivity.instance.enterScreenOff());
            } else {
                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                mainIntent.putExtra("extra_screen_off", true);
                context.startActivity(mainIntent);
            }
        }
    }

    public static void setTimer(Context context, int minutes) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        clearTimer(context);

        if (minutes <= 0) return;

        long now = System.currentTimeMillis();
        long expireTime = now + (minutes * 60 * 1000L);
        context.getSharedPreferences("tv_launcher_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_SLEEP_EXPIRE_TIME, expireTime)
                .apply();

        // 1-minute warning alarm (if timer >= 2 minutes)
        if (minutes >= 2) {
            long warnTime = expireTime - (60 * 1000L);
            Intent warnIntent = new Intent(context, SleepTimerReceiver.class);
            warnIntent.setAction(ACTION_SLEEP_WARNING);
            PendingIntent warnPi = PendingIntent.getBroadcast(context, 101, warnIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, warnTime, warnPi);
        }

        // Expiration alarm
        Intent expireIntent = new Intent(context, SleepTimerReceiver.class);
        expireIntent.setAction(ACTION_SLEEP_EXPIRE);
        PendingIntent expirePi = PendingIntent.getBroadcast(context, 102, expireIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expireTime, expirePi);
    }

    public static void clearTimer(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            Intent warnIntent = new Intent(context, SleepTimerReceiver.class);
            warnIntent.setAction(ACTION_SLEEP_WARNING);
            PendingIntent warnPi = PendingIntent.getBroadcast(context, 101, warnIntent,
                    PendingIntent.FLAG_NO_CREATE | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            if (warnPi != null) {
                am.cancel(warnPi);
                warnPi.cancel();
            }

            Intent expireIntent = new Intent(context, SleepTimerReceiver.class);
            expireIntent.setAction(ACTION_SLEEP_EXPIRE);
            PendingIntent expirePi = PendingIntent.getBroadcast(context, 102, expireIntent,
                    PendingIntent.FLAG_NO_CREATE | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            if (expirePi != null) {
                am.cancel(expirePi);
                expirePi.cancel();
            }
        }
        context.getSharedPreferences("tv_launcher_prefs", Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_SLEEP_EXPIRE_TIME)
                .apply();
    }

    public static int getRemainingMinutes(Context context) {
        long expireTime = context.getSharedPreferences("tv_launcher_prefs", Context.MODE_PRIVATE)
                .getLong(PREF_SLEEP_EXPIRE_TIME, 0);
        if (expireTime <= 0) return 0;
        long diff = expireTime - System.currentTimeMillis();
        if (diff <= 0) return 0;
        return (int) Math.ceil(diff / 60000.0);
    }
}
