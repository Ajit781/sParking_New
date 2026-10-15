package com.innovus.sparkingnew;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.innovus.sparkingnew.network.ApiConfig;
import com.innovus.sparkingnew.network.VersionCheckService;
import com.innovus.sparkingnew.session.SessionManager;

/**
 * Splash screen launcher activity.
 *
 * Exclusively performs Version Check and dynamic Base URL discovery from server.
 * Saves dynamic Base URL in Local DB (SharedPreferences) for application-wide use.
 *
 * NOTE: Token generation is NOT performed on Splash. It is invoked post OTP verification.
 */
public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "sParking_FLOW";

    private ProgressBar progressBar;
    private TextView tvStatus;
    private MaterialButton btnRetry;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        // Configure edge-to-edge status bar
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }

        View rootView = findViewById(R.id.splash_root);
        if (rootView == null) {
            rootView = findViewById(android.R.id.content);
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Dynamic API Configuration & Cache from Local DB
        ApiConfig.init(this);

        initViews();
        startEntranceAnimation();

        // Launch Version Check Flow
        startVersionCheckFlow();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        btnRetry = findViewById(R.id.btn_retry);

        btnRetry.setOnClickListener(v -> startVersionCheckFlow());
    }

    /**
     * Smooth spring & fade entrance animation for branding elements.
     */
    private void startEntranceAnimation() {
        View logoContainer = findViewById(R.id.fl_logo_container);
        View brandText = findViewById(R.id.layout_brand_text);
        View pillBadge = findViewById(R.id.layout_pill_badge);
        View subtitle = findViewById(R.id.tv_subtitle);
        View bottom = findViewById(R.id.layout_bottom);

        if (logoContainer != null) {
            logoContainer.setScaleX(0.75f);
            logoContainer.setScaleY(0.75f);
            logoContainer.setAlpha(0f);
            logoContainer.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(750)
                    .setInterpolator(new OvershootInterpolator(1.3f))
                    .start();
        }

        if (brandText != null) {
            brandText.setAlpha(0f);
            brandText.setTranslationY(25f);
            brandText.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(200)
                    .setDuration(500)
                    .start();
        }

        if (pillBadge != null) {
            pillBadge.setAlpha(0f);
            pillBadge.setTranslationY(20f);
            pillBadge.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(350)
                    .setDuration(450)
                    .start();
        }

        if (subtitle != null) {
            subtitle.setAlpha(0f);
            subtitle.animate()
                    .alpha(1f)
                    .setStartDelay(450)
                    .setDuration(500)
                    .start();
        }

        if (bottom != null) {
            bottom.setAlpha(0f);
            bottom.setTranslationY(30f);
            bottom.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(500)
                    .setDuration(500)
                    .start();
        }
    }

    /**
     * Step 1: Queries VersionCheck API dynamically.
     * Enforces app update if lower version, checks expiry, and updates dynamic base URL in Local DB.
     */
    private void startVersionCheckFlow() {
        progressBar.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.GONE);
        tvStatus.setText("Verifying app version & server configuration...");

        Log.i(TAG, "================================================================================");
        Log.i(TAG, "🚀 [sParking_FLOW] Splash screen started. Checking server version...");
        Log.i(TAG, "🌐 Active Mode: " + (ApiConfig.isProduction() ? "PRODUCTION [AES-256-GCM]" : "STAGING [Plain Real Data]"));
        Log.i(TAG, "📡 Initial Active Base URL: " + ApiConfig.getBaseUrl());
        Log.i(TAG, "================================================================================");

        VersionCheckService.checkVersion(this, new VersionCheckService.VersionCheckCallback() {
            @Override
            public void onSuccess(VersionCheckService.VersionCheckResult result) {
                Log.i(TAG, "================================================================================");
                Log.i(TAG, "✅ [sParking_FLOW] Version Check Result: " + result);

                // 1. Force update check if installed version is lower than ServerVersion
                if (result.isLowerVersion()) {
                    Log.w(TAG, "⚠️ [sParking_FLOW] Lower Version detected! Required: " + result.getServerVersion());
                    progressBar.setVisibility(View.GONE);
                    showUpdateRequiredDialog(result.getServerVersion());
                    return;
                }

                // 2. Build expiry check if valid_upto is provided
                if (result.isBuildExpired()) {
                    Log.w(TAG, "⚠️ [sParking_FLOW] Build Expired on: " + result.getValidUpto());
                    progressBar.setVisibility(View.GONE);
                    showBuildExpiredDialog();
                    return;
                }

                // 3. Dynamic Base URL update for Higher/Equal versions
                if (result.hasBaseUrl()) {
                    Log.i(TAG, "🔗 [sParking_FLOW] Dynamic Base URL from server: " + result.getBaseUrl());
                    ApiConfig.setBaseUrl(SplashActivity.this, result.getBaseUrl());
                }

                Log.i(TAG, "================================================================================");
                tvStatus.setText("Configuration verified. Launching...");
                navigateToNextScreen();
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.w(TAG, "⚠️ [sParking_FLOW] VersionCheck failed: " + errorMessage + ". Proceeding with cached configuration: " + ApiConfig.getBaseUrl());
                tvStatus.setText("Launching with cached configuration...");
                navigateToNextScreen();
            }
        });
    }

    private void showUpdateRequiredDialog(String serverVersion) {
        String versionDisplay = (serverVersion != null && !serverVersion.isEmpty()) ? serverVersion : "latest";
        new AlertDialog.Builder(this)
                .setTitle("Update Required")
                .setMessage("A new version (" + versionDisplay + ") of S-Parking is available. Please update the app from the Play Store to continue.")
                .setCancelable(false)
                .setPositiveButton("Update Now", (dialog, which) -> {
                    String packageName = getPackageName();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
                    } catch (Exception e) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
                    }
                    finish();
                })
                .show();
    }

    private void showBuildExpiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Build Expired")
                .setMessage("This build has expired. Please install the latest official release.")
                .setCancelable(false)
                .setPositiveButton("Close App", (dialog, which) -> finish())
                .show();
    }

    /**
     * Navigates directly to MainActivity (if user is already logged in) or LoginActivity.
     * Token generation will be called during/after OTP validation or login, NOT on Splash!
     */
    private void navigateToNextScreen() {
        boolean isLoggedIn = SessionManager.getInstance(SplashActivity.this).isLoggedIn();
        Class<?> targetActivity = isLoggedIn ? MainActivity.class : LoginActivity.class;

        Log.i(TAG, "================================================================================");
        Log.i(TAG, "🎯 [sParking_FLOW] Navigating from Splash -> " + targetActivity.getSimpleName());
        Log.i(TAG, "👤 User Session Logged In: " + isLoggedIn);
        Log.i(TAG, "🔑 Note: Token generation deferred to post-OTP verification flow.");
        Log.i(TAG, "================================================================================");

        handler.postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, targetActivity);
            startActivity(intent);
            finish();
        }, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
