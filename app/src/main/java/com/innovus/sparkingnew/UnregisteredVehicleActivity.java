package com.innovus.sparkingnew;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import com.innovus.sparkingnew.printer.ReceiptPrinter;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.innovus.sparkingnew.models.UserData;
import com.innovus.sparkingnew.models.VehicleType;
import com.innovus.sparkingnew.network.BookingNetworkService;
import com.innovus.sparkingnew.session.SessionManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Screen to register and check in an unregistered vehicle (when VEH_001 "Vehicle Not Found" occurs).
 * Modern card UI matching CheckInActivity with clean inputs and dynamic response feedback.
 */
public class UnregisteredVehicleActivity extends AppCompatActivity {

    private static final String TAG = "UnregisteredVehicleAct";

    private ImageView btnBack;
    private TextInputLayout tilMobileNo;
    private TextInputEditText etMobileNo;
    private TextInputLayout tilOwnerName;
    private TextInputEditText etOwnerName;
    private TextInputLayout tilVehicleNo;
    private TextInputEditText etVehicleNo;
    private Spinner spinnerVehicleType;
    private ProgressBar progressLoadingTypes;

    // Response / Error Banner
    private MaterialCardView cardResponseStatus;
    private TextView tvResponseIcon;
    private TextView tvResponseStatusCode;
    private TextView tvResponseMessage;
    private ImageView btnDismissResponse;

    // Action Button
    private MaterialButton btnRegister;
    private ProgressBar progressRegister;

    private SessionManager sessionManager;
    private TokenManager tokenManager;
    private UserData currentUser;

    private final List<VehicleType> vehicleTypeList = new ArrayList<>();
    private ArrayAdapter<VehicleType> spinnerAdapter;
    private int paymentModeId = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_unregistered_vehicle);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.unreg_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupStatusBar();

        sessionManager = SessionManager.getInstance(this);
        tokenManager = TokenManager.getInstance(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentUser = getIntent().getSerializableExtra("USER_DATA", UserData.class);
        } else {
            @SuppressWarnings("deprecation")
            UserData legacy = (UserData) getIntent().getSerializableExtra("USER_DATA");
            currentUser = legacy;
        }

        if (currentUser == null) {
            currentUser = sessionManager.getUserData();
        }

        paymentModeId = getIntent().getIntExtra("PAYMENT_MODE_ID", 1);
        String passedVehicleNo = getIntent().getStringExtra("VEHICLE_NO");
        String passedMobileNo = getIntent().getStringExtra("ALTERNATE_MOBILE");

        initViews();
        setupInputFilters();
        setupListeners();

        if (passedVehicleNo != null && !passedVehicleNo.trim().isEmpty()) {
            etVehicleNo.setText(passedVehicleNo.trim().toUpperCase(Locale.getDefault()));
            etVehicleNo.setSelection(etVehicleNo.getText().length());
        }

        if (passedMobileNo != null && !passedMobileNo.trim().isEmpty() && !passedMobileNo.equals("0000000000")) {
            etMobileNo.setText(passedMobileNo.trim());
        }

        loadVehicleTypes();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        tilMobileNo = findViewById(R.id.til_unreg_mobile);
        etMobileNo = findViewById(R.id.et_unreg_mobile);
        tilOwnerName = findViewById(R.id.til_unreg_owner_name);
        etOwnerName = findViewById(R.id.et_unreg_owner_name);
        tilVehicleNo = findViewById(R.id.til_unreg_vehicle_no);
        etVehicleNo = findViewById(R.id.et_unreg_vehicle_no);
        spinnerVehicleType = findViewById(R.id.spinner_vehicle_type);
        progressLoadingTypes = findViewById(R.id.progress_loading_types);

        cardResponseStatus = findViewById(R.id.card_response_status);
        tvResponseIcon = findViewById(R.id.tv_response_icon);
        tvResponseStatusCode = findViewById(R.id.tv_response_status_code);
        tvResponseMessage = findViewById(R.id.tv_response_message);
        btnDismissResponse = findViewById(R.id.btn_dismiss_response);

        btnRegister = findViewById(R.id.btn_register_vehicle);
        progressRegister = findViewById(R.id.progress_register);

        spinnerAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_vehicle_type, vehicleTypeList);
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_vehicle_type);
        spinnerVehicleType.setAdapter(spinnerAdapter);
    }

    private void setupInputFilters() {
        // Clear errors and auto-capitalize vehicle registration plate
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

        etMobileNo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tilMobileNo.setError(null);
                cardResponseStatus.setVisibility(View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnDismissResponse.setOnClickListener(v -> cardResponseStatus.setVisibility(View.GONE));
        btnRegister.setOnClickListener(v -> performUnregisteredCheckIn());

        tilMobileNo.setEndIconOnClickListener(v -> {
            etMobileNo.setText("0000000000");
            etMobileNo.setSelection(10);
            tilMobileNo.setError(null);
            Toast.makeText(this, "Default mobile 0000000000 applied", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupStatusBar() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#0F172A"));

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
        }
    }

    /**
     * Loads vehicle types from backend API (Two Wheeler, Four Wheeler, etc.).
     */
    private void loadVehicleTypes() {
        if (progressLoadingTypes != null) progressLoadingTypes.setVisibility(View.VISIBLE);
        String bearerToken = tokenManager.getToken();

        BookingNetworkService.getAllVehicleTypes(bearerToken, new BookingNetworkService.VehicleTypesCallback() {
            @Override
            public void onResponse(List<VehicleType> vehicleTypes, String status, String message, boolean isSuccess) {
                if (progressLoadingTypes != null) progressLoadingTypes.setVisibility(View.GONE);

                vehicleTypeList.clear();
                if (isSuccess && vehicleTypes != null && !vehicleTypes.isEmpty()) {
                    vehicleTypeList.addAll(vehicleTypes);
                } else {
                    // Fallback to standard categories if server empty
                    vehicleTypeList.add(new VehicleType(1, "Two Wheeler", ""));
                    vehicleTypeList.add(new VehicleType(2, "Four Wheeler", ""));
                    vehicleTypeList.add(new VehicleType(3, "Heavy Vehicle", ""));
                    vehicleTypeList.add(new VehicleType(4, "Three Wheeler", ""));
                }
                spinnerAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String errorMessage) {
                if (progressLoadingTypes != null) progressLoadingTypes.setVisibility(View.GONE);
                Log.e(TAG, "Failed to load vehicle types: " + errorMessage);
                vehicleTypeList.clear();
                vehicleTypeList.add(new VehicleType(1, "Two Wheeler", ""));
                vehicleTypeList.add(new VehicleType(2, "Four Wheeler", ""));
                vehicleTypeList.add(new VehicleType(3, "Heavy Vehicle", ""));
                vehicleTypeList.add(new VehicleType(4, "Three Wheeler", ""));
                spinnerAdapter.notifyDataSetChanged();
            }
        });
    }

    private void performUnregisteredCheckIn() {
        String mobileNo = etMobileNo.getText() != null ? etMobileNo.getText().toString().trim() : "";
        String vehicleNo = etVehicleNo.getText() != null ? etVehicleNo.getText().toString().trim().toUpperCase(Locale.getDefault()) : "";

        if (mobileNo.isEmpty() || mobileNo.length() < 10) {
            tilMobileNo.setError("Please enter a valid 10-digit mobile number");
            etMobileNo.requestFocus();
            return;
        }

        if (vehicleNo.isEmpty()) {
            tilVehicleNo.setError("Please enter vehicle registration number");
            etVehicleNo.requestFocus();
            return;
        }

        if (vehicleTypeList.isEmpty() || spinnerVehicleType.getSelectedItem() == null) {
            Toast.makeText(this, "Please select vehicle type", Toast.LENGTH_SHORT).show();
            return;
        }

        VehicleType selectedType = (VehicleType) spinnerVehicleType.getSelectedItem();
        int vehicleTypeId = selectedType.getVehicleTypeId();

        if (currentUser == null) {
            currentUser = sessionManager.getUserData();
        }
        int agentId = (currentUser != null) ? currentUser.getAgentId() : 0;
        if (agentId <= 0) {
            Toast.makeText(this, "Agent session expired. Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }

        String bearerToken = tokenManager.getToken();
        setLoading(true);

        String enteredOwner = (etOwnerName != null && etOwnerName.getText() != null)
                ? etOwnerName.getText().toString().trim()
                : "";
        String ownerName = enteredOwner;

        int finalAgentId = agentId > 0 ? agentId : 159;
        String emailId = "deep@gmail.com";
        String address = (currentUser != null && currentUser.getLocation() != null && !currentUser.getLocation().isEmpty())
                ? currentUser.getLocation()
                : "634";
        String alternateMobile = "0000000000";

        Log.i(TAG, "==================== [SUBMITTING UNREGISTERED CHECKIN] ====================");
        Log.i(TAG, "Agent ID: " + finalAgentId);
        Log.i(TAG, "Vehicle No: " + vehicleNo);
        Log.i(TAG, "Vehicle Type ID: " + vehicleTypeId);
        Log.i(TAG, "Owner Name: " + ownerName);
        Log.i(TAG, "Mobile No: " + mobileNo);
        Log.i(TAG, "Email ID: " + emailId);
        Log.i(TAG, "Address: " + address);
        Log.i(TAG, "Payment Mode ID: " + paymentModeId);
        Log.i(TAG, "Alternate Mobile No: " + alternateMobile);
        Log.i(TAG, "Inner enc_data JSON: {\"agent_id\":" + finalAgentId + ",\"vehicle_no\":\"" + vehicleNo + "\",\"vehicle_type_id\":" + vehicleTypeId + ",\"owner_name\":\"" + ownerName + "\",\"mobile_no\":\"" + mobileNo + "\",\"email_id\":\"" + emailId + "\",\"address\":\"" + address + "\",\"payment_mode_id\":" + paymentModeId + ",\"alternate_mobile_no\":\"" + alternateMobile + "\"}");
        Log.i(TAG, "=========================================================================");

        BookingNetworkService.checkinUnregisteredVehicle(
                finalAgentId,
                vehicleNo,
                vehicleTypeId,
                ownerName,
                mobileNo,
                emailId,
                address,
                paymentModeId,
                alternateMobile,
                bearerToken,
                new BookingNetworkService.CheckInCallback() {
                    @Override
                    public void onResponse(String status, String message, String rawData, boolean isSuccess) {
                        setLoading(false);
                        Log.i(TAG, "[UNREGISTERED CHECK-IN RESULT] Status: " + status + " | Message: " + message + " | RawData: " + rawData);

                        showResponseBanner(isSuccess, status, message);

                        if (isSuccess) {
                            showRegistrationSuccessDialog(vehicleNo, message, rawData);
                        } else {
                            Toast.makeText(UnregisteredVehicleActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false);
                        Log.e(TAG, "Registration error: " + errorMessage);
                        showResponseBanner(false, "ERROR", errorMessage);
                        Toast.makeText(UnregisteredVehicleActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    /**
     * Renders modern feedback banner card with status code and backend response message.
     */
    private void showResponseBanner(boolean isSuccess, String status, String message) {
        cardResponseStatus.setVisibility(View.VISIBLE);
        tvResponseStatusCode.setVisibility(View.GONE);

        if (isSuccess) {
            cardResponseStatus.setCardBackgroundColor(Color.parseColor("#ECFDF5")); // Light Emerald
            cardResponseStatus.setStrokeColor(Color.parseColor("#A7F3D0"));
            tvResponseIcon.setText("✅");
            tvResponseMessage.setText(message != null && !message.isEmpty() ? message : "Vehicle registered & checked in successfully");
            tvResponseMessage.setTextColor(Color.parseColor("#047857"));
            btnDismissResponse.setImageTintList(ColorStateList.valueOf(Color.parseColor("#047857")));
        } else {
            cardResponseStatus.setCardBackgroundColor(Color.parseColor("#FEF2F2")); // Light Rose
            cardResponseStatus.setStrokeColor(Color.parseColor("#FECACA"));
            tvResponseIcon.setText("⚠️");
            tvResponseMessage.setText(message != null && !message.isEmpty() ? message : "Unable to register vehicle");
            tvResponseMessage.setTextColor(Color.parseColor("#991B1B"));
            btnDismissResponse.setImageTintList(ColorStateList.valueOf(Color.parseColor("#B91C1C")));
        }
    }

    private void showRegistrationSuccessDialog(String vehicleNo, String message, String rawData) {
        String bookingNo = "";
        String slotName = "";
        String checkinTime = "";

        try {
            if (rawData != null && rawData.trim().startsWith("{")) {
                JSONObject dataObj = new JSONObject(rawData);
                bookingNo = dataObj.optString("booking_no", "");
                slotName = dataObj.optString("slot_name", "");
                checkinTime = dataObj.optString("checkin_time", "");
            }
        } catch (Exception ignored) {}

        final String finalBookingNo = bookingNo;
        final String finalSlotName = !slotName.isEmpty() ? slotName : "General";
        final String finalCheckinTime = !checkinTime.isEmpty() ? checkinTime : "Just now";
        
        VehicleType selectedType = (VehicleType) spinnerVehicleType.getSelectedItem();
        final String finalVehicleType = selectedType != null && selectedType.getVehicleTypeName() != null 
                ? selectedType.getVehicleTypeName() 
                : "Vehicle";
        
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
            MaterialButton btnPrint = dialogView.findViewById(R.id.btn_print_checkin_ticket);
            MaterialButton btnDone = dialogView.findViewById(R.id.btn_done_checkin);

            tvVeh.setText(vehicleNo);
            tvBk.setText(!finalBookingNo.isEmpty() ? finalBookingNo : "N/A");
            tvSl.setText(finalSlotName);
            tvTm.setText(finalCheckinTime);
            tvTyp.setText(finalVehicleType);

            btnPrint.setOnClickListener(v -> {
                btnPrint.setEnabled(false);
                btnPrint.setText("Printing Ticket...");
                ReceiptPrinter.printCheckInTicket(
                        UnregisteredVehicleActivity.this,
                        vehicleNo,
                        finalCheckinTime,
                        finalBookingNo,
                        finalVehicleType,
                        parkingLocation,
                        finalSlotName,
                        (printSuccess, statusMsg) -> {
                            runOnUiThread(() -> {
                                Toast.makeText(UnregisteredVehicleActivity.this, statusMsg != null ? statusMsg : "Ticket printed", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                Intent intent = new Intent(UnregisteredVehicleActivity.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(intent);
                                finish();
                            });
                        }
                );
            });

            btnDone.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(UnregisteredVehicleActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });

            dialog.show();
        } catch (Exception e) {
            Intent intent = new Intent(UnregisteredVehicleActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            btnRegister.setEnabled(false);
            btnRegister.setText("");
            progressRegister.setVisibility(View.VISIBLE);
        } else {
            btnRegister.setEnabled(true);
            btnRegister.setText("REGISTER & CHECK-IN");
            progressRegister.setVisibility(View.GONE);
        }
    }
}
