package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.service.dreams.DreamService;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;

import java.io.InputStream;

/**
 * ClockDream: DreamService screensaver wrapper.
 * Displays CleanYourTV's lock/clock screen adhering to the lock wallpaper rule:
 * - Uses the image from the lockscreen assets folder by default (lockscreen_01.jpg)
 * - Changes only when changed in the Display dialog (stored in pref_lock_wallpaper_index)
 * - Decoded strictly on-demand in RGB_565 when dreaming starts
 * - Recycled immediately in onDreamingStopped()
 * - Protects against premature remote key launches via standard wake behavior
 */
public class ClockDream extends DreamService {

    private static final String TAG = "CleanYourTV_Dream";
    private static final String PREFS_NAME = "CleanYourTV_Prefs";

    private Bitmap lockscreenBitmap = null;
    private ImageView dreamAmbientView = null;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // standard TV remote key wakes the dream
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        dreamAmbientView = findViewById(R.id.dreamAmbient);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        loadLockWallpaper();
    }

    private void loadLockWallpaper() {
        recycleBitmap();
        try {
            SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            int lockIndex = sp.getInt("pref_lock_wallpaper_index", 0);
            
            // Lock wallpaper filename resolution:
            // Index 0 is dedicated lockscreen_01.jpg
            String filename = "lockscreen_01.jpg";
            if (lockIndex > 0) {
                // If a user picked another wallpaper from the display dialog
                filename = String.format(java.util.Locale.US, "wallpaper_%02d.jpg", lockIndex);
            }

            InputStream is = null;
            try {
                is = getAssets().open("wallpapers/" + filename);
            } catch (Exception notFound) {
                is = getAssets().open("wallpapers/lockscreen_01.jpg");
            }

            if (is != null) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                lockscreenBitmap = BitmapFactory.decodeStream(is, null, opts);
                is.close();

                if (dreamAmbientView != null && lockscreenBitmap != null) {
                    dreamAmbientView.setImageBitmap(lockscreenBitmap);
                    dreamAmbientView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error decoding lock wallpaper for DreamService", e);
        }
    }

    private void recycleBitmap() {
        if (dreamAmbientView != null) {
            dreamAmbientView.setImageDrawable(null);
        }
        if (lockscreenBitmap != null) {
            if (!lockscreenBitmap.isRecycled()) {
                lockscreenBitmap.recycle();
            }
            lockscreenBitmap = null;
        }
    }

    @Override
    public void onDreamingStopped() {
        recycleBitmap();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        recycleBitmap();
        super.onDetachedFromWindow();
    }
}
