package com.innovus.sparkingnew;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.view.LayoutInflater;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.innovus.sparkingnew.models.UserData;
import com.innovus.sparkingnew.network.LoginNetworkService;
import com.innovus.sparkingnew.session.SessionManager;
import com.innovus.sparkingnew.utils.PosDeviceHelper;
import com.eze.api.EzeAPI;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressLogin;
    private CheckBox cbRememberMe;
    private LinearLayout layoutErrorBanner;
    private TextView tvBackendError;
    private ImageView btnDismissError;
    private TextView tvDeviceInfoPill;

    private AlertDialog loginProgressDialog;
    private TextView tvDialogTitle;
    private TextView tvDialogWelcomeName;
    private TextView tvDialogMessage;
    private ProgressBar pbLoginLoader;
    private View layoutSuccessBadge;
    private View layoutLoginDetails;
    private TextView tvDialogStationLocation;
    private TextView tvDialogDeviceInfo;
    private ProgressBar pbRedirectBar;
    private MaterialButton btnDialogContinue;
    private TextView tvRedirectHint;
    private Handler navigationHandler;
    private Runnable navigateRunnable;

    private SessionManager sessionManager;
    private TokenManager tokenManager;
    private String detectedDeviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupStatusBar();

        sessionManager = SessionManager.getInstance(this);
        tokenManager = TokenManager.getInstance(this);

        initViews();
        initDevice();
        initializeEzeAPI();
    }

    private void initializeEzeAPI() {
        try {
            org.json.JSONObject jsonRequest = new org.json.JSONObject();

            // DEMO Mode Credentials (Active)
            jsonRequest.put("demoAppKey", "8def92fb-4aac-4810-bbfc-091ac5f96fa6");
            jsonRequest.put("prodAppKey", "8def92fb-4aac-4810-bbfc-091ac5f96fa6");

            jsonRequest.put("merchantName", "KOLKATA_MUNICIPAL_CORPORA");
            jsonRequest.put("userName", "2222600680");
            jsonRequest.put("currencyCode", "INR");
            jsonRequest.put("appMode", "DEMO");
            jsonRequest.put("captureSignature", "false");
            jsonRequest.put("prepareDevice", "false");


            // PRODUCTION Mode Credentials (Commented out)
//            jsonRequest.put("prodAppKey", "c21188a2-dded-46f4-810a-95cdad9ef996");
//            jsonRequest.put("merchantName", "PIONEER_CO_OPERTIVE_CAR_P");
//            jsonRequest.put("userName", "9831092712");
//            jsonRequest.put("currencyCode", "INR");
//            jsonRequest.put("appMode", "PROD");
//            jsonRequest.put("captureSignature", "false");
//            jsonRequest.put("prepareDevice", "false");


            Log.i(TAG, "======================================================================");
            Log.i(TAG, "🚀 [EZEAPI / RAZORPAY INIT REQUEST] (LoginActivity)");
            Log.i(TAG, "Request Code: 10001");
            Log.i(TAG, "App Mode: " + jsonRequest.optString("appMode"));
            Log.i(TAG, "Merchant Name: " + jsonRequest.optString("merchantName"));
            Log.i(TAG, "User Name: " + jsonRequest.optString("userName"));
            Log.i(TAG, "App Key: " + (jsonRequest.has("demoAppKey") ? jsonRequest.optString("demoAppKey") : jsonRequest.optString("prodAppKey")));
            Log.i(TAG, "Full JSON Payload:\n" + jsonRequest.toString(2));
            Log.i(TAG, "======================================================================");

            EzeAPI.initialize(this, 10001, jsonRequest);
        } catch (Exception e) {
            Log.e(TAG, "[EZEAPI INIT EXCEPTION] in LoginActivity: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 10001) {
            Log.i(TAG, "======================================================================");
            Log.i(TAG, "[EZEAPI / RAZORPAY INIT RESPONSE] (LoginActivity)");
            Log.i(TAG, "Request Code: " + requestCode);
            Log.i(TAG, "Result Code: " + resultCode + " (" + (resultCode == RESULT_OK ? "RESULT_OK" : (resultCode == RESULT_CANCELED ? "RESULT_CANCELED" : "OTHER")) + ")");

            if (data == null) {
                Log.e(TAG, "Data Intent: NULL (Terminal returned no intent data)");
            } else {
                android.os.Bundle extras = data.getExtras();
                if (extras != null) {
                    Log.i(TAG, "Intent Extras Keys: " + extras.keySet().toString());
                    for (String key : extras.keySet()) {
                        Log.i(TAG, "  -> Extra [" + key + "]: " + extras.get(key));
                    }
                }

                if (data.hasExtra("response")) {
                    String rawResp = data.getStringExtra("response");
                    Log.i(TAG, "Raw Response String:\n" + rawResp);
                    try {
                        org.json.JSONObject respObj = new org.json.JSONObject(rawResp);
                        Log.i(TAG, "Formatted Response JSON:\n" + respObj.toString(2));

                        if (resultCode == RESULT_OK) {
                            Log.i(TAG, "EzeAPI Initialize SUCCESS!");
                        } else {
                            org.json.JSONObject err = respObj.optJSONObject("error");
                            if (err != null) {
                                Log.e(TAG, " EzeAPI Error Code: " + err.optString("code"));
                                Log.e(TAG, "EzeAPI Error Message: " + err.optString("message"));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse response JSON: " + e.getMessage());
                    }
                } else {
                    Log.w(TAG, "Intent has NO 'response' extra!");
                }
            }
            Log.i(TAG, "======================================================================");
        }
    }

    private void initViews() {
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        progressLogin = findViewById(R.id.progress_login);
        cbRememberMe = findViewById(R.id.cb_remember_me);
        layoutErrorBanner = findViewById(R.id.layout_error_banner);
        tvBackendError = findViewById(R.id.tv_backend_error);
        btnDismissError = findViewById(R.id.btn_dismiss_error);
        tvDeviceInfoPill = findViewById(R.id.tv_device_info_pill);

        btnDismissError.setOnClickListener(v -> layoutErrorBanner.setVisibility(View.GONE));
        btnLogin.setOnClickListener(v -> performLogin());

        // Fields remain blank for manual entry as requested
        etUsername.setText("");
        etPassword.setText("");
    }

    private void initDevice() {
        // Automatically fetch device ID from Pax A910s hardware / system property
        detectedDeviceId = PosDeviceHelper.getDeviceId(this);
        Log.i(TAG, "Initialized POS Device ID: " + detectedDeviceId);

        if (tvDeviceInfoPill != null) {
            tvDeviceInfoPill.setText("Terminal: " + PosDeviceHelper.getDeviceDisplayInfo(this));
        }
    }

    private void performLogin() {
        hideErrorBanner();

        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        // Client validations
        if (username.isEmpty()) {
            showBackendError("Please enter username.");
            etUsername.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            showBackendError("Please enter password.");
            etPassword.requestFocus();
            return;
        }

        setLoading(true);
        showLoginLoaderDialog();

        // Ensure we have a valid hardware device ID
        if (detectedDeviceId == null || detectedDeviceId.isEmpty()) {
            detectedDeviceId = PosDeviceHelper.getDeviceId(this);
        }

        String bearerToken = tokenManager.getToken();
        Log.d(TAG, "Performing login -> Username: " + username + ", DeviceID: " + detectedDeviceId);

        LoginNetworkService.executeLogin(username, password, "0020797790", bearerToken, new LoginNetworkService.LoginCallback() {
            @Override
            public void onSuccess(UserData userData, String rawData, String message) {
                setLoading(false);
                Log.i(TAG, "Login successful: " + message);

                sessionManager.saveUserSession(userData, username, detectedDeviceId);
                updateLoginDialogSuccess(userData, message);
            }

            @Override
            public void onFailure(String backendErrorMessage) {
                setLoading(false);
                dismissLoginDialog();
                Log.e(TAG, "Login failed: " + backendErrorMessage);

                // Show EXACT error message returned by backend
                showBackendError(backendErrorMessage);
                Toast.makeText(LoginActivity.this, backendErrorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoginLoaderDialog() {
        if (isFinishing() || isDestroyed()) return;

        if (navigationHandler != null && navigateRunnable != null) {
            navigationHandler.removeCallbacks(navigateRunnable);
        }

        if (loginProgressDialog == null) {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_login_success, null);

            tvDialogTitle = dialogView.findViewById(R.id.tv_dialog_title);
            tvDialogWelcomeName = dialogView.findViewById(R.id.tv_dialog_welcome_name);
            tvDialogMessage = dialogView.findViewById(R.id.tv_dialog_message);
            pbLoginLoader = dialogView.findViewById(R.id.pb_login_loader);
            layoutSuccessBadge = dialogView.findViewById(R.id.layout_success_badge);
            layoutLoginDetails = dialogView.findViewById(R.id.layout_login_details);
            tvDialogStationLocation = dialogView.findViewById(R.id.tv_dialog_station_location);
            tvDialogDeviceInfo = dialogView.findViewById(R.id.tv_dialog_device_info);
            pbRedirectBar = dialogView.findViewById(R.id.pb_redirect_bar);
            btnDialogContinue = dialogView.findViewById(R.id.btn_dialog_continue);
            tvRedirectHint = dialogView.findViewById(R.id.tv_redirect_hint);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(dialogView);
            builder.setCancelable(false);

            loginProgressDialog = builder.create();
            if (loginProgressDialog.getWindow() != null) {
                loginProgressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        }

        // Setup for Loading state
        pbLoginLoader.setVisibility(View.VISIBLE);
        layoutSuccessBadge.setVisibility(View.GONE);
        layoutLoginDetails.setVisibility(View.GONE);
        btnDialogContinue.setVisibility(View.GONE);

        tvDialogTitle.setText("Signing in...");
        tvDialogWelcomeName.setText("Please wait");
        tvDialogWelcomeName.setTextColor(ContextCompat.getColor(this, R.color.primary));
        tvDialogMessage.setText("Connecting to server");
        tvRedirectHint.setText("Authenticating...");
        pbRedirectBar.setVisibility(View.VISIBLE);

        if (!loginProgressDialog.isShowing()) {
            loginProgressDialog.show();
        }
    }

    private void updateLoginDialogSuccess(UserData userData, String message) {
        if (isFinishing() || isDestroyed()) return;

        // Ensure dialog is active
        if (loginProgressDialog == null || !loginProgressDialog.isShowing()) {
            showLoginLoaderDialog();
        }

        // Switch loader to success badge
        pbLoginLoader.setVisibility(View.GONE);
        layoutSuccessBadge.setVisibility(View.VISIBLE);

        // Animate success badge
        layoutSuccessBadge.setScaleX(0.3f);
        layoutSuccessBadge.setScaleY(0.3f);
        layoutSuccessBadge.setAlpha(0f);
        layoutSuccessBadge.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(350)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .start();

        tvDialogTitle.setText("Login Successful");

        String agentName = (userData != null && userData.getAgentName() != null && !userData.getAgentName().trim().isEmpty())
                ? userData.getAgentName().trim()
                : "Operator";
        tvDialogWelcomeName.setText("Welcome, " + agentName);
        tvDialogWelcomeName.setTextColor(ContextCompat.getColor(this, R.color.accent));

        String displayMsg = (message != null && !message.trim().isEmpty())
                ? message.trim()
                : "Authenticated successfully";
        tvDialogMessage.setText(displayMsg);

        // Populate station and terminal info
        String location = (userData != null && userData.getLocation() != null && !userData.getLocation().trim().isEmpty())
                ? userData.getLocation().trim()
                : "Parking Zone";
        tvDialogStationLocation.setText("Station: " + location);

        String terminalId = (detectedDeviceId != null && !detectedDeviceId.isEmpty())
                ? detectedDeviceId
                : (userData != null && userData.getDeviceUid() != null ? userData.getDeviceUid() : "POS Terminal");
        tvDialogDeviceInfo.setText("Terminal: " + terminalId);
        layoutLoginDetails.setVisibility(View.VISIBLE);

        pbRedirectBar.setVisibility(View.VISIBLE);
        tvRedirectHint.setText("Redirecting...");
        btnDialogContinue.setVisibility(View.VISIBLE);

        navigateRunnable = () -> {
            dismissLoginDialog();
            if (!isFinishing() && !isDestroyed()) {
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("USER_DATA", userData);
                startActivity(intent);
                finish();
            }
        };

        if (navigationHandler == null) {
            navigationHandler = new Handler(Looper.getMainLooper());
        } else {
            navigationHandler.removeCallbacksAndMessages(null);
        }

        navigationHandler.postDelayed(navigateRunnable, 1100);

        btnDialogContinue.setOnClickListener(v -> {
            if (navigationHandler != null && navigateRunnable != null) {
                navigationHandler.removeCallbacks(navigateRunnable);
            }
            navigateRunnable.run();
        });
    }

    private void dismissLoginDialog() {
        try {
            if (navigationHandler != null && navigateRunnable != null) {
                navigationHandler.removeCallbacks(navigateRunnable);
            }
            if (loginProgressDialog != null && loginProgressDialog.isShowing()) {
                loginProgressDialog.dismiss();
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        dismissLoginDialog();
        super.onDestroy();
    }

    private void showBackendError(String message) {
        layoutErrorBanner.setVisibility(View.VISIBLE);
        tvBackendError.setText(message);
    }

    private void hideErrorBanner() {
        layoutErrorBanner.setVisibility(View.GONE);
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            btnLogin.setEnabled(false);
            btnLogin.setText("");
            progressLogin.setVisibility(View.VISIBLE);
        } else {
            btnLogin.setEnabled(true);
            btnLogin.setText("Sign In");
            progressLogin.setVisibility(View.GONE);
        }
    }

    private void setupStatusBar() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(android.graphics.Color.parseColor("#09132C"));

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
        }
    }
}
