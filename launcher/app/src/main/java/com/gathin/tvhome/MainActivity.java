package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class MainActivity extends Activity {
    public static volatile MainActivity instance = null;

    public static final String ACTION_VIEW_INPUTS = "com.gathin.tvhome.action.VIEW_INPUTS";
    public static final String ACTION_LAUNCHER_SETTINGS = "com.gathin.tvhome.action.LAUNCHER_SETTINGS";
    public static final String ACTION_APP_LIBRARY = "com.gathin.tvhome.action.APP_LIBRARY";

    private static final String PREFS_NAME = "tv_launcher_prefs";
    private static final String KEY_LAST_WEATHER_TIME = "last_weather_time";
    private static final String KEY_LAST_WEATHER_TEMP = "last_weather_temp";
    private static final String KEY_LAST_USED_PKG = "last_used_package";
    private static final long WEATHER_CACHE_MS = 30 * 60 * 1000L; // 30 mins
    private static final long SCREENSAVER_IDLE_MS = 120 * 1000L; // 2 minutes auto lock

    // ==========================================
    // WEATHER CONFIGURATION (Open-Meteo)
    // To enable live weather on the top bar:
    // 1. Set WEATHER_ENABLED to true
    // 2. Set WEATHER_LATITUDE and WEATHER_LONGITUDE to your coordinates
    // When false (default 0.0, 0.0), weather is gracefully hidden.
    // ==========================================
    public static final boolean WEATHER_ENABLED = false;
    public static final double WEATHER_LATITUDE = 0.0;
    public static final double WEATHER_LONGITUDE = 0.0;

    // UI elements
    private ScrollView mainScrollView;
    private EffectsCanvasView effectsCanvas;

    // Top Bar
    private LinearLayout topBar;
    private View weatherContainer;
    private TextView weatherTemp;
    private TextView pillRam;
    private TextView pillStorage;
    private LinearLayout btnWifi, btnBrightness, btnInputs, btnLock, btnSleep, btnSettings;
    private ImageView iconWifi, iconBrightness, iconInputs, iconLock, iconSleep, iconSettings;
    private TextView textWifi, textBrightness, textInputs, textLock, textSleep, textSettings;
    private FrameLayout screenOffOverlay;
    private boolean isScreenOffActive = false;
    private long lastScreenOffWakeTime = 0;
    private View brightnessDimOverlay;
    private int currentBrightnessPercent = 80;

    // Wallpaper & Visibility Safety
    private ImageView wallpaperView;
    private View wallpaperScrimView;
    private Bitmap currentWallpaperBitmap = null;
    private int currentWallpaperIndex = 0;
    private Bitmap currentLockWallpaperBitmap = null;
    private int currentLockWallpaperIndex = 0;
    private boolean isHomeDynamicWallpaper = false;
    private boolean isLockDynamicWallpaper = false;
    private final Handler dynamicWallpaperHandler = new Handler(Looper.getMainLooper());
    private final Runnable dynamicWallpaperRunnable = new Runnable() {
        @Override
        public void run() {
            if (isHomeDynamicWallpaper && !isScreensaverActive && !isFinishing() && !wallpaperList.isEmpty()) {
                int next = (currentWallpaperIndex + 1) % wallpaperList.size();
                loadWallpaper(next, true);
                dynamicWallpaperHandler.postDelayed(this, 10 * 60 * 1000L);
            }
        }
    };
    private final Handler lockDynamicHandler = new Handler(Looper.getMainLooper());
    private final Runnable lockDynamicRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScreensaverActive && isLockDynamicWallpaper && !lockWallpaperList.isEmpty()) {
                currentLockWallpaperIndex = (currentLockWallpaperIndex + 1) % lockWallpaperList.size();
                loadCurrentLockWallpaper();
                lockDynamicHandler.postDelayed(this, 2 * 60 * 1000L);
            }
        }
    };
    private TextClock clock, homeDate;
    private ImageView weatherIcon;

    public static class WallpaperConfig {
        final String filename;
        final String title;
        final boolean lightText;
        final float scrimAlpha;

        WallpaperConfig(String filename, String title, boolean lightText, float scrimAlpha) {
            this.filename = filename;
            this.title = title;
            this.lightText = lightText;
            this.scrimAlpha = scrimAlpha;
        }
    }

    private final List<WallpaperConfig> wallpaperList = new ArrayList<>();
    private final List<WallpaperConfig> lockWallpaperList = new ArrayList<>();
    private LinearLayout favSection;
    private LinearLayout moodSection;

    // Dock
    private TextView dockTagline;
    private final LinearLayout[] dockItems = new LinearLayout[6];
    private final ImageView[] dockLogos = new ImageView[6];
    private final TextView[] dockNames = new TextView[6];
    private int focusedDockIndex = 0;

    // Phase 3: Favourites (All 6 characters in same single row)
    private LinearLayout favHeader;
    private ImageView favAppLogo;
    private TextView favTitle;
    private FrameLayout favGroupContainer;
    private View favGlowView;
    private LinearLayout favRow;
    private final ImageView[] favChars = new ImageView[6];

    // Phase 4: Mood Deck (Pure Card with Name, No Image Posters)
    private FrameLayout moodDeckContainer;
    private FrameLayout fannedPostersGroup;
    private TextView cardLeftTitle, cardCenterTitle, cardRightTitle;
    private LinearLayout slotReelGroup;
    private TextView slotResultTitle;
    private TextView countdownText;
    private ProgressBar countdownProgress;
    private TextView moodNameText;
    private TextView moodTitlesCountText;
    private TextView moodControlsHint;

    private int activeMoodIndex = 0;
    private boolean isMoodSpinning = false;
    private boolean isMoodCountingDown = false;
    private CountDownTimer countdownTimer;
    private MoodTitle pickedTitle = null;

    // Overlays & Action Buttons
    private View wallpaperDimView;
    private TextView btnMoreApps;
    private TextView btnHomeUninstall;
    private LinearLayout btnUninstallApps;
    private ImageView iconUninstall;
    private TextView textUninstall;
    private FrameLayout moreAppsOverlay;
    private RecyclerView appsGrid;
    private FrameLayout lockscreenOverlay;
    private ImageView lockscreenWallpaper;
    private LinearLayout lockscreenClockContainer;
    private TextClock lockscreenClock;
    private TextClock lockscreenDate;
    private FrameLayout uiContainer;

    // Screensaver state
    private boolean isScreensaverActive = false;
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Runnable idleRunnable = () -> enterScreensaver();

    // ==========================================
    // DATA STRUCTURES
    // ==========================================

    private View lastFocusedView = null;

    public static class DockApp {
        final String name;
        final String packageName;
        final String tagline;
        final int iconRes;
        final String iconName;
        final String customIconPath;

        DockApp(String name, String packageName, String tagline, int iconRes, String iconName, String customIconPath) {
            this.name = name;
            this.packageName = packageName;
            this.tagline = tagline;
            this.iconRes = iconRes;
            this.iconName = iconName;
            this.customIconPath = customIconPath;
        }
    }

    private static final DockApp[] DEFAULT_DOCK_APPS = new DockApp[]{
            new DockApp("Sun NXT", "com.suntv.sunnxt", "Arambikalangala!", R.drawable.icon_sunnxt, "icon_sunnxt", null),
            new DockApp("MX Player", "com.mxtech.videoplayer.ad", "it's movie time", R.drawable.icon_mxplayer, "icon_mxplayer", null),
            new DockApp("Hotstar", "in.startv.hotstar", "Let's go hotstar", R.drawable.icon_hotstar, "icon_hotstar", null),
            new DockApp("YouTube", "org.smarttube.stable", "Ad-free streaming", R.drawable.icon_youtube, "icon_youtube", null),
            new DockApp("NetMirror", "app.netmirror.newtv", "Mass mode: ON.", R.drawable.icon_netmirror, "icon_netmirror", null),
            new DockApp("Cloudstream", "com.lagradost.cloudstream3", "Nani?! — another episode.", R.drawable.icon_cloudstream, "icon_cloudstream", null)
    };

    private final DockApp[] dockApps = new DockApp[6];
    private final DockApp[] backupDockApps = new DockApp[6];
    private boolean isDockMoveMode = false;
    private int dockMoveIndex = 0;
    private int dockLongPressSlot = -1;
    private boolean dockLongPressTriggered = false;
    private final Handler dockLongPressHandler = new Handler(Looper.getMainLooper());
    private final Runnable dockLongPressRunnable = () -> {
        if (dockLongPressSlot >= 0 && dockLongPressSlot < 6) {
            dockLongPressTriggered = true;
            showDockMenuDialog(dockLongPressSlot);
        }
    };
    private int dockReplaceSlot = -1;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private static class FavCharacter {
        final String title;
        final boolean isAnime;
        final int appLogoRes;
        final int bwRes;
        final int colorRes;
        final int glowColor;
        final EffectsCanvasView.EffectType effect;

        FavCharacter(String title, boolean isAnime, int appLogoRes,
                     int bwRes, int colorRes, int glowColor,
                     EffectsCanvasView.EffectType effect) {
            this.title = title;
            this.isAnime = isAnime;
            this.appLogoRes = appLogoRes;
            this.bwRes = bwRes;
            this.colorRes = colorRes;
            this.glowColor = glowColor;
            this.effect = effect;
        }
    }

    private FavCharacter[] favCharacters;

    public static class MoodTitle {
        final String name;
        final boolean isAnime;
        final int posterRes;
        final String targetPackage;
        final String deepLinkUri;
        final String hint;

        MoodTitle(String name, boolean isAnime, int posterRes) {
            this(name, isAnime, posterRes, null, null, null);
        }

        MoodTitle(String name, boolean isAnime, int posterRes, String targetPackage, String deepLinkUri, String hint) {
            this.name = name;
            this.isAnime = isAnime;
            this.posterRes = posterRes;
            this.targetPackage = targetPackage;
            this.deepLinkUri = deepLinkUri;
            this.hint = hint;
        }
    }

    public static class MoodDeck {
        final String name;
        final int color;
        final List<MoodTitle> titles;

        MoodDeck(String name, int color, List<MoodTitle> titles) {
            this.name = name;
            this.color = color;
            this.titles = titles;
        }
    }

    private final List<MoodDeck> moods = new ArrayList<>();
    private final Random random = new Random();

    // ==========================================
    // LIFECYCLE
    // ==========================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ServiceRepair.restore(this);
        instance = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        uiContainer = findViewById(R.id.uiContainer);
        mainScrollView = findViewById(R.id.mainScrollView);
        effectsCanvas = findViewById(R.id.effectsCanvas);
        wallpaperView = findViewById(R.id.wallpaperView);
        wallpaperScrimView = findViewById(R.id.wallpaperScrimView);
        wallpaperDimView = findViewById(R.id.wallpaperDimView);

        clock = findViewById(R.id.clock);
        homeDate = findViewById(R.id.homeDate);
        weatherContainer = findViewById(R.id.weatherContainer);
        weatherIcon = findViewById(R.id.weatherIcon);
        weatherTemp = findViewById(R.id.weatherTemp);
        topBar = findViewById(R.id.topBar);
        dockTagline = findViewById(R.id.dockTagline);
        lockscreenOverlay = findViewById(R.id.lockscreenOverlay);
        lockscreenWallpaper = findViewById(R.id.lockscreenWallpaper);
        lockscreenClockContainer = findViewById(R.id.lockscreenClockContainer);
        lockscreenClock = findViewById(R.id.lockscreenClock);
        lockscreenDate = findViewById(R.id.lockscreenDate);

        pillRam = findViewById(R.id.pillRam);
        pillStorage = findViewById(R.id.pillStorage);
        btnWifi = findViewById(R.id.btnWifi);
        btnBrightness = findViewById(R.id.btnBrightness);
        btnInputs = findViewById(R.id.btnInputs);
        btnLock = findViewById(R.id.btnLock);
        btnSleep = findViewById(R.id.btnSleep);
        btnSettings = findViewById(R.id.btnSettings);
        iconWifi = findViewById(R.id.iconWifi);
        iconBrightness = findViewById(R.id.iconBrightness);
        iconInputs = findViewById(R.id.iconInputs);
        iconLock = findViewById(R.id.iconLock);
        iconSleep = findViewById(R.id.iconSleep);
        iconSettings = findViewById(R.id.iconSettings);
        textWifi = findViewById(R.id.textWifi);
        textBrightness = findViewById(R.id.textBrightness);
        textInputs = findViewById(R.id.textInputs);
        textLock = findViewById(R.id.textLock);
        textSleep = findViewById(R.id.textSleep);
        textSettings = findViewById(R.id.textSettings);
        screenOffOverlay = findViewById(R.id.screenOffOverlay);
        brightnessDimOverlay = findViewById(R.id.brightnessDimOverlay);

        currentBrightnessPercent = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("pref_brightness", 80);
        applyBrightness(currentBrightnessPercent);
        updateRamIndicator();

        favSection = findViewById(R.id.favSection);
        favHeader = findViewById(R.id.favHeader);
        favAppLogo = findViewById(R.id.favAppLogo);
        favTitle = findViewById(R.id.favTitle);
        favGroupContainer = findViewById(R.id.favGroupContainer);
        favGlowView = findViewById(R.id.favGlowView);
        favRow = findViewById(R.id.favRow);

        moodSection = findViewById(R.id.moodSection);
        moodDeckContainer = findViewById(R.id.moodDeckContainer);
        fannedPostersGroup = findViewById(R.id.fannedPostersGroup);
        cardLeftTitle = findViewById(R.id.cardLeftTitle);
        cardCenterTitle = findViewById(R.id.cardCenterTitle);
        cardRightTitle = findViewById(R.id.cardRightTitle);
        slotReelGroup = findViewById(R.id.slotReelGroup);
        slotResultTitle = findViewById(R.id.slotResultTitle);
        countdownText = findViewById(R.id.countdownText);
        countdownProgress = findViewById(R.id.countdownProgress);
        moodNameText = findViewById(R.id.moodNameText);
        moodTitlesCountText = findViewById(R.id.moodTitlesCountText);
        moodControlsHint = findViewById(R.id.moodControlsHint);

        btnMoreApps = findViewById(R.id.btnMoreApps);
        btnHomeUninstall = findViewById(R.id.btnHomeUninstall);
        btnUninstallApps = findViewById(R.id.btnUninstallApps);
        iconUninstall = findViewById(R.id.iconUninstall);
        textUninstall = findViewById(R.id.textUninstall);
        moreAppsOverlay = findViewById(R.id.moreAppsOverlay);
        appsGrid = findViewById(R.id.appsGrid);

        initFavouritesData();
        initMoodData();

        initNetworkMonitoring();
        loadDockConfig();
        initTopIcons();
        initWallpapers();
        initDock();
        initFavourites();
        initMoodDeck();
        initMoreApps();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (WEATHER_ENABLED && (WEATHER_LATITUDE != 0.0 || WEATHER_LONGITUDE != 0.0)) {
            String cachedTemp = prefs.getString(KEY_LAST_WEATHER_TEMP, "--°C");
            if (weatherTemp != null) weatherTemp.setText(cachedTemp);
            if (weatherContainer != null) weatherContainer.setVisibility(View.VISIBLE);
        } else {
            if (weatherContainer != null) weatherContainer.setVisibility(View.GONE);
        }

        handleLaunchIntent(getIntent());
        unmuteAudio();
        checkAndMigrateScreensaverComponent();
    }

    private void checkAndMigrateScreensaverComponent() {
        try {
            String current = Settings.Secure.getString(getContentResolver(), "screensaver_components");
            // Only update if current value is MiDream or empty, never overwrite another choice
            if (current == null || current.trim().isEmpty() || "com.mitv.dream/com.mitv.dream.MiDream".equals(current.trim())) {
                String cleanDream = getPackageName() + "/" + ClockDream.class.getName();
                boolean success = Settings.Secure.putString(getContentResolver(), "screensaver_components", cleanDream);
                Log.i("TVHome", "Migrated screensaver_components to " + cleanDream + ", success=" + success);
            }
        } catch (Exception e) {
            Log.w("TVHome", "Could not migrate screensaver_components (WRITE_SECURE_SETTINGS check)", e);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
        if (!isScreenOffActive) {
            unmuteAudio();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ServiceRepair.isMissing(this)) {
            ServiceRepair.restore(this);
        }
        if (!isScreenOffActive) {
            unmuteAudio();
        }
        resetIdleTimer();
        registerNetworkMonitoring();
        checkStorageSpace();
        updateSleepTimerIndicator();
        fetchWeather();
        // Dynamically update recommendations and schedule periodic auto-refresh
        fetchDynamicTrendingTitles();
        dynamicRefreshHandler.removeCallbacks(dynamicRefreshRunnable);
        dynamicRefreshHandler.postDelayed(dynamicRefreshRunnable, 30 * 60 * 1000L);

        // Start 5-second RAM usage indicator updates
        ramHandler.removeCallbacks(ramRunnable);
        ramHandler.post(ramRunnable);

        if (isHomeDynamicWallpaper) {
            dynamicWallpaperHandler.removeCallbacks(dynamicWallpaperRunnable);
            dynamicWallpaperHandler.postDelayed(dynamicWallpaperRunnable, 10 * 60 * 1000L);
        }

        // Reset any stale focus visuals
        resetAllFocusVisuals();

        // Restore last used dock app focus
        String lastUsedPkg = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_LAST_USED_PKG, null);
        if (lastUsedPkg != null) {
            for (int i = 0; i < 6; i++) {
                if (dockApps[i] != null && lastUsedPkg.equals(dockApps[i].packageName)) {
                    focusedDockIndex = i;
                    break;
                }
            }
        }

        if (moreAppsOverlay != null && moreAppsOverlay.getVisibility() == View.VISIBLE) {
            loadInstalledApps();
            if (lastFocusedView != null && lastFocusedView.isShown()) {
                lastFocusedView.requestFocus();
            }
        } else if (!isScreensaverActive) {
            if (lastFocusedView != null && lastFocusedView.isShown()) {
                lastFocusedView.requestFocus();
            } else if (dockItems[focusedDockIndex] != null) {
                dockItems[focusedDockIndex].requestFocus();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if (moreAppsOverlay != null && moreAppsOverlay.getVisibility() == View.VISIBLE) {
                loadInstalledApps();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            cancelFocusWatchdog();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelFocusWatchdog();
        unregisterNetworkMonitoring();
        idleHandler.removeCallbacks(idleRunnable);
        dynamicRefreshHandler.removeCallbacks(dynamicRefreshRunnable);
        backLongPressHandler.removeCallbacks(backLongPressRunnable);
        ramHandler.removeCallbacks(ramRunnable);
        dynamicWallpaperHandler.removeCallbacks(dynamicWallpaperRunnable);
        lockDynamicHandler.removeCallbacks(lockDynamicRunnable);
        if (effectsCanvas != null) {
            effectsCanvas.clearEffect();
        }
        if (isScreensaverActive) {
            exitScreensaver();
        }
        if (countdownTimer != null) {
            countdownTimer.cancel();
            isMoodCountingDown = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        cancelFocusWatchdog();
        unregisterNetworkMonitoring();
        idleHandler.removeCallbacks(idleRunnable);
        dynamicRefreshHandler.removeCallbacks(dynamicRefreshRunnable);
        backLongPressHandler.removeCallbacks(backLongPressRunnable);
        ramHandler.removeCallbacks(ramRunnable);
        dynamicWallpaperHandler.removeCallbacks(dynamicWallpaperRunnable);
        lockDynamicHandler.removeCallbacks(lockDynamicRunnable);
        recycleCurrentWallpaper();
        if (effectsCanvas != null) {
            effectsCanvas.clearEffect();
        }
    }

    // ==========================================
    // INITIALIZATION: DATA
    // ==========================================

    private void initFavouritesData() {
        // All 6 characters on the SAME ROW (0: Zoro, 1: Byakuya, 2: Dom, 3: Spiderman, 4: Ironman, 5: Vijay)
        favCharacters = new FavCharacter[]{
                new FavCharacter(
                        "One Piece (Zoro)", true,
                        R.drawable.icon_cloudstream,
                        R.drawable.char_zoro_bw, R.drawable.char_zoro_color,
                        Color.parseColor("#00E676"),
                        EffectsCanvasView.EffectType.ZORO
                ),
                new FavCharacter(
                        "Bleach: TYBW (Byakuya)", true,
                        R.drawable.icon_cloudstream,
                        R.drawable.char_byakuya_bw, R.drawable.char_byakuya_color,
                        Color.parseColor("#E040FB"),
                        EffectsCanvasView.EffectType.BYAKUYA
                ),
                new FavCharacter(
                        "Fast & Furious Series (Dom)", false,
                        R.drawable.icon_netmirror,
                        R.drawable.char_dom_bw, R.drawable.char_dom_color,
                        Color.parseColor("#FF9100"),
                        EffectsCanvasView.EffectType.DOM
                ),
                new FavCharacter(
                        "Spider-Man: No Way Home", false,
                        R.drawable.icon_netmirror,
                        R.drawable.char_spiderman_bw, R.drawable.char_spiderman_color,
                        Color.parseColor("#E53935"),
                        EffectsCanvasView.EffectType.SPIDERMAN
                ),
                new FavCharacter(
                        "Iron Man (2008)", false,
                        R.drawable.icon_netmirror,
                        R.drawable.char_ironman_bw, R.drawable.char_ironman_color,
                        Color.parseColor("#00E5FF"),
                        EffectsCanvasView.EffectType.IRONMAN
                ),
                new FavCharacter(
                        "Master (JD)", false,
                        R.drawable.icon_netmirror,
                        R.drawable.char_vijay_bw, R.drawable.char_vijay_color,
                        Color.parseColor("#FF6D00"),
                        EffectsCanvasView.EffectType.VIJAY
                )
        };
    }

    private void initMoodData() {
        moods.clear();

        // 1. Trending Blockbusters (Latest 2024-2026 Hits)
        List<MoodTitle> blockbusters = new ArrayList<>();
        blockbusters.add(new MoodTitle("Dune: Part Two", false, R.drawable.poster_dune_part_two));
        blockbusters.add(new MoodTitle("Deadpool & Wolverine", false, R.drawable.poster_spider_man_nwh));
        blockbusters.add(new MoodTitle("Oppenheimer", false, R.drawable.poster_interstellar));
        blockbusters.add(new MoodTitle("Kalki 2898 AD", false, R.drawable.poster_rrr));
        blockbusters.add(new MoodTitle("Across the Spider-Verse", false, R.drawable.poster_spider_man_nwh));
        blockbusters.add(new MoodTitle("The Dark Knight", false, R.drawable.poster_the_dark_knight));
        blockbusters.add(new MoodTitle("Interstellar", false, R.drawable.poster_interstellar));
        blockbusters.add(new MoodTitle("Avengers: Endgame", false, R.drawable.poster_avengers_endgame));
        moods.add(new MoodDeck("Trending Blockbusters", Color.parseColor("#E74C3C"), blockbusters));

        // 2. Trending Anime (Always Cloudstream or AniLab)
        List<MoodTitle> anime = new ArrayList<>();
        anime.add(new MoodTitle("Solo Leveling", true, R.drawable.poster_solo_leveling));
        anime.add(new MoodTitle("Demon Slayer: Hashira", true, R.drawable.poster_demon_slayer));
        anime.add(new MoodTitle("Jujutsu Kaisen", true, R.drawable.poster_jujutsu_kaisen));
        anime.add(new MoodTitle("Attack on Titan", true, R.drawable.poster_attack_on_titan));
        anime.add(new MoodTitle("Bleach: TYBW", true, R.drawable.poster_bleach_tybw));
        anime.add(new MoodTitle("Kaiju No. 8", true, R.drawable.poster_solo_leveling));
        anime.add(new MoodTitle("Chainsaw Man", true, R.drawable.poster_jujutsu_kaisen));
        anime.add(new MoodTitle("One Piece", true, R.drawable.poster_one_piece));
        moods.add(new MoodDeck("Trending Anime", Color.parseColor("#9B59B6"), anime));

        // 3. Top Rated Series
        List<MoodTitle> series = new ArrayList<>();
        series.add(new MoodTitle("Severance", false, R.drawable.poster_breaking_bad));
        series.add(new MoodTitle("Shogun", false, R.drawable.poster_the_boys));
        series.add(new MoodTitle("The Bear", false, R.drawable.poster_mirzapur));
        series.add(new MoodTitle("Breaking Bad", false, R.drawable.poster_breaking_bad));
        series.add(new MoodTitle("The Boys", false, R.drawable.poster_the_boys));
        series.add(new MoodTitle("Fallout", false, R.drawable.poster_money_heist));
        series.add(new MoodTitle("Stranger Things", false, R.drawable.poster_panchayat));
        series.add(new MoodTitle("Arcane", false, R.drawable.poster_solo_leveling));
        moods.add(new MoodDeck("Top Rated Series", Color.parseColor("#2ECC71"), series));

        // 4. South Hits
        List<MoodTitle> southHits = new ArrayList<>();
        southHits.add(new MoodTitle("Devara: Part 1", false, R.drawable.poster_rrr));
        southHits.add(new MoodTitle("The G.O.A.T", false, R.drawable.poster_master));
        southHits.add(new MoodTitle("Leo", false, R.drawable.poster_master));
        southHits.add(new MoodTitle("Jailer", false, R.drawable.poster_vikram));
        southHits.add(new MoodTitle("Salaar", false, R.drawable.poster_kgf_chapter_2));
        southHits.add(new MoodTitle("Manjummel Boys", false, R.drawable.poster_vikram));
        southHits.add(new MoodTitle("Vikram", false, R.drawable.poster_vikram));
        southHits.add(new MoodTitle("Master", false, R.drawable.poster_master));
        southHits.add(new MoodTitle("K.G.F: Chapter 2", false, R.drawable.poster_kgf_chapter_2));
        moods.add(new MoodDeck("South Hits", Color.parseColor("#FF6B35"), southHits));

        // Dynamic recommendation: Randomize card order so each session surfaces fresh titles
        for (MoodDeck md : moods) {
            Collections.shuffle(md.titles);
        }

        // 5. Top Recommendations
        List<MoodTitle> allTitles = new ArrayList<>();
        for (MoodDeck md : moods) {
            allTitles.addAll(md.titles);
        }
        Collections.shuffle(allTitles);
        moods.add(new MoodDeck("Top Recommendations", Color.parseColor("#B8C0CC"), allTitles));

        // Fetch live dynamic trending recommendations from online endpoints
        fetchDynamicTrendingTitles();
    }

    private final Handler dynamicRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable dynamicRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchDynamicTrendingTitles();
            // Automatically rotate cards over time to surface new spotlight titles
            if (!moods.isEmpty()) {
                Collections.rotate(moods.get(activeMoodIndex).titles, 1);
                updateMoodCardUI();
            }
            dynamicRefreshHandler.postDelayed(this, 30 * 60 * 1000L); // Auto-refresh every 30 minutes
        }
    };

    // RAM usage indicator: polls every 5 seconds while launcher is active
    private final Handler ramHandler = new Handler(Looper.getMainLooper());
    private long lastStorageCheckTime = 0;
    private final Runnable ramRunnable = new Runnable() {
        @Override
        public void run() {
            updateRamIndicator();
            updateSleepTimerIndicator();
            long now = System.currentTimeMillis();
            if (now - lastStorageCheckTime >= 60000L) {
                lastStorageCheckTime = now;
                checkStorageSpace();
            }
            ramHandler.postDelayed(this, 5000L);
        }
    };

    private void checkStorageSpace() {
        try {
            File dataDir = Environment.getDataDirectory();
            StatFs stat = new StatFs(dataDir.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();
            long totalBytes = totalBlocks * blockSize;
            long freeBytes = availableBlocks * blockSize;
            long freeMb = freeBytes / (1024 * 1024);

            boolean isLow = (freeMb < 500) || (totalBytes > 0 && freeBytes < (totalBytes * 0.10));
            if (isLow) {
                if (pillStorage != null) {
                    pillStorage.setVisibility(View.VISIBLE);
                    pillStorage.setText("Low storage: " + freeMb + " MB");
                }
                if (btnWifi != null) {
                    btnWifi.setNextFocusLeftId(R.id.pillStorage);
                }
                long now = System.currentTimeMillis();
                long lastToast = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong("last_low_storage_toast", 0);
                if (now - lastToast > 24 * 60 * 60 * 1000L) {
                    Toast.makeText(this, "Low storage warning: " + freeMb + " MB free", Toast.LENGTH_LONG).show();
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putLong("last_low_storage_toast", now).apply();
                }
            } else {
                if (pillStorage != null) {
                    pillStorage.setVisibility(View.GONE);
                }
                if (btnWifi != null) {
                    btnWifi.setNextFocusLeftId(View.NO_ID);
                }
            }
        } catch (Exception e) {
            Log.w("TVHome", "Failed to check storage space", e);
        }
    }

    private void openStorageSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            openSettings();
        }
    }

    private void showSleepTimerDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_sleep, null);
        Button btnScreenOffNow = view.findViewById(R.id.btnSleepScreenOffNow);
        Button btnCancel = view.findViewById(R.id.btnSleepCancelTimer);
        Button btnSleep15 = view.findViewById(R.id.btnSleep15);
        Button btnSleep30 = view.findViewById(R.id.btnSleep30);
        Button btnSleep60 = view.findViewById(R.id.btnSleep60);
        Button btnSleep90 = view.findViewById(R.id.btnSleep90);
        Button btnSleep120 = view.findViewById(R.id.btnSleep120);
        Button btnClose = view.findViewById(R.id.btnCloseSleep);

        int remaining = SleepTimerReceiver.getRemainingMinutes(this);
        if (remaining > 0) {
            btnCancel.setVisibility(View.VISIBLE);
            btnCancel.setText("Cancel timer (active: " + remaining + " min)");
            btnScreenOffNow.setNextFocusDownId(R.id.btnSleepCancelTimer);
            btnCancel.setNextFocusUpId(R.id.btnSleepScreenOffNow);
            btnCancel.setNextFocusDownId(R.id.btnSleep15);
            btnSleep15.setNextFocusUpId(R.id.btnSleepCancelTimer);
        } else {
            btnCancel.setVisibility(View.GONE);
            btnScreenOffNow.setNextFocusDownId(R.id.btnSleep15);
            btnSleep15.setNextFocusUpId(R.id.btnSleepScreenOffNow);
        }

        btnSleep15.setNextFocusRightId(R.id.btnSleep30);
        btnSleep30.setNextFocusLeftId(R.id.btnSleep15);
        btnSleep30.setNextFocusRightId(R.id.btnSleep60);
        btnSleep60.setNextFocusLeftId(R.id.btnSleep30);

        btnSleep15.setNextFocusDownId(R.id.btnSleep90);
        btnSleep30.setNextFocusDownId(R.id.btnSleep90);
        btnSleep60.setNextFocusDownId(R.id.btnSleep120);

        btnSleep90.setNextFocusUpId(R.id.btnSleep15);
        btnSleep90.setNextFocusRightId(R.id.btnSleep120);
        btnSleep90.setNextFocusDownId(R.id.btnCloseSleep);

        btnSleep120.setNextFocusUpId(R.id.btnSleep60);
        btnSleep120.setNextFocusLeftId(R.id.btnSleep90);
        btnSleep120.setNextFocusDownId(R.id.btnCloseSleep);

        btnClose.setNextFocusUpId(R.id.btnSleep90);

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            v.setBackgroundResource(hasFocus ? R.drawable.top_btn_focused : R.drawable.top_btn_unfocused);
        };
        btnScreenOffNow.setOnFocusChangeListener(focusListener);
        btnCancel.setOnFocusChangeListener(focusListener);
        btnSleep15.setOnFocusChangeListener(focusListener);
        btnSleep30.setOnFocusChangeListener(focusListener);
        btnSleep60.setOnFocusChangeListener(focusListener);
        btnSleep90.setOnFocusChangeListener(focusListener);
        btnSleep120.setOnFocusChangeListener(focusListener);
        btnClose.setOnFocusChangeListener(focusListener);

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnScreenOffNow.setOnClickListener(v -> {
            dialog.dismiss();
            enterScreenOff();
        });

        btnCancel.setOnClickListener(v -> {
            SleepTimerReceiver.clearTimer(this);
            unmuteAudio();
            updateSleepTimerIndicator();
            Toast.makeText(this, "Sleep timer turned off", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnSleep15.setOnClickListener(v -> {
            SleepTimerReceiver.setTimer(this, 15);
            updateSleepTimerIndicator();
            Toast.makeText(this, "Sleep timer set for 15 minutes", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnSleep30.setOnClickListener(v -> {
            SleepTimerReceiver.setTimer(this, 30);
            updateSleepTimerIndicator();
            Toast.makeText(this, "Sleep timer set for 30 minutes", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnSleep60.setOnClickListener(v -> {
            SleepTimerReceiver.setTimer(this, 60);
            updateSleepTimerIndicator();
            Toast.makeText(this, "Sleep timer set for 60 minutes", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnSleep90.setOnClickListener(v -> {
            SleepTimerReceiver.setTimer(this, 90);
            updateSleepTimerIndicator();
            Toast.makeText(this, "Sleep timer set for 90 minutes", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnSleep120.setOnClickListener(v -> {
            SleepTimerReceiver.setTimer(this, 120);
            updateSleepTimerIndicator();
            Toast.makeText(this, "Sleep timer set for 120 minutes", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        btnScreenOffNow.requestFocus();
    }

    private void updateSleepTimerIndicator() {
        if (btnSleep == null) return;
        int remaining = SleepTimerReceiver.getRemainingMinutes(this);
        if (btnSleep.hasFocus() || btnSleep.isFocused()) {
            if (textSleep != null) {
                textSleep.setVisibility(View.VISIBLE);
                textSleep.setText(remaining > 0 ? ("Sleep (" + remaining + "m)") : "Sleep");
            }
            return;
        }
        if (remaining > 0) {
            if (textSleep != null) {
                textSleep.setVisibility(View.VISIBLE);
                textSleep.setText(remaining + "m");
            }
        } else {
            if (textSleep != null) {
                textSleep.setVisibility(View.GONE);
                textSleep.setText("");
            }
        }
    }

    public void enterScreenOff() {
        if (isScreensaverActive) {
            exitScreensaver();
        }
        isScreenOffActive = true;
        muteAudio();
        idleHandler.removeCallbacks(idleRunnable);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) {}
        if (screenOffOverlay != null) {
            screenOffOverlay.setVisibility(View.VISIBLE);
            screenOffOverlay.setAlpha(1f);
            screenOffOverlay.bringToFront();
            screenOffOverlay.requestFocus();
        }
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = 0.01f;
            getWindow().setAttributes(lp);
        } catch (Exception ignored) {}
    }

    private void muteAudio() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamMute(AudioManager.STREAM_MUSIC, true);
            }
        } catch (Exception ignored) {}
    }

    private void exitScreenOff() {
        if (!isScreenOffActive) return;
        isScreenOffActive = false;
        lastScreenOffWakeTime = android.os.SystemClock.uptimeMillis();
        if (screenOffOverlay != null) {
            screenOffOverlay.setVisibility(View.GONE);
        }
        try {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) {}
        applyBrightness(currentBrightnessPercent);
        unmuteAudio();
        resetIdleTimer();
    }

    private void unmuteAudio() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setStreamMute(AudioManager.STREAM_MUSIC, false);
            }
        } catch (Exception ignored) {}
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            android.view.accessibility.AccessibilityManager am = (android.view.accessibility.AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                List<android.accessibilityservice.AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                for (android.accessibilityservice.AccessibilityServiceInfo service : enabledServices) {
                    if (service.getId() != null && service.getId().contains("com.gathin.tvhome")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null) return;
        if (intent.getBooleanExtra("extra_screen_off", false)) {
            enterScreenOff();
        }
        if (intent.hasExtra("set_wallpaper_index")) {
            int wpIdx = intent.getIntExtra("set_wallpaper_index", 0);
            loadWallpaper(wpIdx, true);
        }
        if (intent.getBooleanExtra("enter_lock_screen", false)) {
            enterScreensaver();
        }
        if (intent.getBooleanExtra("exit_lock_screen", false)) {
            exitScreensaver();
        }
        if (intent.getBooleanExtra("show_display_dialog", false)) {
            showBrightnessDialog();
        }
        if (intent.hasExtra("set_lock_wallpaper_index")) {
            int lockIdx = intent.getIntExtra("set_lock_wallpaper_index", 0);
            if (lockIdx >= 0 && lockIdx < lockWallpaperList.size()) {
                currentLockWallpaperIndex = lockIdx;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putInt("pref_lock_wallpaper_index", lockIdx)
                        .apply();
            }
        }
        if (intent.hasExtra("set_lock_dynamic")) {
            isLockDynamicWallpaper = intent.getBooleanExtra("set_lock_dynamic", false);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean("pref_lock_dynamic_wallpaper", isLockDynamicWallpaper)
                    .apply();
        }
        if (intent.getBooleanExtra("debug_worst_case_topbar", false)) {
            if (pillStorage != null) {
                pillStorage.setVisibility(View.VISIBLE);
                pillStorage.setText("Low storage: 320 MB");
            }
            if (btnSleep != null && textSleep != null) {
                textSleep.setVisibility(View.VISIBLE);
                textSleep.setText("45m");
                int dp10 = (int) (10 * getResources().getDisplayMetrics().density);
                btnSleep.setPadding(dp10, 0, dp10, 0);
            }
        }
    }

    private void updateRamIndicator() {
        if (pillRam == null) return;
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            double totalGB = mi.totalMem / (1024.0 * 1024.0 * 1024.0);
            double availGB = mi.availMem / (1024.0 * 1024.0 * 1024.0);
            double usedGB = Math.max(0.0, totalGB - availGB);
            pillRam.setText(String.format(Locale.US, "%.1f/%.1f GB", usedGB, totalGB));
        } catch (Exception ignored) {}
    }

    // Dev escape hatch: holding Back for 5 seconds opens system Settings
    private final Handler backLongPressHandler = new Handler(Looper.getMainLooper());
    private final Runnable backLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            Toast.makeText(MainActivity.this, "Emergency Escape: Opening Settings", Toast.LENGTH_LONG).show();
            openSettings();
        }
    };

    // Watchdog: brings MainActivity back to front if launched screen freezes or leaves no window focused
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (!hasWindowFocus()) {
                    Intent homeIntent = new Intent(MainActivity.this, MainActivity.class);
                    homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(homeIntent);
                }
            } catch (Exception ignored) {}
        }
    };

    private void startFocusWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable);
        watchdogHandler.postDelayed(watchdogRunnable, 5000L);
    }

    private void cancelFocusWatchdog() {
        watchdogHandler.removeCallbacks(watchdogRunnable);
    }

    private void fetchDynamicTrendingTitles() {
        new Thread(() -> {
            try {
                // Fetch trending anime dynamically from Kitsu (Live API updates as new anime airs)
                java.net.URL url = new java.net.URL("https://kitsu.io/api/edge/trending/anime?limit=10");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    org.json.JSONObject root = new org.json.JSONObject(sb.toString());
                    org.json.JSONArray data = root.optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        List<MoodTitle> liveAnime = new ArrayList<>();
                        for (int i = 0; i < data.length(); i++) {
                            org.json.JSONObject item = data.getJSONObject(i);
                            org.json.JSONObject attrs = item.optJSONObject("attributes");
                            if (attrs != null) {
                                String title = attrs.optString("canonicalTitle", "Anime Hit");
                                liveAnime.add(new MoodTitle(title, true, R.drawable.poster_solo_leveling));
                            }
                        }
                        if (!liveAnime.isEmpty() && moods.size() > 1) {
                            android.util.Log.i("TvHome_LiveFeed", "Kitsu live anime fetched: " + liveAnime.size() + " titles. Top title: " + liveAnime.get(0).name);
                            runOnUiThread(() -> {
                                MoodDeck animeDeck = moods.get(1); // Trending Anime
                                animeDeck.titles.clear();
                                animeDeck.titles.addAll(liveAnime);
                                Collections.shuffle(animeDeck.titles);
                                if (activeMoodIndex == 1) updateMoodCardUI();
                            });
                        }
                    }
                }
            } catch (Exception ignored) {}

            try {
                // Fetch trending series & hits dynamically from TVMaze API
                java.net.URL url = new java.net.URL("https://api.tvmaze.com/shows?page=0");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "CleanYourTV/1.0");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    org.json.JSONArray data = new org.json.JSONArray(sb.toString());
                    if (data.length() > 0) {
                        List<MoodTitle> liveSeries = new ArrayList<>();
                        int count = Math.min(data.length(), 20);
                        for (int i = 0; i < count; i++) {
                            org.json.JSONObject item = data.getJSONObject(i);
                            String name = item.optString("name", "Hit Series");
                            liveSeries.add(new MoodTitle(name, false, R.drawable.poster_breaking_bad));
                        }
                        if (!liveSeries.isEmpty() && moods.size() > 2) {
                            android.util.Log.i("TvHome_LiveFeed", "TVMaze live series fetched: " + liveSeries.size() + " titles. Top title: " + liveSeries.get(0).name);
                            runOnUiThread(() -> {
                                MoodDeck seriesDeck = moods.get(2); // Top Rated Series
                                seriesDeck.titles.clear();
                                seriesDeck.titles.addAll(liveSeries);
                                Collections.shuffle(seriesDeck.titles);
                                if (activeMoodIndex == 2) updateMoodCardUI();
                            });
                        }
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    // ==========================================
    // INITIALIZATION: UI COMPONENTS
    // ==========================================

    private void initTopIcons() {
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            LinearLayout btn = (LinearLayout) v;
            ImageView icon = (v == btnWifi) ? iconWifi : (v == btnBrightness) ? iconBrightness : (v == btnInputs) ? iconInputs : (v == btnLock) ? iconLock : (v == btnSleep) ? iconSleep : iconSettings;
            TextView text = (v == btnWifi) ? textWifi : (v == btnBrightness) ? textBrightness : (v == btnInputs) ? textInputs : (v == btnLock) ? textLock : (v == btnSleep) ? textSleep : textSettings;

            if (hasFocus) {
                lastFocusedView = v;
                resetAllFocusVisuals();
                btn.setBackgroundResource(R.drawable.top_btn_focused);
                if (icon != null) icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                if (text != null) {
                    text.setVisibility(View.VISIBLE);
                    text.setTextColor(Color.WHITE);
                }
                if (v == btnSleep) {
                    int remaining = SleepTimerReceiver.getRemainingMinutes(this);
                    if (remaining > 0) {
                        textSleep.setText("Sleep (" + remaining + "m)");
                    } else {
                        textSleep.setText("Sleep");
                    }
                }
                btn.animate().scaleX(1.03f).scaleY(1.03f).setDuration(100).start();
                mainScrollView.smoothScrollTo(0, 0);
            } else {
                btn.setBackgroundResource(R.drawable.top_btn_unfocused);
                if (icon != null) icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                if (text != null) {
                    if (v == btnSleep) {
                        updateSleepTimerIndicator();
                    } else if (v == btnWifi) {
                        textWifi.setVisibility(View.VISIBLE);
                    } else {
                        text.setVisibility(View.GONE);
                    }
                }
                btn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
            }
        };

        btnWifi.setOnFocusChangeListener(focusListener);
        btnBrightness.setOnFocusChangeListener(focusListener);
        btnInputs.setOnFocusChangeListener(focusListener);
        btnLock.setOnFocusChangeListener(focusListener);
        if (btnSleep != null) btnSleep.setOnFocusChangeListener(focusListener);
        btnSettings.setOnFocusChangeListener(focusListener);

        btnWifi.setOnClickListener(v -> openWifiSettings());
        btnBrightness.setOnClickListener(v -> showBrightnessDialog());
        btnInputs.setOnClickListener(v -> openInputs());
        btnLock.setOnClickListener(v -> enterScreensaver());
        if (btnSleep != null) btnSleep.setOnClickListener(v -> showSleepTimerDialog());
        btnSettings.setOnClickListener(v -> openSettings());

        if (pillStorage != null) {
            pillStorage.setNextFocusRightId(R.id.btnWifi);
            pillStorage.setNextFocusDownId(R.id.dockItem0);
            pillStorage.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    lastFocusedView = v;
                    pillStorage.setBackgroundResource(R.drawable.top_btn_focused);
                    pillStorage.setTextColor(Color.WHITE);
                    pillStorage.animate().scaleX(1.03f).scaleY(1.03f).setDuration(100).start();
                } else {
                    pillStorage.setBackgroundResource(R.drawable.top_btn_unfocused);
                    pillStorage.setTextColor(Color.parseColor("#FFFFB300"));
                    pillStorage.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
            });
            pillStorage.setOnClickListener(v -> openStorageSettings());
        }

        // Horizontal navigation: RAM (non-focusable, skipped) -> Wi-Fi <-> Brightness <-> Inputs <-> Lock <-> Sleep <-> Settings
        btnWifi.setNextFocusRightId(R.id.btnBrightness);
        btnBrightness.setNextFocusLeftId(R.id.btnWifi);
        btnBrightness.setNextFocusRightId(R.id.btnInputs);
        btnInputs.setNextFocusLeftId(R.id.btnBrightness);
        btnInputs.setNextFocusRightId(R.id.btnLock);
        btnLock.setNextFocusLeftId(R.id.btnInputs);
        btnLock.setNextFocusRightId(R.id.btnSleep);
        if (btnSleep != null) {
            btnSleep.setNextFocusLeftId(R.id.btnLock);
            btnSleep.setNextFocusRightId(R.id.btnSettings);
            btnSleep.setNextFocusDownId(R.id.dockItem4);
        }
        btnSettings.setNextFocusLeftId(R.id.btnSleep);

        // Down navigation to dock items
        btnWifi.setNextFocusDownId(R.id.dockItem0);
        btnBrightness.setNextFocusDownId(R.id.dockItem1);
        btnInputs.setNextFocusDownId(R.id.dockItem2);
        btnLock.setNextFocusDownId(R.id.dockItem4);
        btnSettings.setNextFocusDownId(R.id.dockItem5);
    }

    private static final String KEY_DOCK_CONFIG = "pref_dock_config_json_v2";

    private void loadDockConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_DOCK_CONFIG, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                if (arr.length() == 6) {
                    for (int i = 0; i < 6; i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String name = obj.getString("name");
                        String pkg = obj.getString("pkg");
                        String tagline = obj.optString("tagline", name);
                        String iconName = obj.optString("iconName", "");
                        String customIconPath = obj.optString("customIconPath", null);
                        int iconRes = 0;
                        if (!iconName.isEmpty()) {
                            iconRes = getResources().getIdentifier(iconName, "drawable", getPackageName());
                        }
                        dockApps[i] = new DockApp(name, pkg, tagline, iconRes, iconName, customIconPath);
                    }
                    return;
                }
            } catch (Exception e) {
                Log.w("TVHome", "Could not parse dock config, using defaults", e);
            }
        }
        System.arraycopy(DEFAULT_DOCK_APPS, 0, dockApps, 0, 6);
    }

    private void saveDockConfig() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < 6; i++) {
                DockApp app = dockApps[i];
                if (app == null) continue;
                JSONObject obj = new JSONObject();
                obj.put("name", app.name);
                obj.put("pkg", app.packageName);
                obj.put("tagline", app.tagline);
                obj.put("iconName", app.iconName != null ? app.iconName : "");
                obj.put("customIconPath", app.customIconPath != null ? app.customIconPath : "");
                arr.put(obj);
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_DOCK_CONFIG, arr.toString())
                    .apply();
        } catch (Exception e) {
            Log.e("TVHome", "Failed to save dock config", e);
        }
    }

    private void updateDockSlot(int i) {
        if (i < 0 || i >= 6 || dockApps[i] == null) return;
        DockApp app = dockApps[i];
        if (dockNames[i] != null) {
            dockNames[i].setText(app.name);
        }
        if (dockLogos[i] != null) {
            if (app.iconRes != 0) {
                dockLogos[i].setImageResource(app.iconRes);
            } else if (app.customIconPath != null && new File(app.customIconPath).exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(app.customIconPath);
                if (bmp != null) {
                    dockLogos[i].setImageBitmap(bmp);
                } else {
                    dockLogos[i].setImageResource(R.drawable.ic_apps_pill);
                }
            } else {
                try {
                    Drawable icon = getPackageManager().getApplicationIcon(app.packageName);
                    dockLogos[i].setImageDrawable(icon);
                } catch (Exception e) {
                    dockLogos[i].setImageResource(R.drawable.ic_apps_pill);
                }
            }
        }
    }

    private void initDock() {
        int[] itemIds = new int[]{R.id.dockItem0, R.id.dockItem1, R.id.dockItem2, R.id.dockItem3, R.id.dockItem4, R.id.dockItem5};
        int[] logoIds = new int[]{R.id.logo0, R.id.logo1, R.id.logo2, R.id.logo3, R.id.logo4, R.id.logo5};
        int[] nameIds = new int[]{R.id.name0, R.id.name1, R.id.name2, R.id.name3, R.id.name4, R.id.name5};
        int[] charTargetIds = new int[]{R.id.char_zoro, R.id.char_byakuya, R.id.char_dom, R.id.char_spiderman, R.id.char_ironman, R.id.char_vijay};

        for (int i = 0; i < 6; i++) {
            final int index = i;
            dockItems[i] = findViewById(itemIds[i]);
            dockLogos[i] = findViewById(logoIds[i]);
            dockNames[i] = findViewById(nameIds[i]);

            updateDockSlot(i);

            if (i == 0) dockItems[i].setNextFocusUpId(R.id.btnWifi);
            else if (i == 1) dockItems[i].setNextFocusUpId(R.id.btnBrightness);
            else if (i <= 3) dockItems[i].setNextFocusUpId(R.id.btnInputs);
            else if (i == 4) dockItems[i].setNextFocusUpId(R.id.btnLock);
            else dockItems[i].setNextFocusUpId(R.id.btnSettings);

            // Down goes directly to character beneath it
            dockItems[i].setNextFocusDownId(charTargetIds[i]);

            dockItems[i].setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    lastFocusedView = v;
                    focusedDockIndex = index;
                    onDockItemFocused(index);
                } else {
                    dockLongPressHandler.removeCallbacks(dockLongPressRunnable);
                    dockLongPressTriggered = false;
                    onDockItemUnfocused(index);
                }
            });

            dockItems[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (event.getRepeatCount() == 0 && !isDockMoveMode) {
                            dockLongPressSlot = index;
                            dockLongPressTriggered = false;
                            dockLongPressHandler.removeCallbacks(dockLongPressRunnable);
                            dockLongPressHandler.postDelayed(dockLongPressRunnable, 600L);
                        }
                    } else if (event.getAction() == KeyEvent.ACTION_UP) {
                        dockLongPressHandler.removeCallbacks(dockLongPressRunnable);
                        if (dockLongPressTriggered) {
                            dockLongPressTriggered = false;
                            return true;
                        }
                    }
                }
                return false;
            });

            dockItems[i].setOnClickListener(v -> {
                if (!isDockMoveMode && !dockLongPressTriggered) {
                    launchApp(dockApps[index].packageName);
                }
            });
        }

        dockNames[0].setAlpha(1f);
        if (dockApps[0] != null) {
            dockTagline.setText(dockApps[0].tagline.isEmpty() ? dockApps[0].name : dockApps[0].tagline);
        }
    }

    private void showDockMenuDialog(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 6 || dockApps[slotIndex] == null) return;
        String[] options = new String[]{"Move", "Replace app", "Reset order"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Dock: " + dockApps[slotIndex].name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        startDockMove(slotIndex);
                    } else if (which == 1) {
                        startDockReplace(slotIndex);
                    } else if (which == 2) {
                        resetDockOrder();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDockMove(int slotIndex) {
        isDockMoveMode = true;
        dockMoveIndex = slotIndex;
        System.arraycopy(dockApps, 0, backupDockApps, 0, 6);
        if (dockTagline != null) {
            dockTagline.setText("◄ ► Move  •  OK: Drop  •  Back: Cancel");
            dockTagline.setVisibility(View.VISIBLE);
        }
        if (dockLogos[slotIndex] != null) {
            dockLogos[slotIndex].animate().translationY(-14f).scaleX(1.2f).scaleY(1.2f).setDuration(150).start();
        }
    }

    private void startDockReplace(int slotIndex) {
        dockReplaceSlot = slotIndex;
        openMoreApps();
    }

    private void resetDockOrder() {
        System.arraycopy(DEFAULT_DOCK_APPS, 0, dockApps, 0, 6);
        saveDockConfig();
        for (int i = 0; i < 6; i++) {
            updateDockSlot(i);
        }
        if (focusedDockIndex >= 0 && focusedDockIndex < 6 && dockApps[focusedDockIndex] != null) {
            dockTagline.setText(dockApps[focusedDockIndex].tagline.isEmpty() ? dockApps[focusedDockIndex].name : dockApps[focusedDockIndex].tagline);
        }
        Toast.makeText(this, "Dock reset to default", Toast.LENGTH_SHORT).show();
    }

    private void replaceDockSlotWithApp(int slot, ResolveInfo ri) {
        if (slot < 0 || slot >= 6 || ri == null || ri.activityInfo == null) return;
        PackageManager pm = getPackageManager();
        String pkg = ri.activityInfo.packageName;
        String label = ri.loadLabel(pm).toString();

        // Check canonical default apps
        for (DockApp def : DEFAULT_DOCK_APPS) {
            if (def.packageName.equals(pkg)) {
                dockApps[slot] = new DockApp(def.name, def.packageName, def.tagline, def.iconRes, def.iconName, null);
                saveDockConfig();
                updateDockSlot(slot);
                focusedDockIndex = slot;
                if (dockItems[slot] != null) dockItems[slot].requestFocus();
                Toast.makeText(this, def.name + " placed in dock", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Custom app: cache 256px icon to disk
        String iconPath = null;
        try {
            Drawable drawable = ri.loadIcon(pm);
            Bitmap bitmap;
            if (drawable instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) drawable).getBitmap();
            } else {
                bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            File cacheDir = new File(getCacheDir(), "dock_icons");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File iconFile = new File(cacheDir, pkg + ".png");
            FileOutputStream fos = new FileOutputStream(iconFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            iconPath = iconFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("TVHome", "Failed to cache icon for " + pkg, e);
        }

        dockApps[slot] = new DockApp(label, pkg, label, 0, "", iconPath);
        saveDockConfig();
        updateDockSlot(slot);
        focusedDockIndex = slot;
        if (dockItems[slot] != null) dockItems[slot].requestFocus();
        Toast.makeText(this, label + " placed in dock", Toast.LENGTH_SHORT).show();
    }

    private void resetAllFocusVisuals() {
        // 1. Reset all dock focus visuals
        for (int i = 0; i < 6; i++) {
            if (dockLogos[i] != null) {
                dockLogos[i].setScaleX(1.0f);
                dockLogos[i].setScaleY(1.0f);
                dockLogos[i].setTranslationY(0f);
            }
            if (dockNames[i] != null) {
                dockNames[i].setAlpha(0f);
            }
        }
        if (dockTagline != null) {
            dockTagline.setAlpha(0f);
            dockTagline.setVisibility(View.INVISIBLE);
        }

        // 2. Reset character focus visuals
        restoreAllCharacters();

        // 3. Reset mood deck visuals
        restoreMoodFannedState();

        // 4. Collapse top bar button text labels if not focused (Wi-Fi stays always visible)
        if (textWifi != null) textWifi.setVisibility(View.VISIBLE);
        if (btnBrightness != null && !btnBrightness.hasFocus() && textBrightness != null) textBrightness.setVisibility(View.GONE);
        if (btnInputs != null && !btnInputs.hasFocus() && textInputs != null) textInputs.setVisibility(View.GONE);
        if (btnLock != null && !btnLock.hasFocus() && textLock != null) textLock.setVisibility(View.GONE);
        if (btnSettings != null && !btnSettings.hasFocus() && textSettings != null) textSettings.setVisibility(View.GONE);
        if (btnSleep != null && !btnSleep.hasFocus()) updateSleepTimerIndicator();
    }

    private void onDockItemFocused(int index) {
        mainScrollView.smoothScrollTo(0, 0);
        if (topBar != null) {
            topBar.setVisibility(View.VISIBLE);
            topBar.animate().alpha(1f).setDuration(120).start();
        }

        // Enforce: exactly one view has focus visuals
        resetAllFocusVisuals();

        if (dockApps[index] != null && dockTagline != null) {
            dockTagline.setText(dockApps[index].tagline.isEmpty() ? dockApps[index].name : dockApps[index].tagline);
            dockTagline.setVisibility(View.VISIBLE);
            dockTagline.animate().alpha(1f).setDuration(150).start();
        }

        if (dockLogos[index] != null) {
            dockLogos[index].animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start();
        }
        if (dockNames[index] != null) {
            dockNames[index].animate().alpha(1f).setDuration(150).start();
        }
    }

    private void onDockItemUnfocused(int index) {
        if (dockLogos[index] != null) {
            dockLogos[index].animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        }
        if (dockNames[index] != null) {
            dockNames[index].animate().alpha(0f).setDuration(150).start();
        }
        boolean anyDockFocused = false;
        for (int i = 0; i < 6; i++) {
            if (dockItems[i] != null && dockItems[i].hasFocus()) {
                anyDockFocused = true;
                break;
            }
        }
        if (!anyDockFocused && dockTagline != null) {
            dockTagline.animate().alpha(0f).setDuration(120).withEndAction(() -> dockTagline.setVisibility(View.INVISIBLE)).start();
        }
    }

    private void initFavourites() {
        int[] charIds = new int[]{
                R.id.char_zoro, R.id.char_byakuya, R.id.char_dom,
                R.id.char_spiderman, R.id.char_ironman, R.id.char_vijay
        };
        int[] dockTargetIds = new int[]{R.id.dockItem0, R.id.dockItem1, R.id.dockItem2, R.id.dockItem3, R.id.dockItem4, R.id.dockItem5};

        for (int i = 0; i < 6; i++) {
            final int index = i;
            favChars[i] = findViewById(charIds[i]);

            // Up goes back to Dock item
            favChars[i].setNextFocusUpId(dockTargetIds[i]);
            // Down goes to Mood Deck
            favChars[i].setNextFocusDownId(R.id.moodDeckContainer);

            // Left / Right moves along single horizontal row
            if (i > 0) {
                favChars[i].setNextFocusLeftId(charIds[i - 1]);
            }
            if (i < 5) {
                favChars[i].setNextFocusRightId(charIds[i + 1]);
            }

            favChars[i].setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    onFavCharacterFocused(index);
                } else {
                    boolean anyCharFocused = false;
                    for (int c = 0; c < 6; c++) {
                        if (favChars[c] != null && favChars[c].hasFocus()) {
                            anyCharFocused = true;
                            break;
                        }
                    }
                    if (!anyCharFocused) {
                        restoreAllCharacters();
                    }
                }
            });

            favChars[i].setOnClickListener(v -> launchCharacter(favCharacters[index]));
        }
    }

    private void onFavCharacterFocused(int index) {
        lastFocusedView = favChars[index];
        FavCharacter fc = favCharacters[index];

        // Hide topBar so dock icons and characters never overlap time and top icons
        if (topBar != null) {
            topBar.animate().alpha(0f).setDuration(120).withEndAction(() -> topBar.setVisibility(View.INVISIBLE)).start();
        }

        // Smooth scroll to bring the entire character row and feet into view
        if (favSection != null) {
            int scrollY = Math.max(0, favSection.getTop() - 35);
            mainScrollView.smoothScrollTo(0, scrollY);
        }

        // Dim homescreen wallpaper slightly when character is focused
        if (wallpaperDimView != null) {
            wallpaperDimView.animate().alpha(0.50f).setDuration(200).start();
        }

        // 1. Highlight focused character in full color; do NOT blur other characters!
        for (int i = 0; i < 6; i++) {
            if (i == index) {
                favChars[i].setImageResource(fc.colorRes);
                favChars[i].setTranslationZ(24f);
                favChars[i].animate()
                        .scaleX(1.15f)
                        .scaleY(1.15f)
                        .alpha(1.0f)
                        .setDuration(150)
                        .start();
            } else {
                favChars[i].setImageResource(favCharacters[i].bwRes);
                favChars[i].setTranslationZ(0f);
                favChars[i].animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(0.90f) // Keep sharp and unblurred per user request!
                        .setDuration(150)
                        .start();
            }
        }

        // 2. Title header above row (no app icon per user request)
        if (favAppLogo != null) favAppLogo.setVisibility(View.GONE);
        if (favTitle != null) favTitle.setText(fc.title);
        if (favHeader != null) {
            favHeader.setVisibility(View.VISIBLE);
            favHeader.animate().alpha(1f).setDuration(120).start();
        }

        // 3. Trigger localized Canvas effect directly (No round colored circle background)
        effectsCanvas.triggerEffect(fc.effect, favChars[index]);
    }

    private void restoreAllCharacters() {
        if (wallpaperDimView != null) {
            wallpaperDimView.animate().alpha(0f).setDuration(200).start();
        }
        for (int i = 0; i < 6; i++) {
            if (favChars[i] != null) {
                favChars[i].setImageResource(favCharacters[i].bwRes);
                favChars[i].setTranslationZ(0f);
                favChars[i].animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start();
            }
        }
        if (favGlowView != null) {
            favGlowView.animate().alpha(0f).setDuration(150).start();
        }
        if (effectsCanvas != null) {
            effectsCanvas.clearEffect();
        }
        if (favHeader != null) {
            favHeader.animate().alpha(0f).setDuration(120).withEndAction(() -> favHeader.setVisibility(View.INVISIBLE)).start();
        }
    }

    private void launchCharacter(FavCharacter fc) {
        if (fc == null || fc.title == null || fc.title.trim().isEmpty()) {
            launchTitleInCloudStream(null);
            return;
        }
        launchTitleInCloudStream(fc.title);
    }

    private void launchTitleInCloudStream(String title) {
        final String csPkg = "com.lagradost.cloudstream3";
        recordLastUsed(csPkg);
        if (title == null || title.trim().isEmpty()) {
            if (!launchPackageIfInstalled(csPkg)) {
                Toast.makeText(this, "CloudStream is not installed", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        try {
            String encoded = URLEncoder.encode(title.trim(), "UTF-8");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("cloudstreamsearch://" + encoded));
            intent.setPackage(csPkg);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                if (!launchPackageIfInstalled(csPkg)) {
                    Toast.makeText(this, "CloudStream is not installed", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.w("TVHome", "Failed to launch CloudStream for title: " + title, e);
            if (!launchPackageIfInstalled(csPkg)) {
                Toast.makeText(this, "CloudStream is not installed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchNetMirror(String title) {
        recordLastUsed("app.netmirror.newtv");
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage("app.netmirror.newtv");
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage("app.netmirror.newtv");
        }
        if (intent != null) {
            try {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not open NetMirror: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "NetMirror is not installed: " + title, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean launchPackageIfInstalled(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(pkg);
        }
        if (intent != null) {
            try {
                recordLastUsed(pkg);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            } catch (Exception e) {
                Toast.makeText(this, "Could not launch " + pkg + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return false;
    }

    // ==========================================
    // INITIALIZATION: PHASE 4 MOOD DECK
    // ==========================================

    private void initMoodDeck() {
        updateMoodCardUI();

        moodDeckContainer.setNextFocusUpId(R.id.char_spiderman);
        moodDeckContainer.setNextFocusDownId(R.id.btnMoreApps);

        final View cardLeftFrame = findViewById(R.id.cardLeftFrame);
        final View cardCenterFrame = findViewById(R.id.cardCenterFrame);
        final View cardRightFrame = findViewById(R.id.cardRightFrame);
        moodDeckContainer.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                lastFocusedView = v;
                restoreAllCharacters();
                if (topBar != null) {
                    topBar.animate().alpha(0f).setDuration(120).withEndAction(() -> topBar.setVisibility(View.INVISIBLE)).start();
                }
                fannedPostersGroup.animate().scaleX(1.08f).scaleY(1.08f).setDuration(150).start();
                fannedPostersGroup.setForeground(null);
                // Rectangular glow border on ALL THREE poster cards!
                if (cardLeftFrame != null) cardLeftFrame.setForeground(getDrawable(R.drawable.card_glow_border));
                if (cardCenterFrame != null) cardCenterFrame.setForeground(getDrawable(R.drawable.card_glow_border));
                if (cardRightFrame != null) cardRightFrame.setForeground(getDrawable(R.drawable.card_glow_border));

                // Smooth scroll to position the entire mood section and instruction hint ("left/right") in full view
                mainScrollView.post(() -> {
                    if (moodSection != null) {
                        mainScrollView.smoothScrollTo(0, Math.max(0, moodSection.getTop() - 20));
                    }
                });
            } else {
                fannedPostersGroup.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                fannedPostersGroup.setForeground(null);
                if (cardLeftFrame != null) cardLeftFrame.setForeground(null);
                if (cardCenterFrame != null) cardCenterFrame.setForeground(null);
                if (cardRightFrame != null) cardRightFrame.setForeground(null);
                if (countdownTimer != null) {
                    countdownTimer.cancel();
                    isMoodCountingDown = false;
                }
                restoreMoodFannedState();
            }
        });

        moodDeckContainer.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isMoodCountingDown) {
                    cancelCountdown();
                    return true;
                }
                skipToPreviousMoodCard();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (isMoodCountingDown) {
                    cancelCountdown();
                    return true;
                }
                // D-Pad Right skips card (DO NOT spin on right; spin ONLY on OK!)
                skipToNextMoodCard();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                // SPIN ONLY ON OK!
                if (isMoodCountingDown) {
                    launchPickedMoodTitle();
                    return true;
                }
                startSlotReelSpin();
                return true;
            }
            return false;
        });

        moodDeckContainer.setOnClickListener(v -> {
            if (isMoodCountingDown) {
                launchPickedMoodTitle();
            } else {
                startSlotReelSpin();
            }
        });
    }

    private void updateMoodCardUI() {
        if (moods.isEmpty()) return;
        MoodDeck current = moods.get(activeMoodIndex);

        moodNameText.setText(current.name);
        moodNameText.setTextColor(Color.WHITE); // Same crisp font color for all titles!
        moodTitlesCountText.setText("• " + current.titles.size() + " titles");
        moodTitlesCountText.setTextColor(Color.parseColor("#B3FFFFFF"));

        // Update cards to display only the dynamic titles/names (No image posters)
        if (!current.titles.isEmpty()) {
            if (cardCenterTitle != null) cardCenterTitle.setText(current.titles.get(0).name);
            if (current.titles.size() > 1 && cardLeftTitle != null) {
                cardLeftTitle.setText(current.titles.get(1).name);
            }
            if (current.titles.size() > 2 && cardRightTitle != null) {
                cardRightTitle.setText(current.titles.get(2).name);
            }
        }
    }

    private void skipToPreviousMoodCard() {
        if (moods.isEmpty()) return;
        // Fly fanned cards off to right with tilt and fade
        fannedPostersGroup.animate()
                .translationX(500f)
                .rotation(14f)
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    activeMoodIndex = (activeMoodIndex - 1 + moods.size()) % moods.size();
                    Collections.rotate(moods.get(activeMoodIndex).titles, -1);
                    updateMoodCardUI();
                    restoreMoodFannedState();

                    fannedPostersGroup.setTranslationX(-350f);
                    fannedPostersGroup.setRotation(-10f);
                    fannedPostersGroup.setAlpha(0f);
                    fannedPostersGroup.animate()
                            .translationX(0f)
                            .rotation(0f)
                            .alpha(1f)
                            .setDuration(160)
                            .start();
                })
                .start();
    }

    private void skipToNextMoodCard() {
        // Fly fanned cards off to left with tilt and fade
        fannedPostersGroup.animate()
                .translationX(-500f)
                .rotation(-14f)
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    activeMoodIndex = (activeMoodIndex + 1) % moods.size();
                    Collections.rotate(moods.get(activeMoodIndex).titles, 1);
                    updateMoodCardUI();
                    restoreMoodFannedState();

                    fannedPostersGroup.setTranslationX(350f);
                    fannedPostersGroup.setRotation(10f);
                    fannedPostersGroup.setAlpha(0f);
                    fannedPostersGroup.animate()
                            .translationX(0f)
                            .rotation(0f)
                            .alpha(1f)
                            .setDuration(160)
                            .start();
                })
                .start();
    }

    private void startSlotReelSpin() {
        if (isMoodSpinning) return;
        MoodDeck current = moods.get(activeMoodIndex);
        if (current.titles.isEmpty()) return;

        if (countdownTimer != null) {
            countdownTimer.cancel();
            isMoodCountingDown = false;
        }

        isMoodSpinning = true;
        fannedPostersGroup.setVisibility(View.GONE);
        slotReelGroup.setVisibility(View.VISIBLE);
        countdownText.setText("Spinning reel…");
        countdownProgress.setVisibility(View.GONE);

        final int spinSteps = 15;
        final long[] delays = new long[]{40, 50, 60, 70, 80, 95, 110, 130, 160, 200, 250, 310, 380, 460, 550};
        final int finalPickIdx = random.nextInt(current.titles.size());

        runSpinStep(current, 0, spinSteps, delays, finalPickIdx);
    }

    private void runSpinStep(MoodDeck deck, int step, int totalSteps, long[] delays, int finalIdx) {
        if (step >= totalSteps) {
            isMoodSpinning = false;
            pickedTitle = deck.titles.get(finalIdx);
            if (slotResultTitle != null) slotResultTitle.setText(pickedTitle.name);
            startCountdownTimer();
            return;
        }

        int randomIdx = random.nextInt(deck.titles.size());
        if (slotResultTitle != null) slotResultTitle.setText(deck.titles.get(randomIdx).name);
        long delay = delays[step];

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            runSpinStep(deck, step + 1, totalSteps, delays, finalIdx);
        }, delay);
    }

    private void startCountdownTimer() {
        isMoodCountingDown = true;
        countdownProgress.setVisibility(View.VISIBLE);
        countdownProgress.setProgress(100);
        countdownProgress.getProgressDrawable().setColorFilter(moods.get(activeMoodIndex).color, PorterDuff.Mode.SRC_IN);

        moodControlsHint.setText("OK: Open Now   |   ◄ / ► Left/Right: Cancel");
        if (moodSection != null && mainScrollView != null) {
            mainScrollView.smoothScrollTo(0, Math.max(0, moodSection.getTop() - 20));
        }

        countdownTimer = new CountDownTimer(4000, 50) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) Math.ceil(millisUntilFinished / 1000.0);
                countdownText.setText("Opening " + pickedTitle.name + " in " + sec + "…");
                int progress = (int) ((millisUntilFinished / 4000.0) * 100);
                countdownProgress.setProgress(progress);
            }

            @Override
            public void onFinish() {
                launchPickedMoodTitle();
            }
        }.start();
    }

    private void cancelCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }
        isMoodCountingDown = false;
        restoreMoodFannedState();
        moodControlsHint.setText("◄ / ► Left/Right: Skip Mood   |   OK: Pick & Spin");
    }

    private void restoreMoodFannedState() {
        slotReelGroup.setVisibility(View.GONE);
        fannedPostersGroup.setVisibility(View.VISIBLE);
        moodControlsHint.setText("◄ / ► Left/Right: Skip Mood   |   OK: Pick & Spin");
    }

    private void launchPickedMoodTitle() {
        if (pickedTitle == null) return;
        if (countdownTimer != null) {
            countdownTimer.cancel();
        }
        isMoodCountingDown = false;
        restoreMoodFannedState();

        launchMoodTitle(pickedTitle);
    }

    private void launchMoodTitle(MoodTitle title) {
        if (title == null || title.name == null || title.name.trim().isEmpty()) {
            launchTitleInCloudStream(null);
            return;
        }
        launchTitleInCloudStream(title.name);
    }

    // ==========================================
    // INITIALIZATION: MORE APPS
    // ==========================================

    private void initMoreApps() {
        btnMoreApps.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                lastFocusedView = v;
                resetAllFocusVisuals();
                if (topBar != null) {
                    topBar.animate().alpha(0f).setDuration(120).withEndAction(() -> topBar.setVisibility(View.INVISIBLE)).start();
                }
                btnMoreApps.setBackgroundResource(R.drawable.top_btn_focused);
                btnMoreApps.setTextColor(Color.WHITE);
                btnMoreApps.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start();
            } else {
                btnMoreApps.setBackgroundResource(R.drawable.top_btn_unfocused);
                btnMoreApps.setTextColor(Color.WHITE);
                btnMoreApps.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            }
        });
        btnMoreApps.setOnClickListener(v -> openMoreApps());

        // Setup Homescreen Uninstall button next to All Apps
        if (btnHomeUninstall != null) {
            btnHomeUninstall.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    lastFocusedView = v;
                    resetAllFocusVisuals();
                    if (topBar != null) {
                        topBar.animate().alpha(0f).setDuration(120).withEndAction(() -> topBar.setVisibility(View.INVISIBLE)).start();
                    }
                    btnHomeUninstall.setBackgroundResource(R.drawable.top_btn_focused);
                    btnHomeUninstall.setTextColor(Color.WHITE);
                    btnHomeUninstall.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start();
                } else {
                    btnHomeUninstall.setBackgroundResource(R.drawable.top_btn_unfocused);
                    btnHomeUninstall.setTextColor(Color.WHITE);
                    btnHomeUninstall.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            });
            btnHomeUninstall.setOnClickListener(v -> openUninstallPicker());
            btnHomeUninstall.setNextFocusLeftId(R.id.btnMoreApps);
            btnHomeUninstall.setNextFocusUpId(R.id.moodDeckContainer);
            btnMoreApps.setNextFocusRightId(R.id.btnHomeUninstall);
        }

        LinearLayout btnBackFromApps = findViewById(R.id.btnBackFromApps);
        ImageView iconBackFromApps = findViewById(R.id.iconBackFromApps);
        TextView textBackFromApps = findViewById(R.id.textBackFromApps);
        if (btnBackFromApps != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                btnBackFromApps.setDefaultFocusHighlightEnabled(false);
            }
            btnBackFromApps.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    lastFocusedView = v;
                    btnBackFromApps.setBackgroundResource(R.drawable.top_btn_focused);
                    if (iconBackFromApps != null) iconBackFromApps.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    if (textBackFromApps != null) textBackFromApps.setTextColor(Color.WHITE);
                    btnBackFromApps.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start();
                } else {
                    btnBackFromApps.setBackgroundResource(R.drawable.top_btn_unfocused);
                    if (iconBackFromApps != null) iconBackFromApps.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    if (textBackFromApps != null) textBackFromApps.setTextColor(Color.WHITE);
                    btnBackFromApps.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
            });
            btnBackFromApps.setOnClickListener(v -> closeMoreApps());
            btnBackFromApps.setOnKeyListener((v, keyCode, event) -> {
                if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    closeMoreApps();
                    return true;
                }
                return false;
            });
            btnBackFromApps.setNextFocusUpId(R.id.btnBackFromApps);
            btnBackFromApps.setNextFocusLeftId(R.id.btnBackFromApps);
            btnBackFromApps.setNextFocusRightId(R.id.btnUninstallApps);
            btnBackFromApps.setNextFocusDownId(R.id.appsGrid);
        }

        // Setup All Apps drawer Uninstall button next to "All Apps" header
        if (btnUninstallApps != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                btnUninstallApps.setDefaultFocusHighlightEnabled(false);
            }
            btnUninstallApps.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    lastFocusedView = v;
                    btnUninstallApps.setBackgroundResource(R.drawable.top_btn_focused);
                    if (iconUninstall != null) iconUninstall.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    if (textUninstall != null) textUninstall.setTextColor(Color.WHITE);
                    btnUninstallApps.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start();
                } else {
                    btnUninstallApps.setBackgroundResource(R.drawable.top_btn_unfocused);
                    if (iconUninstall != null) iconUninstall.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    if (textUninstall != null) textUninstall.setTextColor(Color.WHITE);
                    btnUninstallApps.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                }
            });
            btnUninstallApps.setOnClickListener(v -> openUninstallPicker());
            btnUninstallApps.setNextFocusLeftId(R.id.btnBackFromApps);
            btnUninstallApps.setNextFocusRightId(R.id.btnUninstallApps);
            btnUninstallApps.setNextFocusUpId(R.id.btnUninstallApps);
            btnUninstallApps.setNextFocusDownId(R.id.appsGrid);
        }
    }

    private void openUninstallPicker() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(mainIntent, 0);

        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackList = pm.queryIntentActivities(leanbackIntent, 0);

        Set<String> seen = new HashSet<>();
        List<ResolveInfo> allAppsList = new ArrayList<>();

        for (ResolveInfo ri : leanbackList) {
            if (ri.activityInfo != null && !seen.contains(ri.activityInfo.packageName)) {
                seen.add(ri.activityInfo.packageName);
                if (!ri.activityInfo.packageName.equals(getPackageName())) {
                    allAppsList.add(ri);
                }
            }
        }
        for (ResolveInfo ri : list) {
            if (ri.activityInfo != null && !seen.contains(ri.activityInfo.packageName)) {
                seen.add(ri.activityInfo.packageName);
                if (!ri.activityInfo.packageName.equals(getPackageName())) {
                    allAppsList.add(ri);
                }
            }
        }

        Collections.sort(allAppsList, (a, b) ->
                a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        List<String> names = new ArrayList<>();
        for (ResolveInfo ri : allAppsList) {
            names.add(ri.loadLabel(pm).toString());
        }

        if (allAppsList.isEmpty()) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "No apps available to uninstall", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select App to Uninstall (" + allAppsList.size() + " Apps)")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    confirmUninstallApp(allAppsList.get(which));
                })
                .setNegativeButton("System Settings", (dialog, which) -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                    } catch (Exception ignored) {}
                })
                .show();
    }

    private void openMoreApps() {
        if (uiContainer != null) {
            uiContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        moreAppsOverlay.setVisibility(View.VISIBLE);
        moreAppsOverlay.setAlpha(0f);
        moreAppsOverlay.animate().alpha(1f).setDuration(180).start();

        loadInstalledApps();
    }

    private void closeMoreApps() {
        final boolean wasReplacing = (dockReplaceSlot >= 0);
        dockReplaceSlot = -1;
        moreAppsOverlay.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            moreAppsOverlay.setVisibility(View.GONE);
            if (uiContainer != null) {
                uiContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            if (wasReplacing && focusedDockIndex >= 0 && focusedDockIndex < 6 && dockItems[focusedDockIndex] != null) {
                dockItems[focusedDockIndex].requestFocus();
            } else if (btnMoreApps != null) {
                btnMoreApps.requestFocus();
            }
        }).start();
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);

        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackActivities = pm.queryIntentActivities(leanbackIntent, 0);

        Set<String> seen = new HashSet<>();
        List<ResolveInfo> allApps = new ArrayList<>();

        for (ResolveInfo ri : leanbackActivities) {
            if (ri.activityInfo != null && !ri.activityInfo.packageName.equals(getPackageName())) {
                seen.add(ri.activityInfo.packageName);
                allApps.add(ri);
            }
        }
        for (ResolveInfo ri : activities) {
            if (ri.activityInfo != null && !seen.contains(ri.activityInfo.packageName) && !ri.activityInfo.packageName.equals(getPackageName())) {
                seen.add(ri.activityInfo.packageName);
                allApps.add(ri);
            }
        }

        Collections.sort(allApps, (a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        appsGrid.setLayoutManager(new GridLayoutManager(this, 5));
        appsGrid.setAdapter(new AppsAdapter(allApps, pm));
        appsGrid.requestFocus();
    }

    private class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.AppViewHolder> {
        private final List<ResolveInfo> apps;
        private final PackageManager pm;

        AppsAdapter(List<ResolveInfo> apps, PackageManager pm) {
            this.apps = apps;
            this.pm = pm;
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_grid_app, parent, false);
            ImageView icon = view.findViewById(R.id.appIcon);
            TextView label = view.findViewById(R.id.appLabel);
            return new AppViewHolder(view, icon, label);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            ResolveInfo app = apps.get(position);
            holder.label.setText(app.loadLabel(pm));
            holder.icon.setImageDrawable(app.loadIcon(pm));

            if (position < 5) {
                holder.itemView.setNextFocusUpId(R.id.btnBackFromApps);
            }

            holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    lastFocusedView = v;
                    holder.itemView.setBackgroundResource(R.drawable.app_tile_focused);
                    holder.itemView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
                } else {
                    holder.itemView.setBackgroundResource(R.drawable.app_tile_unfocused);
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            });

            holder.itemView.setOnClickListener(v -> {
                if (dockReplaceSlot >= 0) {
                    int targetSlot = dockReplaceSlot;
                    dockReplaceSlot = -1;
                    replaceDockSlotWithApp(targetSlot, app);
                    closeMoreApps();
                } else {
                    launchApp(app.activityInfo.packageName);
                }
            });

            // Long-click / holding OK on remote to show uninstall option
            holder.itemView.setLongClickable(true);
            holder.itemView.setOnLongClickListener(v -> {
                confirmUninstallApp(app);
                return true;
            });

            holder.itemView.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        if (event.getRepeatCount() == 0) {
                            event.startTracking();
                        }
                    }
                    if (event.isLongPress()) {
                        confirmUninstallApp(app);
                        return true;
                    }
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class AppViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;

            AppViewHolder(@NonNull View itemView, ImageView icon, TextView label) {
                super(itemView);
                this.icon = icon;
                this.label = label;
            }
        }
    }

    private boolean isProtectedPackage(String pkg) {
        if (pkg == null) return true;
        // Only prevent uninstalling the launcher itself while running
        return pkg.equals(getPackageName());
    }

    private void confirmUninstallApp(ResolveInfo app) {
        if (app == null || app.activityInfo == null) return;
        final String pkg = app.activityInfo.packageName;
        final CharSequence label = app.loadLabel(getPackageManager());

        if (pkg.equals(getPackageName())) {
            Toast.makeText(this, "Cannot uninstall current launcher", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Uninstall " + label)
                .setMessage("Do you want to uninstall " + label + "?")
                .setIcon(app.loadIcon(getPackageManager()))
                .setPositiveButton("Uninstall", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
                        intent.setData(Uri.parse("package:" + pkg));
                        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                        startActivityForResult(intent, 1001);
                    } catch (Exception e) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_DELETE);
                            intent.setData(Uri.parse("package:" + pkg));
                            startActivity(intent);
                        } catch (Exception ex) {
                            Toast.makeText(MainActivity.this, "Uninstall failed: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==========================================
    // APP LAUNCHERS & UTILITIES
    // ==========================================

    private void launchApp(String packageName) {
        if ("org.smarttube.stable".equals(packageName)) {
            if (launchPackageIfInstalled("org.smarttube.stable")) return;
            if (launchPackageIfInstalled("com.google.android.youtube.tv")) return;
        }
        recordLastUsed(packageName);
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(packageName);
        }
        if (intent != null) {
            try {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Could not launch " + packageName + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, packageName + " is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void recordLastUsed(String packageName) {
        if (packageName != null) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_USED_PKG, packageName)
                    .apply();
        }
    }

    private void openWifiSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            } catch (Exception e2) {
                openSettings();
            }
        }
    }

    private void initNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> updateNetworkStatus());
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> updateNetworkStatus());
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                runOnUiThread(() -> updateNetworkStatus());
            }
        };
    }

    private void registerNetworkMonitoring() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                connectivityManager.registerNetworkCallback(request, networkCallback);
            } catch (Exception e) {
                Log.w("TVHome", "Failed to register network callback", e);
            }
        }
        updateNetworkStatus();
    }

    private void unregisterNetworkMonitoring() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
    }

    private void updateNetworkStatus() {
        if (iconWifi == null || textWifi == null || connectivityManager == null) return;
        textWifi.setVisibility(View.VISIBLE);
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            iconWifi.setImageResource(R.drawable.ic_wifi_off);
            textWifi.setText("Disconnected");
            return;
        }
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (caps == null) {
            iconWifi.setImageResource(R.drawable.ic_wifi_off);
            textWifi.setText("Disconnected");
            return;
        }

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            iconWifi.setImageResource(R.drawable.ic_ethernet);
            textWifi.setText("Connected to Ethernet");
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int level = 4;
            String ssid = null;
            if (wm != null) {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null) {
                    level = WifiManager.calculateSignalLevel(info.getRssi(), 5);
                    ssid = info.getSSID();
                }
            }
            int iconRes;
            switch (level) {
                case 0: iconRes = R.drawable.ic_wifi_0; break;
                case 1: iconRes = R.drawable.ic_wifi_1; break;
                case 2: iconRes = R.drawable.ic_wifi_2; break;
                case 3: iconRes = R.drawable.ic_wifi_3; break;
                case 4:
                default: iconRes = R.drawable.ic_wifi; break;
            }
            iconWifi.setImageResource(iconRes);

            String cleanSsid = null;
            if (ssid != null) {
                ssid = ssid.trim();
                if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() >= 2) {
                    ssid = ssid.substring(1, ssid.length() - 1).trim();
                }
                if (!ssid.isEmpty() && !ssid.equalsIgnoreCase("<unknown ssid>")) {
                    cleanSsid = ssid;
                }
            }

            if (cleanSsid != null) {
                textWifi.setText("Connected to " + cleanSsid);
            } else {
                textWifi.setText("Connected to Wi-Fi");
            }
        } else {
            iconWifi.setImageResource(R.drawable.ic_wifi);
            textWifi.setText("Connected to Internet");
        }
    }

    private void initWallpapers() {
        wallpaperList.clear();
        lockWallpaperList.clear();
        try {
            InputStream is = getAssets().open("wallpapers/wallpapers_config.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                wallpaperList.add(new WallpaperConfig(
                        obj.getString("filename"),
                        obj.getString("title"),
                        obj.optBoolean("light_text", false),
                        (float) obj.optDouble("scrim_alpha", 0.0)
                ));
            }
        } catch (Exception e) {
            Log.w("TVHome", "Could not load wallpapers_config.json, using fallback", e);
            wallpaperList.add(new WallpaperConfig("wallpaper_01.jpg", "Wallpaper 1", false, 0.35f));
            wallpaperList.add(new WallpaperConfig("wallpaper_02.jpg", "Wallpaper 2", true, 0.0f));
            wallpaperList.add(new WallpaperConfig("wallpaper_03.jpg", "Wallpaper 3", true, 0.0f));
            wallpaperList.add(new WallpaperConfig("wallpaper_04.jpg", "Wallpaper 4", true, 0.0f));
            wallpaperList.add(new WallpaperConfig("wallpaper_05.jpg", "Wallpaper 5", false, 0.25f));
            wallpaperList.add(new WallpaperConfig("wallpaper_06.jpg", "Wallpaper 6", true, 0.0f));
            wallpaperList.add(new WallpaperConfig("wallpaper_07.jpg", "Wallpaper 7", true, 0.0f));
            wallpaperList.add(new WallpaperConfig("wallpaper_08.jpg", "Wallpaper 8", true, 0.0f));
            wallpaperList.add(new WallpaperConfig("wallpaper_09.jpg", "Wallpaper 9", false, 0.35f));
            wallpaperList.add(new WallpaperConfig("wallpaper_10.jpg", "Wallpaper 10", true, 0.0f));
            wallpaperList.add(new WallpaperConfig("wallpaper_11.jpg", "Wallpaper 11", true, 0.0f));
        }

        // Lockscreen wallpapers:
        // Entry 0 is dedicated to the lockscreen folder image (Goku)
        lockWallpaperList.add(new WallpaperConfig("lockscreen_01.jpg", "Lockscreen Wallpaper", true, 0.0f));
        // Followed by homescreen wallpapers for optional user choice
        lockWallpaperList.addAll(wallpaperList);

        int savedIndex = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("pref_wallpaper_index", 0);
        if (savedIndex < 0 || savedIndex >= wallpaperList.size()) savedIndex = 0;
        loadWallpaper(savedIndex, false);

        int savedLockIndex = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("pref_lock_wallpaper_index", 0);
        if (savedLockIndex < 0 || savedLockIndex >= lockWallpaperList.size()) savedLockIndex = 0;
        currentLockWallpaperIndex = savedLockIndex;

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isHomeDynamicWallpaper = sp.getBoolean("pref_home_dynamic_wallpaper", false);
        isLockDynamicWallpaper = sp.getBoolean("pref_lock_dynamic_wallpaper", false);
    }

    private void recycleCurrentWallpaper() {
        if (currentWallpaperBitmap != null) {
            if (wallpaperView != null) {
                wallpaperView.setImageDrawable(null);
            }
            if (!currentWallpaperBitmap.isRecycled()) {
                currentWallpaperBitmap.recycle();
            }
            currentWallpaperBitmap = null;
        }
    }

    private void recycleLockWallpaper() {
        if (lockscreenWallpaper != null) {
            lockscreenWallpaper.setImageDrawable(null);
        }
        if (currentLockWallpaperBitmap != null) {
            if (!currentLockWallpaperBitmap.isRecycled()) {
                currentLockWallpaperBitmap.recycle();
            }
            currentLockWallpaperBitmap = null;
        }
    }

    private void loadWallpaper(int index, boolean save) {
        if (index < 0 || index >= wallpaperList.size()) return;
        currentWallpaperIndex = index;
        WallpaperConfig cfg = wallpaperList.get(index);
        recycleCurrentWallpaper();
        try {
            InputStream is = getAssets().open("wallpapers/" + cfg.filename);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565; // Ultra low RAM: 1280x720 RGB_565 is only 1.8 MB!
            currentWallpaperBitmap = BitmapFactory.decodeStream(is, null, opts);
            is.close();
            if (wallpaperView != null && currentWallpaperBitmap != null) {
                wallpaperView.setImageBitmap(currentWallpaperBitmap);
            }
        } catch (Exception e) {
            Log.w("TVHome", "Failed to load wallpaper " + cfg.filename, e);
        }

        applyWallpaperSafety(cfg);

        if (save) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt("pref_wallpaper_index", currentWallpaperIndex)
                    .apply();
        }
    }

    private void applyWallpaperSafety(WallpaperConfig cfg) {
        int textColor = cfg.lightText ? Color.WHITE : Color.BLACK;
        int dateColor = cfg.lightText ? Color.parseColor("#E6FFFFFF") : Color.parseColor("#CC000000");

        if (clock != null) {
            clock.setTextColor(textColor);
            if (cfg.lightText) {
                clock.setShadowLayer(4f, 0f, 2f, Color.parseColor("#80000000"));
            } else {
                clock.setShadowLayer(0f, 0f, 0f, 0);
            }
        }
        if (weatherTemp != null) {
            weatherTemp.setTextColor(textColor);
        }
        if (weatherIcon != null) {
            weatherIcon.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
        }
        if (homeDate != null) {
            homeDate.setTextColor(dateColor);
        }
        if (wallpaperScrimView != null) {
            wallpaperScrimView.setAlpha(cfg.scrimAlpha);
        }
    }

    private void applyLockscreenSafety(WallpaperConfig cfg) {
        int textColor = cfg.lightText ? Color.WHITE : Color.BLACK;
        int dateColor = cfg.lightText ? Color.parseColor("#E6FFFFFF") : Color.parseColor("#CC000000");

        if (lockscreenClock != null) {
            lockscreenClock.setTextColor(textColor);
            if (cfg.lightText) {
                lockscreenClock.setShadowLayer(4f, 0f, 2f, Color.parseColor("#80000000"));
            } else {
                lockscreenClock.setShadowLayer(0f, 0f, 0f, 0);
            }
        }
        if (lockscreenDate != null) {
            lockscreenDate.setTextColor(dateColor);
        }
    }

    private void updateDynamicButtonState(Button btn, boolean enabled) {
        if (btn != null) {
            btn.setText(enabled ? "Auto: ON" : "Auto: OFF");
            btn.setTextColor(enabled ? Color.parseColor("#34C759") : Color.WHITE);
        }
    }

    private void showBrightnessDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_brightness, null);
        TextView levelText = view.findViewById(R.id.dialogBrightnessLevel);
        ProgressBar progressBar = view.findViewById(R.id.brightnessSeekBar);
        LinearLayout rowBrightnessSelector = view.findViewById(R.id.rowBrightnessSelector);
        LinearLayout rowWallpaperSelector = view.findViewById(R.id.rowWallpaperSelector);
        TextView dialogWallpaperName = view.findViewById(R.id.dialogWallpaperName);
        Button btnToggleHomeDynamic = view.findViewById(R.id.btnToggleHomeDynamic);
        LinearLayout rowLockWallpaperSelector = view.findViewById(R.id.rowLockWallpaperSelector);
        TextView dialogLockWallpaperName = view.findViewById(R.id.dialogLockWallpaperName);
        Button btnToggleLockDynamic = view.findViewById(R.id.btnToggleLockDynamic);
        Button btnDone = view.findViewById(R.id.btnDoneBrightness);

        final int[] level = new int[]{currentBrightnessPercent};
        levelText.setText(level[0] + "%");
        progressBar.setProgress(level[0]);

        final int[] selectedWallpaperIndex = new int[]{currentWallpaperIndex};
        if (!wallpaperList.isEmpty() && selectedWallpaperIndex[0] < wallpaperList.size()) {
            dialogWallpaperName.setText(wallpaperList.get(selectedWallpaperIndex[0]).title);
        }

        final int[] selectedLockWallpaperIndex = new int[]{currentLockWallpaperIndex};
        if (!lockWallpaperList.isEmpty() && selectedLockWallpaperIndex[0] < lockWallpaperList.size()) {
            dialogLockWallpaperName.setText(lockWallpaperList.get(selectedLockWallpaperIndex[0]).title);
        }

        final boolean[] homeDynamic = new boolean[]{isHomeDynamicWallpaper};
        final boolean[] lockDynamic = new boolean[]{isLockDynamicWallpaper};

        updateDynamicButtonState(btnToggleHomeDynamic, homeDynamic[0]);
        updateDynamicButtonState(btnToggleLockDynamic, lockDynamic[0]);

        btnToggleHomeDynamic.setOnClickListener(v -> {
            homeDynamic[0] = !homeDynamic[0];
            updateDynamicButtonState(btnToggleHomeDynamic, homeDynamic[0]);
        });

        btnToggleLockDynamic.setOnClickListener(v -> {
            lockDynamic[0] = !lockDynamic[0];
            updateDynamicButtonState(btnToggleLockDynamic, lockDynamic[0]);
        });

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            v.setBackgroundResource(hasFocus ? R.drawable.top_btn_focused : R.drawable.top_btn_unfocused);
        };
        rowBrightnessSelector.setOnFocusChangeListener(focusListener);
        rowWallpaperSelector.setOnFocusChangeListener(focusListener);
        btnToggleHomeDynamic.setOnFocusChangeListener(focusListener);
        rowLockWallpaperSelector.setOnFocusChangeListener(focusListener);
        btnToggleLockDynamic.setOnFocusChangeListener(focusListener);
        btnDone.setOnFocusChangeListener(focusListener);

        rowBrightnessSelector.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (level[0] > 10) {
                        level[0] -= 10;
                        levelText.setText(level[0] + "%");
                        progressBar.setProgress(level[0]);
                        applyBrightness(level[0]);
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (level[0] < 100) {
                        level[0] += 10;
                        levelText.setText(level[0] + "%");
                        progressBar.setProgress(level[0]);
                        applyBrightness(level[0]);
                    }
                    return true;
                }
            }
            return false;
        });

        rowWallpaperSelector.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (selectedWallpaperIndex[0] > 0) {
                        selectedWallpaperIndex[0]--;
                    } else {
                        selectedWallpaperIndex[0] = wallpaperList.size() - 1;
                    }
                    dialogWallpaperName.setText(wallpaperList.get(selectedWallpaperIndex[0]).title);
                    loadWallpaper(selectedWallpaperIndex[0], false);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (selectedWallpaperIndex[0] < wallpaperList.size() - 1) {
                        selectedWallpaperIndex[0]++;
                    } else {
                        selectedWallpaperIndex[0] = 0;
                    }
                    dialogWallpaperName.setText(wallpaperList.get(selectedWallpaperIndex[0]).title);
                    loadWallpaper(selectedWallpaperIndex[0], false);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (selectedWallpaperIndex[0] < wallpaperList.size() - 1) {
                        selectedWallpaperIndex[0]++;
                    } else {
                        selectedWallpaperIndex[0] = 0;
                    }
                    dialogWallpaperName.setText(wallpaperList.get(selectedWallpaperIndex[0]).title);
                    loadWallpaper(selectedWallpaperIndex[0], false);
                    return true;
                }
            }
            return false;
        });

        rowLockWallpaperSelector.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (selectedLockWallpaperIndex[0] > 0) {
                        selectedLockWallpaperIndex[0]--;
                    } else {
                        selectedLockWallpaperIndex[0] = lockWallpaperList.size() - 1;
                    }
                    dialogLockWallpaperName.setText(lockWallpaperList.get(selectedLockWallpaperIndex[0]).title);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (selectedLockWallpaperIndex[0] < lockWallpaperList.size() - 1) {
                        selectedLockWallpaperIndex[0]++;
                    } else {
                        selectedLockWallpaperIndex[0] = 0;
                    }
                    dialogLockWallpaperName.setText(lockWallpaperList.get(selectedLockWallpaperIndex[0]).title);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    if (selectedLockWallpaperIndex[0] < lockWallpaperList.size() - 1) {
                        selectedLockWallpaperIndex[0]++;
                    } else {
                        selectedLockWallpaperIndex[0] = 0;
                    }
                    dialogLockWallpaperName.setText(lockWallpaperList.get(selectedLockWallpaperIndex[0]).title);
                    return true;
                }
            }
            return false;
        });

        View.OnKeyListener autoKeyBlocker = (v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return true;
            }
            return false;
        };
        btnToggleHomeDynamic.setOnKeyListener(autoKeyBlocker);
        btnToggleLockDynamic.setOnKeyListener(autoKeyBlocker);

        rowBrightnessSelector.setNextFocusUpId(R.id.rowBrightnessSelector);
        rowBrightnessSelector.setNextFocusDownId(R.id.rowWallpaperSelector);
        rowBrightnessSelector.setNextFocusLeftId(R.id.rowBrightnessSelector);
        rowBrightnessSelector.setNextFocusRightId(R.id.rowBrightnessSelector);

        rowWallpaperSelector.setNextFocusUpId(R.id.rowBrightnessSelector);
        rowWallpaperSelector.setNextFocusDownId(R.id.btnToggleHomeDynamic);
        rowWallpaperSelector.setNextFocusLeftId(R.id.rowWallpaperSelector);
        rowWallpaperSelector.setNextFocusRightId(R.id.rowWallpaperSelector);

        btnToggleHomeDynamic.setNextFocusUpId(R.id.rowWallpaperSelector);
        btnToggleHomeDynamic.setNextFocusDownId(R.id.rowLockWallpaperSelector);
        btnToggleHomeDynamic.setNextFocusLeftId(R.id.btnToggleHomeDynamic);
        btnToggleHomeDynamic.setNextFocusRightId(R.id.btnToggleHomeDynamic);

        rowLockWallpaperSelector.setNextFocusUpId(R.id.btnToggleHomeDynamic);
        rowLockWallpaperSelector.setNextFocusDownId(R.id.btnToggleLockDynamic);
        rowLockWallpaperSelector.setNextFocusLeftId(R.id.rowLockWallpaperSelector);
        rowLockWallpaperSelector.setNextFocusRightId(R.id.rowLockWallpaperSelector);

        btnToggleLockDynamic.setNextFocusUpId(R.id.rowLockWallpaperSelector);
        btnToggleLockDynamic.setNextFocusDownId(R.id.btnDoneBrightness);
        btnToggleLockDynamic.setNextFocusLeftId(R.id.btnToggleLockDynamic);
        btnToggleLockDynamic.setNextFocusRightId(R.id.btnToggleLockDynamic);

        btnDone.setNextFocusUpId(R.id.btnToggleLockDynamic);
        btnDone.setNextFocusDownId(R.id.btnDoneBrightness);
        btnDone.setNextFocusLeftId(R.id.btnDoneBrightness);
        btnDone.setNextFocusRightId(R.id.btnDoneBrightness);

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnDone.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            currentBrightnessPercent = level[0];
            currentWallpaperIndex = selectedWallpaperIndex[0];
            currentLockWallpaperIndex = selectedLockWallpaperIndex[0];
            isHomeDynamicWallpaper = homeDynamic[0];
            isLockDynamicWallpaper = lockDynamic[0];
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt("pref_brightness", currentBrightnessPercent)
                    .putInt("pref_wallpaper_index", currentWallpaperIndex)
                    .putInt("pref_lock_wallpaper_index", currentLockWallpaperIndex)
                    .putBoolean("pref_home_dynamic_wallpaper", isHomeDynamicWallpaper)
                    .putBoolean("pref_lock_dynamic_wallpaper", isLockDynamicWallpaper)
                    .apply();
            applyBrightness(currentBrightnessPercent);
            loadWallpaper(currentWallpaperIndex, false);
            if (isHomeDynamicWallpaper) {
                dynamicWallpaperHandler.removeCallbacks(dynamicWallpaperRunnable);
                dynamicWallpaperHandler.postDelayed(dynamicWallpaperRunnable, 10 * 60 * 1000L);
            } else {
                dynamicWallpaperHandler.removeCallbacks(dynamicWallpaperRunnable);
            }
        });

        dialog.show();
        btnDone.requestFocus();
    }

    private void applyBrightness(int percent) {
        currentBrightnessPercent = Math.max(10, Math.min(100, percent));
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = Math.max(0.05f, currentBrightnessPercent / 100.0f);
            getWindow().setAttributes(lp);
        } catch (Exception ignored) {}

        if (brightnessDimOverlay != null) {
            float dimAlpha = Math.max(0f, (100 - currentBrightnessPercent) / 100.0f * 0.70f);
            brightnessDimOverlay.setAlpha(dimAlpha);
        }

        try {
            int sysBrightness = (int) Math.round((currentBrightnessPercent / 100.0) * 255.0);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, sysBrightness);
        } catch (Exception ignored) {}
    }

    private void openInputs() {
        final String[] inputLabels = new String[]{"HDMI 1", "HDMI 2", "HDMI 3", "AV", "DTV / Cable", "USB Media Explorer"};
        final String[] inputIds = new String[]{
                "com.droidlogic.tvinput/.services.Hdmi1InputService/HW5",
                "com.droidlogic.tvinput/.services.Hdmi2InputService/HW6",
                "com.droidlogic.tvinput/.services.Hdmi3InputService/HW7",
                "com.droidlogic.tvinput/.services.AV1InputService/HW1",
                "com.droidlogic.tvinput/.services.ADTVInputService/HW16",
                null
        };

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Select TV Input / Source")
                .setItems(inputLabels, (dialog, which) -> {
                    String inputId = inputIds[which];
                    if (inputId != null) {
                        switchToTvInput(inputId, inputLabels[which]);
                    } else {
                        // USB Media Explorer
                        try {
                            Intent usb = getPackageManager().getLeanbackLaunchIntentForPackage("com.xiaomi.mitv.mediaexplorer");
                            if (usb == null) usb = getPackageManager().getLaunchIntentForPackage("com.xiaomi.mitv.mediaexplorer");
                            if (usb != null) {
                                usb.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(usb);
                                startFocusWatchdog();
                            }
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "USB Media Explorer unavailable", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void switchToTvInput(String inputId, String label) {
        try {
            Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            startFocusWatchdog();
        } catch (Exception e) {
            Toast.makeText(this, "Could not switch to " + label + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent miSettings = getPackageManager().getLaunchIntentForPackage("com.xiaomi.mitv.settings");
                if (miSettings != null) {
                    miSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(miSettings);
                    return;
                }
            } catch (Exception ignored) {}
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // KEY HANDLING & SCREENSAVER
    // ==========================================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!hasWindowFocus()) {
            return super.dispatchKeyEvent(event);
        }
        resetIdleTimer();

        // Dev escape hatch: holding Back for 5 seconds anywhere opens system Settings
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    backLongPressHandler.removeCallbacks(backLongPressRunnable);
                    backLongPressHandler.postDelayed(backLongPressRunnable, 5000L);
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                backLongPressHandler.removeCallbacks(backLongPressRunnable);
            }
        }

        // If screensaver is active, ANY button press wakes up immediately to Homescreen!
        if (isScreensaverActive) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                exitScreensaver();
                mainScrollView.smoothScrollTo(0, 0);
                if (dockItems[focusedDockIndex] != null) {
                    dockItems[focusedDockIndex].requestFocus();
                }
            }
            return true;
        }

        // If screen off / sleep active, ANY button press wakes up!
        if (isScreenOffActive) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                exitScreenOff();
                mainScrollView.smoothScrollTo(0, 0);
                if (dockItems[focusedDockIndex] != null) {
                    dockItems[focusedDockIndex].requestFocus();
                }
            }
            return true;
        }

        // Guard: ignore select/enter keys for 600ms right after waking from screen-off so focused dock items don't launch!
        if (android.os.SystemClock.uptimeMillis() - lastScreenOffWakeTime < 600) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                return true;
            }
        }

        if (isDockMoveMode) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (dockMoveIndex > 0) {
                        DockApp temp = dockApps[dockMoveIndex];
                        dockApps[dockMoveIndex] = dockApps[dockMoveIndex - 1];
                        dockApps[dockMoveIndex - 1] = temp;

                        dockLogos[dockMoveIndex].setTranslationY(0f);
                        dockLogos[dockMoveIndex].setScaleX(1f);
                        dockLogos[dockMoveIndex].setScaleY(1f);

                        updateDockSlot(dockMoveIndex);
                        updateDockSlot(dockMoveIndex - 1);

                        dockMoveIndex--;
                        focusedDockIndex = dockMoveIndex;
                        dockItems[dockMoveIndex].requestFocus();

                        dockLogos[dockMoveIndex].setTranslationY(-14f);
                        dockLogos[dockMoveIndex].setScaleX(1.2f);
                        dockLogos[dockMoveIndex].setScaleY(1.2f);
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (dockMoveIndex < 5) {
                        DockApp temp = dockApps[dockMoveIndex];
                        dockApps[dockMoveIndex] = dockApps[dockMoveIndex + 1];
                        dockApps[dockMoveIndex + 1] = temp;

                        dockLogos[dockMoveIndex].setTranslationY(0f);
                        dockLogos[dockMoveIndex].setScaleX(1f);
                        dockLogos[dockMoveIndex].setScaleY(1f);

                        updateDockSlot(dockMoveIndex);
                        updateDockSlot(dockMoveIndex + 1);

                        dockMoveIndex++;
                        focusedDockIndex = dockMoveIndex;
                        dockItems[dockMoveIndex].requestFocus();

                        dockLogos[dockMoveIndex].setTranslationY(-14f);
                        dockLogos[dockMoveIndex].setScaleX(1.2f);
                        dockLogos[dockMoveIndex].setScaleY(1.2f);
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    isDockMoveMode = false;
                    dockLogos[dockMoveIndex].animate().translationY(0f).scaleX(1.15f).scaleY(1.15f).setDuration(150).start();
                    saveDockConfig();
                    if (dockApps[dockMoveIndex] != null) {
                        dockTagline.setText(dockApps[dockMoveIndex].tagline.isEmpty() ? dockApps[dockMoveIndex].name : dockApps[dockMoveIndex].tagline);
                    }
                    Toast.makeText(this, "Dock order saved", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                    isDockMoveMode = false;
                    System.arraycopy(backupDockApps, 0, dockApps, 0, 6);
                    for (int i = 0; i < 6; i++) {
                        updateDockSlot(i);
                    }
                    dockLogos[dockMoveIndex].animate().translationY(0f).scaleX(1.15f).scaleY(1.15f).setDuration(150).start();
                    if (dockApps[dockMoveIndex] != null) {
                        dockTagline.setText(dockApps[dockMoveIndex].tagline.isEmpty() ? dockApps[dockMoveIndex].name : dockApps[dockMoveIndex].tagline);
                    }
                    Toast.makeText(this, "Move cancelled", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (moreAppsOverlay != null && moreAppsOverlay.getVisibility() == View.VISIBLE) {
                View cur = getCurrentFocus();
                if (cur != null && cur.getId() == R.id.btnBackFromApps) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        closeMoreApps();
                        return true;
                    }
                }
            }

            // Menu key: Immediately returns to Home Screen & Dock
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                if (moreAppsOverlay.getVisibility() == View.VISIBLE) {
                    closeMoreApps();
                }
                mainScrollView.smoothScrollTo(0, 0);
                if (dockItems[0] != null) {
                    dockItems[0].requestFocus();
                }
                return true;
            }

            // Back key: Step back one level
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (moreAppsOverlay.getVisibility() == View.VISIBLE) {
                    closeMoreApps();
                    return true;
                }
                if (isMoodCountingDown) {
                    cancelCountdown();
                    return true;
                }
                if (btnMoreApps.hasFocus()) {
                    moodDeckContainer.requestFocus();
                    return true;
                }
                if (moodDeckContainer.hasFocus()) {
                    if (favChars[3] != null) favChars[3].requestFocus();
                    return true;
                }
                for (int i = 0; i < 6; i++) {
                    if (favChars[i] != null && favChars[i].hasFocus()) {
                        dockItems[i].requestFocus();
                        return true;
                    }
                }
                if (btnWifi.hasFocus() || btnBrightness.hasFocus() || btnInputs.hasFocus() || btnLock.hasFocus() || btnSettings.hasFocus()) {
                    dockItems[focusedDockIndex].requestFocus();
                    return true;
                }
                return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    private void resetIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable);
        idleHandler.postDelayed(idleRunnable, SCREENSAVER_IDLE_MS);
    }

    private void loadCurrentLockWallpaper() {
        // Decode lockscreen wallpaper strictly on-demand (ultra low RAM)
        recycleLockWallpaper();
        if (!lockWallpaperList.isEmpty() && currentLockWallpaperIndex >= 0 && currentLockWallpaperIndex < lockWallpaperList.size()) {
            WallpaperConfig cfg = lockWallpaperList.get(currentLockWallpaperIndex);
            try {
                InputStream is = getAssets().open("wallpapers/" + cfg.filename);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                currentLockWallpaperBitmap = BitmapFactory.decodeStream(is, null, opts);
                is.close();
                if (lockscreenWallpaper != null && currentLockWallpaperBitmap != null) {
                    lockscreenWallpaper.setImageBitmap(currentLockWallpaperBitmap);
                }
            } catch (Exception e) {
                Log.w("TVHome", "Failed to load lock wallpaper " + cfg.filename, e);
            }
            applyLockscreenSafety(cfg);
        }
    }

    private void enterScreensaver() {
        isScreensaverActive = true;
        if (isLockDynamicWallpaper && !lockWallpaperList.isEmpty()) {
            currentLockWallpaperIndex = (currentLockWallpaperIndex + 1) % lockWallpaperList.size();
        } else {
            currentLockWallpaperIndex = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("pref_lock_wallpaper_index", 0);
            if (currentLockWallpaperIndex < 0 || currentLockWallpaperIndex >= lockWallpaperList.size()) {
                currentLockWallpaperIndex = 0;
            }
        }
        loadCurrentLockWallpaper();
        if (isLockDynamicWallpaper) {
            lockDynamicHandler.removeCallbacks(lockDynamicRunnable);
            lockDynamicHandler.postDelayed(lockDynamicRunnable, 2 * 60 * 1000L);
        }

        if (lockscreenClockContainer != null) {
            lockscreenClockContainer.setTranslationY(0f);
            lockscreenClockContainer.setScaleX(1.0f);
            lockscreenClockContainer.setScaleY(1.0f);
            lockscreenClockContainer.setAlpha(1.0f);
        }
        if (lockscreenWallpaper != null) {
            lockscreenWallpaper.setScaleX(1.0f);
            lockscreenWallpaper.setScaleY(1.0f);
            lockscreenWallpaper.setAlpha(1.0f);
        }
        uiContainer.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(300).start();
        lockscreenOverlay.setVisibility(View.VISIBLE);
        lockscreenOverlay.setAlpha(0f);
        lockscreenOverlay.animate().alpha(1f).setDuration(300).start();
    }

    private void exitScreensaver() {
        if (!isScreensaverActive) return;
        isScreensaverActive = false;
        lockDynamicHandler.removeCallbacks(lockDynamicRunnable);
        currentLockWallpaperIndex = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt("pref_lock_wallpaper_index", 0);
        if (currentLockWallpaperIndex < 0 || currentLockWallpaperIndex >= lockWallpaperList.size()) {
            currentLockWallpaperIndex = 0;
        }

        // Apple-style unlock animation:
        // 1. Clock and date slide up and fade out
        if (lockscreenClockContainer != null) {
            lockscreenClockContainer.animate()
                    .translationY(-140f)
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .alpha(0f)
                    .setDuration(340)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        // 2. Lockscreen wallpaper expands and fades
        if (lockscreenWallpaper != null) {
            lockscreenWallpaper.animate()
                    .scaleX(1.12f)
                    .scaleY(1.12f)
                    .alpha(0f)
                    .setDuration(380)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        // 3. Whole lockscreen overlay fades out and closes
        lockscreenOverlay.animate()
                .alpha(0f)
                .setDuration(380)
                .withEndAction(() -> {
                    lockscreenOverlay.setVisibility(View.GONE);
                    recycleLockWallpaper();
                    if (lockscreenClockContainer != null) {
                        lockscreenClockContainer.setTranslationY(0f);
                        lockscreenClockContainer.setScaleX(1.0f);
                        lockscreenClockContainer.setScaleY(1.0f);
                        lockscreenClockContainer.setAlpha(1.0f);
                    }
                    if (lockscreenWallpaper != null) {
                        lockscreenWallpaper.setScaleX(1.0f);
                        lockscreenWallpaper.setScaleY(1.0f);
                        lockscreenWallpaper.setAlpha(1.0f);
                    }
                })
                .start();

        // 4. Homescreen blooms in smoothly with scale and fade
        if (uiContainer != null) {
            uiContainer.setVisibility(View.VISIBLE);
            uiContainer.setScaleX(0.93f);
            uiContainer.setScaleY(0.93f);
            uiContainer.setAlpha(0f);
            uiContainer.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(380)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        resetIdleTimer();
    }

    // ==========================================
    // WEATHER FETCHER (Open-Meteo)
    // ==========================================

    private void fetchWeather() {
        if (!WEATHER_ENABLED || (WEATHER_LATITUDE == 0.0 && WEATHER_LONGITUDE == 0.0)) {
            if (weatherContainer != null) weatherContainer.setVisibility(View.GONE);
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastTime = prefs.getLong(KEY_LAST_WEATHER_TIME, 0);
        long now = System.currentTimeMillis();

        if (now - lastTime < WEATHER_CACHE_MS) return;

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    String urlStr = String.format(java.util.Locale.US,
                            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m",
                            WEATHER_LATITUDE, WEATHER_LONGITUDE);
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    JSONObject current = json.getJSONObject("current");
                    double temp = current.getDouble("temperature_2m");
                    String res = Math.round(temp) + "°C";
                    android.util.Log.i("TvHome_LiveFeed", "Open-Meteo live weather fetched: " + res);
                    return res;
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(String temp) {
                if (temp != null && weatherTemp != null) {
                    weatherTemp.setText(temp);
                    if (weatherContainer != null) weatherContainer.setVisibility(View.VISIBLE);
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_WEATHER_TIME, System.currentTimeMillis())
                            .putString(KEY_LAST_WEATHER_TEMP, temp)
                            .apply();
                }
            }
        }.execute();
    }
}
