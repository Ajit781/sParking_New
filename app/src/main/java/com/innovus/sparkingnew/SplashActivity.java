package com.innovus.sparkingnew;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.innovus.sparkingnew.session.SessionManager;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";

    private ProgressBar progressBar;
    private TextView tvStatus;
    private MaterialButton btnRetry;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        // Configure edge-to-edge with light status bar icons for dark royal background
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.splash_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tokenManager = TokenManager.getInstance(this);

        initViews();
        startEntranceAnimation();
        checkTokenAndProceed();
    }

    private void initViews() {
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        btnRetry = findViewById(R.id.btn_retry);

        btnRetry.setOnClickListener(v -> checkTokenAndProceed());
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
     * Checks if existing token is valid. Reuses existing token if not expired.
     * Only fetches a fresh token when expired or missing.
     */
    private void checkTokenAndProceed() {
        progressBar.setVisibility(View.VISIBLE);
        btnRetry.setVisibility(View.GONE);

        if (tokenManager.isTokenValid()) {
            Log.i(TAG, "Existing token is valid! Skipping generate_token. Valid until: " + tokenManager.getExpiresAt());
            tvStatus.setText("Session verified. Launching...");
            handler.postDelayed(this::navigateToNextScreen, 1200);
        } else {
            Log.i(TAG, "Token is expired or not found. Fetching fresh token from server...");
            fetchFreshAuthToken();
        }
    }

    private void fetchFreshAuthToken() {
        tvStatus.setText("Establishing secure POS session...");

        AuthNetworkService.generateToken(new AuthNetworkService.TokenCallback() {
            @Override
            public void onSuccess(String accessToken, String createdAt, String expiresAt, String message) {
                Log.d(TAG, "Fresh token received: " + accessToken);

                tokenManager.saveToken(accessToken, createdAt, expiresAt);

                tvStatus.setText("Session established. Launching...");
                handler.postDelayed(SplashActivity.this::navigateToNextScreen, 1200);
            }

            @Override
            public void onFailure(String errorMessage) {
                Log.e(TAG, "Failed to get token: " + errorMessage);
                progressBar.setVisibility(View.GONE);
                btnRetry.setVisibility(View.VISIBLE);
                tvStatus.setText("Authentication failed:\n" + errorMessage);
                Toast.makeText(SplashActivity.this, "Unable to generate session token", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToNextScreen() {
        Class<?> targetActivity = SessionManager.getInstance(SplashActivity.this).isLoggedIn()
                ? MainActivity.class
                : LoginActivity.class;
        Intent intent = new Intent(SplashActivity.this, targetActivity);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
