package com.innovus.sparkingnew;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.innovus.sparkingnew.scanner.VehicleScannerActivity;
import android.view.Window;
import android.view.WindowManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.innovus.sparkingnew.models.UserData;
import com.innovus.sparkingnew.network.BookingNetworkService;
import com.innovus.sparkingnew.printer.ReceiptPrinter;
import com.innovus.sparkingnew.session.SessionManager;
import com.innovus.sparkingnew.utils.PosDeviceHelper;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Modern Vehicle Check-In Activity.
 * Designed with a clean Light Material 3 aesthetic,
 * handling Vehicle Registration, Special Pass, Alternate Phone, and exact backend error reporting.
 */
public class CheckInActivity extends AppCompatActivity {

    private static final String TAG = "CheckInActivity";

    // Header & Navigation
    private ImageView btnBack;

    // Response / Error Banner
    private MaterialCardView cardResponseStatus;
    private TextView tvResponseIcon;
    private TextView tvResponseStatusCode;
    private TextView tvResponseMessage;
    private ImageView btnDismissResponse;

    // Form Inputs
    private TextInputLayout tilVehicleNo;
    private TextInputEditText etVehicleNo;
    private MaterialButton btnScan;
    private MaterialCheckBox cbSpecialPass;
    private TextInputLayout tilAlternateMobile;
    private TextInputEditText etAlternateMobile;

    // Action Button
    private MaterialButton btnNext;
    private ProgressBar progressNext;

    // Dependencies
    private SessionManager sessionManager;
    private TokenManager tokenManager;
    private UserData currentUser;

    // Camera Scanner Launchers
    private static final int RC_VEHICLE_SCAN = 2001;
    private ActivityResultLauncher<Intent> scannerLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_check_in);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.check_in_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupStatusBar();
        initScannerLaunchers();

        sessionManager = SessionManager.getInstance(this);
        tokenManager = TokenManager.getInstance(this);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            currentUser = getIntent().getSerializableExtra("USER_DATA", UserData.class);
        } else {
            @SuppressWarnings("deprecation")
            UserData legacy = (UserData) getIntent().getSerializableExtra("USER_DATA");
            currentUser = legacy;
        }
        if (currentUser == null) {
            currentUser = sessionManager.getUserData();
        }

        initViews();
        setupVehicleInputFilter();
        setupListeners();
    }

    /**
     * Initializes Activity Result launchers for camera permission and the Vehicle Plate Scanner.
     */
    private void initScannerLaunchers() {
        scannerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.i("CHECK_IN_SCAN", "scannerLauncher callback: resultCode=" + result.getResultCode());
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleScanResultData(result.getData());
                    } else {
                        Log.w("CHECK_IN_SCAN", "scannerLauncher returned non-OK resultCode: " + result.getResultCode());
                    }
                }
        );

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    Log.i("CHECK_IN_SCAN", "Camera permission granted: " + isGranted);
                    if (isGranted) {
                        launchVehicleScanner();
                    } else {
                        Toast.makeText(this, "Camera permission is required to scan number plates", Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    /**
     * Extracts and populates the scanned vehicle number into the EditText with full logging.
     */
    private void handleScanResultData(Intent data) {
        if (data == null) {
            Log.e("CHECK_IN_SCAN", "handleScanResultData: intent data is null!");
            return;
        }
        String scannedPlate = data.getStringExtra(VehicleScannerActivity.EXTRA_VEHICLE_NO);
        String scannedMobile = data.getStringExtra(VehicleScannerActivity.EXTRA_MOBILE_NO);
        Log.i("CHECK_IN_SCAN", "handleScanResultData: plate=[" + scannedPlate + "], mobile=[" + scannedMobile + "]");

        if (scannedPlate != null && !scannedPlate.trim().isEmpty()) {
            String clean = scannedPlate.trim().toUpperCase(Locale.getDefault());
            Log.i("CHECK_IN_SCAN", "etVehicleNo BEFORE setText: [" + etVehicleNo.getText() + "]");
            etVehicleNo.setText(clean);
            etVehicleNo.setSelection(clean.length());
            tilVehicleNo.setError(null);
            cardResponseStatus.setVisibility(View.GONE);
            Toast.makeText(this, "Scanned: " + clean, Toast.LENGTH_SHORT).show();
            Log.i("CHECK_IN_SCAN", "etVehicleNo AFTER setText: [" + etVehicleNo.getText() + "]");
        } else {
            Log.w("CHECK_IN_SCAN", "handleScanResultData: scannedPlate was empty or null!");
        }

        if (scannedMobile != null && !scannedMobile.trim().isEmpty() && etAlternateMobile != null) {
            etAlternateMobile.setText(scannedMobile.trim());
        }
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);

        cardResponseStatus = findViewById(R.id.card_response_status);
        tvResponseIcon = findViewById(R.id.tv_response_icon);
        tvResponseStatusCode = findViewById(R.id.tv_response_status_code);
        tvResponseMessage = findViewById(R.id.tv_response_message);
        btnDismissResponse = findViewById(R.id.btn_dismiss_response);

        tilVehicleNo = findViewById(R.id.til_vehicle_no);
        etVehicleNo = findViewById(R.id.et_vehicle_no);
        btnScan = findViewById(R.id.btn_scan);
        cbSpecialPass = findViewById(R.id.cb_special_pass);
        tilAlternateMobile = findViewById(R.id.til_alternate_mobile);
        etAlternateMobile = findViewById(R.id.et_alternate_mobile);

        btnNext = findViewById(R.id.btn_next);
        progressNext = findViewById(R.id.progress_next);
    }

    private void setupVehicleInputFilter() {
        // Ensure cursor is placed at end of default "WB" text
        if (etVehicleNo.getText() != null) {
            etVehicleNo.setSelection(etVehicleNo.getText().length());
        }

        // Auto uppercase and clear errors on typing
        etVehicleNo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilVehicleNo.setError(null);
                cardResponseStatus.setVisibility(View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                String str = s.toString();
                String upper = str.toUpperCase(Locale.getDefault());
                if (!str.equals(upper)) {
                    int selection = etVehicleNo.getSelectionStart();
                    etVehicleNo.setText(upper);
                    etVehicleNo.setSelection(Math.min(selection, upper.length()));
                }
            }
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnDismissResponse.setOnClickListener(v -> cardResponseStatus.setVisibility(View.GONE));

        btnScan.setOnClickListener(v -> startScanning());

        btnNext.setOnClickListener(v -> performCheckIn());

        tilAlternateMobile.setEndIconOnClickListener(v -> {
            etAlternateMobile.setText("0000000000");
            etAlternateMobile.setSelection(10);
            tilAlternateMobile.setError(null);
            Toast.makeText(this, "Default mobile 0000000000 applied", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Checks camera permission and launches the real-time OCR vehicle scanner.
     */
    private void startScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchVehicleScanner();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchVehicleScanner() {
        Log.i("CHECK_IN_SCAN", "launchVehicleScanner: Starting VehicleScannerActivity");
        Intent intent = new Intent(this, VehicleScannerActivity.class);
        try {
            scannerLauncher.launch(intent);
        } catch (Exception e) {
            Log.w("CHECK_IN_SCAN", "scannerLauncher failed, using startActivityForResult", e);
            startActivityForResult(intent, RC_VEHICLE_SCAN);
        }
    }

    /**
     * Executes the checkinRegisteredVehicle API call.
     */
    private void performCheckIn() {
        String vehicleNo = (etVehicleNo.getText() != null)
                ? etVehicleNo.getText().toString().trim().toUpperCase(Locale.getDefault())
                : "";
        String alternateMobile = (etAlternateMobile.getText() != null)
                ? etAlternateMobile.getText().toString().trim()
                : "";

        // Validate vehicle number
        if (vehicleNo.isEmpty() || vehicleNo.length() < 3) {
            tilVehicleNo.setError("Please enter a valid vehicle number");
            etVehicleNo.requestFocus();
            return;
        }

        // Default mobile to "0000000000" if left empty
        if (alternateMobile.isEmpty()) {
            alternateMobile = "0000000000";
        }

        int paymentModeId = cbSpecialPass.isChecked() ? 4 : 1;
        if (currentUser == null) {
            currentUser = sessionManager.getUserData();
        }
        int agentId = (currentUser != null) ? currentUser.getAgentId() : 0;
        if (agentId <= 0) {
            showResponseBanner(false, "SESSION_ERR", "Agent session not found. Please log in again.");
            return;
        }
        String bearerToken = tokenManager.getToken();

        if (bearerToken == null || bearerToken.trim().isEmpty()) {
            showResponseBanner(false, "AUTH_ERR", "Authentication token missing. Please log in again.");
            return;
        }

        setLoading(true);

        Log.i(TAG, "Starting Check-In -> Vehicle: " + vehicleNo
                + ", PaymentMode: " + paymentModeId
                + ", AlternateMobile: " + alternateMobile
                + ", AgentId: " + agentId);

        final String finalVehicleNo = vehicleNo;
        final String finalMobile = alternateMobile;

        BookingNetworkService.checkinRegisteredVehicle(
                agentId,
                vehicleNo,
                paymentModeId,
                alternateMobile,
                bearerToken,
                new BookingNetworkService.CheckInCallback() {
                    @Override
                    public void onResponse(String status, String message, String rawData, boolean isSuccess) {
                        setLoading(false);
                        Log.i(TAG, "API Response received -> Status: " + status + " | Message: " + message + " | Success: " + isSuccess);

                        boolean checkinSucceeded = isSuccess
                                || "CHKIN_001".equalsIgnoreCase(status)
                                || "CHKIN_000".equalsIgnoreCase(status)
                                || (message != null && message.toLowerCase().contains("success"));

                        // Display the exact backend response message and status code
                        showResponseBanner(checkinSucceeded, status, message);

                        if (checkinSucceeded) {
                            showSuccessDialog(finalVehicleNo, status, message, rawData);
                        } else if ("VEH_001".equalsIgnoreCase(status) || (message != null && message.toLowerCase().contains("not found"))) {
                            // Show confirmation dialog before navigating to registration screen
                            showUnregisteredVehiclePromptDialog(finalVehicleNo, finalMobile, paymentModeId, message);
                        } else {
                            // Show Toast with exact backend error message
                            Toast.makeText(CheckInActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false);
                        Log.e(TAG, "API Error: " + errorMessage);
                        showResponseBanner(false, "ERROR", errorMessage);
                        Toast.makeText(CheckInActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    /**
     * Renders modern feedback card with exact status code and backend response message.
     */
    private void showResponseBanner(boolean isSuccess, String status, String message) {
        cardResponseStatus.setVisibility(View.VISIBLE);
        tvResponseStatusCode.setVisibility(View.GONE);

        if (isSuccess) {
            cardResponseStatus.setCardBackgroundColor(Color.parseColor("#ECFDF5")); // Light Emerald
            cardResponseStatus.setStrokeColor(Color.parseColor("#A7F3D0"));
            tvResponseIcon.setText("✅");
            tvResponseMessage.setText(message != null && !message.isEmpty() ? message : "Vehicle checked in successfully");
            tvResponseMessage.setTextColor(Color.parseColor("#047857"));
            btnDismissResponse.setImageTintList(ColorStateList.valueOf(Color.parseColor("#047857")));
        } else {
            cardResponseStatus.setCardBackgroundColor(Color.parseColor("#FEF2F2")); // Light Rose
            cardResponseStatus.setStrokeColor(Color.parseColor("#FECACA"));
            tvResponseIcon.setText("⚠️");
            tvResponseMessage.setText(message != null && !message.isEmpty() ? message : "Unable to process check-in");
            tvResponseMessage.setTextColor(Color.parseColor("#991B1B"));
            btnDismissResponse.setImageTintList(ColorStateList.valueOf(Color.parseColor("#B91C1C")));
        }
    }

    /**
     * Modal dialog confirming successful check-in with Print Ticket action.
     */
    private void showSuccessDialog(String vehicleNo, String status, String message, String rawData) {
        String bookingNo = "";
        String slotName = "";
        String checkinTime = "";
        String vehicleType = "Vehicle";

        try {
            if (rawData != null && !rawData.isEmpty()) {
                JSONObject dataObj = new JSONObject(rawData);
                bookingNo = dataObj.optString("booking_no", "");
                slotName = dataObj.optString("slot_name", "");
                checkinTime = dataObj.optString("checkin_time", "");
                vehicleType = dataObj.optString("vehicle_type_name", "Vehicle");
            }
        } catch (Exception ignored) {}

        final String finalBookingNo = bookingNo;
        final String finalSlotName = !slotName.isEmpty() ? slotName : "General";
        final String finalCheckinTime = !checkinTime.isEmpty() ? checkinTime : "Just now";
        final String finalVehicleType = vehicleType;
        final String parkingLocation = (currentUser != null && currentUser.getLocation() != null && !currentUser.getLocation().isEmpty())
                ? currentUser.getLocation()
                : "sParking Station";

        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_checkin_success, null);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            TextView tvVeh = dialogView.findViewById(R.id.tv_checkin_dialog_vehicle_no);
            TextView tvBk = dialogView.findViewById(R.id.tv_checkin_dialog_booking_no);
            TextView tvSl = dialogView.findViewById(R.id.tv_checkin_dialog_slot);
            TextView tvTm = dialogView.findViewById(R.id.tv_checkin_dialog_time);
            TextView tvTyp = dialogView.findViewById(R.id.tv_checkin_dialog_type);
            com.google.android.material.button.MaterialButton btnPrint = dialogView.findViewById(R.id.btn_print_checkin_ticket);
            com.google.android.material.button.MaterialButton btnDone = dialogView.findViewById(R.id.btn_done_checkin);

            tvVeh.setText(vehicleNo);
            tvBk.setText(!finalBookingNo.isEmpty() ? finalBookingNo : "N/A");
            tvSl.setText(finalSlotName);
            tvTm.setText(finalCheckinTime);
            tvTyp.setText(finalVehicleType);

            btnPrint.setOnClickListener(v -> {
                btnPrint.setEnabled(false);
                btnPrint.setText("Printing Ticket...");
                ReceiptPrinter.printCheckInTicket(
                        CheckInActivity.this,
                        vehicleNo,
                        finalCheckinTime,
                        finalBookingNo,
                        finalVehicleType,
                        parkingLocation,
                        finalSlotName,
                        (printSuccess, statusMsg) -> {
                            runOnUiThread(() -> {
                                Toast.makeText(CheckInActivity.this, statusMsg != null ? statusMsg : "Ticket printed", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                finish();
                            });
                        }
                );
            });

            btnDone.setOnClickListener(v -> {
                dialog.dismiss();
                finish();
            });

            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing checkin success dialog: " + e.getMessage());
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i("CHECK_IN_SCAN", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        if (requestCode == RC_VEHICLE_SCAN && resultCode == RESULT_OK && data != null) {
            Log.i("CHECK_IN_SCAN", "onActivityResult matched RC_VEHICLE_SCAN with RESULT_OK");
            handleScanResultData(data);
        }
        // Delegate EzeAPI print result to ReceiptPrinter — it will call the stored callback
        ReceiptPrinter.handlePrintActivityResult(requestCode, resultCode, data);
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            btnNext.setEnabled(false);
            btnNext.setText("");
            progressNext.setVisibility(View.VISIBLE);
        } else {
            btnNext.setEnabled(true);
            btnNext.setText("Issue Check-In Ticket");
            progressNext.setVisibility(View.GONE);
        }
    }

    /**
     * Prompts the user with an attractive confirmation dialog when an unregistered vehicle is detected,
     * allowing them to review the vehicle number and click "OK, Register" before navigating to UnregisteredVehicleActivity.
     */
    private void showUnregisteredVehiclePromptDialog(String vehicleNo, String alternateMobile, int paymentModeId, String serverMsg) {
        if (isFinishing() || isDestroyed()) return;

        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_unregistered_vehicle_prompt, null);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(true)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            TextView tvVeh = dialogView.findViewById(R.id.tv_unreg_dialog_vehicle_no);
            TextView tvMsg = dialogView.findViewById(R.id.tv_unreg_dialog_message);
            MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_unreg);
            MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm_register);

            tvVeh.setText(vehicleNo != null ? vehicleNo : "");

            if (serverMsg != null && !serverMsg.trim().isEmpty()) {
                tvMsg.setText(serverMsg.trim() + "\n\nWould you like to register this vehicle now and generate a check-in ticket?");
            }

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(CheckInActivity.this, UnregisteredVehicleActivity.class);
                intent.putExtra("VEHICLE_NO", vehicleNo);
                intent.putExtra("ALTERNATE_MOBILE", alternateMobile);
                intent.putExtra("PAYMENT_MODE_ID", paymentModeId);
                if (currentUser != null) {
                    intent.putExtra("USER_DATA", currentUser);
                }
                startActivity(intent);
            });

            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing unregistered vehicle dialog: " + e.getMessage(), e);
            // Fallback navigation in case of dialog error
            Intent intent = new Intent(CheckInActivity.this, UnregisteredVehicleActivity.class);
            intent.putExtra("VEHICLE_NO", vehicleNo);
            intent.putExtra("ALTERNATE_MOBILE", alternateMobile);
            intent.putExtra("PAYMENT_MODE_ID", paymentModeId);
            if (currentUser != null) {
                intent.putExtra("USER_DATA", currentUser);
            }
            startActivity(intent);
        }
    }

    private void setupStatusBar() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#0F172A"));

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false); // Light (white) text & icons for dark status bar
        }
    }
}
