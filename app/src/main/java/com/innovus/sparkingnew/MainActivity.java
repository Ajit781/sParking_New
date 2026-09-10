package com.innovus.sparkingnew;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import android.view.Window;
import android.view.WindowManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.innovus.sparkingnew.adapters.VehicleAdapter;
import com.innovus.sparkingnew.models.ParkedVehicle;
import com.innovus.sparkingnew.models.UserData;
import com.innovus.sparkingnew.network.BookingNetworkService;
import com.innovus.sparkingnew.payment.PosPaymentManager;
import com.innovus.sparkingnew.printer.ReceiptPrinter;
import com.innovus.sparkingnew.session.SessionManager;
import com.innovus.sparkingnew.utils.PosDeviceHelper;
import com.eze.api.EzeAPI;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements VehicleAdapter.OnVehicleActionListener {

    private static final String TAG = "MainActivity";

    // Drawer & Navigation
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageButton btnMenu;
    private ImageButton btnRefresh;
    private TextView tvNavbarSubtitle;
    private TextView tvNavbarDatetime;

    // Drawer Header Views (Original UI design)
    private TextView tvDrawerAvatarInitials;
    private TextView tvDrawerAgentName;
    private TextView tvDrawerAgentIdAgency;
    private TextView tvDrawerLocation;
    private TextView tvDrawerTerminal;
    private TextView tvDrawerRates;

    // Dashboard Views
    private EditText etSearchVehicles;
    private ImageView btnClearSearch;
    private TextView tvVehiclesCount;
    private ProgressBar progressVehicles;
    private LinearLayout layoutLoadingVehicles;
    private View layoutEmptyState;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private RecyclerView rvParkedVehicles;
    private ExtendedFloatingActionButton fabCheckIn;

    // Data & Services
    private SessionManager sessionManager;
    private TokenManager tokenManager;
    private UserData currentUser;
    private final List<ParkedVehicle> allVehicleList = new ArrayList<>();
    private final List<ParkedVehicle> vehicleList = new ArrayList<>();
    private VehicleAdapter vehicleAdapter;

    private double twoWheelerRate = 0.0;
    private double fourWheelerRate = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Window Insets for status bar padding on navbar
        View navbarLayout = findViewById(R.id.navbar_layout);
        if (navbarLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(navbarLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), systemBars.top + 8, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        sessionManager = SessionManager.getInstance(this);
        tokenManager = TokenManager.getInstance(this);

        initViews();
        setupStatusBar();
        setupNavigationDrawer();
        setupBackPressHandler();
        loadAgentProfile();
        updateNavbarDateTime();
        setupRecyclerView();
        setupFloatingCheckInButton();
        initializeEzeAPI();
    }

    /** EzeAPI (Razorpay / PineLabs) initialization — same as SParkingAgent DashBoardActivity */
    private void initializeEzeAPI() {
        try {
            org.json.JSONObject jsonRequest = new org.json.JSONObject();

            // DEMO Mode Credentials (Active for testing)
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


            Log.i(TAG, "=====================================================================");
            Log.i(TAG, "🚀 [EZEAPI / RAZORPAY INIT REQUEST] (MainActivity)");
            Log.i(TAG, "Calling Package Name: " + getPackageName());
            Log.i(TAG, "Request Code: 10001");
            Log.i(TAG, "App Mode: " + jsonRequest.optString("appMode"));
            Log.i(TAG, "Merchant Name: " + jsonRequest.optString("merchantName"));
            Log.i(TAG, "User Name: " + jsonRequest.optString("userName"));
            Log.i(TAG, "App Key: " + (jsonRequest.has("demoAppKey") ? jsonRequest.optString("demoAppKey") : jsonRequest.optString("prodAppKey")));
            Log.i(TAG, "Full JSON Payload:\n" + jsonRequest.toString(2));
            Log.i(TAG, "======================================================================");

            EzeAPI.initialize(this, 10001, jsonRequest);
        } catch (Exception e) {
            Log.e(TAG, "❌ [EZEAPI INIT EXCEPTION] in MainActivity: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 1. Delegate to PosPaymentManager for PineLabs / EzeAPI payment response (REQUEST_CODE_PAY)
        if (PosPaymentManager.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }

        // 2. EzeAPI Initialization callback
        if (requestCode == 10001) {
            Log.i(TAG, "======================================================================");
            Log.i(TAG, "📥 [EZEAPI / RAZORPAY INIT RESPONSE] (MainActivity)");
            Log.i(TAG, "Request Code: " + requestCode);
            Log.i(TAG, "Result Code: " + resultCode + " (" + (resultCode == RESULT_OK ? "RESULT_OK (-1)" : (resultCode == RESULT_CANCELED ? "RESULT_CANCELED (0)" : "OTHER (" + resultCode + ")")) + ")");

            if (data == null) {
                Log.e(TAG, "Data Intent: NULL (Terminal returned no intent data)");
            } else {
                android.os.Bundle extras = data.getExtras();
                if (extras != null) {
                    Log.i(TAG, "Intent Extras Keys Count: " + extras.keySet().size());
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
                            Log.i(TAG, "✅ [EZEAPI INIT SUCCESS] " + respObj.optJSONObject("result"));
                        } else {
                            org.json.JSONObject err = respObj.optJSONObject("error");
                            if (err != null) {
                                Log.e(TAG, "❌ [EZEAPI INIT ERROR CODE]: " + err.optString("code"));
                                Log.e(TAG, "❌ [EZEAPI INIT ERROR MESSAGE]: " + err.optString("message"));
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
            return;
        }

        // 3. Delegate print result
        ReceiptPrinter.handlePrintActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNavbarDateTime();
        loadVehicles();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        btnMenu = findViewById(R.id.btn_menu);
        btnRefresh = findViewById(R.id.btn_refresh);
        tvNavbarSubtitle = findViewById(R.id.tv_navbar_subtitle);
        tvNavbarDatetime = findViewById(R.id.tv_navbar_datetime);

        tvVehiclesCount = findViewById(R.id.tv_vehicles_count);
        progressVehicles = findViewById(R.id.progress_vehicles);
        layoutLoadingVehicles = findViewById(R.id.layout_loading_vehicles);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptySubtitle = findViewById(R.id.tv_empty_subtitle);

        rvParkedVehicles = findViewById(R.id.rv_parked_vehicles);
        fabCheckIn = findViewById(R.id.fab_check_in);

        etSearchVehicles = findViewById(R.id.et_search_vehicles);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        setupSearchBar();

        // Drawer Header Views (Original UI)
        if (navigationView != null && navigationView.getHeaderCount() > 0) {
            View headerView = navigationView.getHeaderView(0);
            tvDrawerAvatarInitials = headerView.findViewById(R.id.tv_drawer_avatar_initials);
            tvDrawerAgentName = headerView.findViewById(R.id.tv_drawer_agent_name);
            tvDrawerAgentIdAgency = headerView.findViewById(R.id.tv_drawer_agent_id_agency);
            tvDrawerLocation = headerView.findViewById(R.id.tv_drawer_location);
            tvDrawerTerminal = headerView.findViewById(R.id.tv_drawer_terminal);
            tvDrawerRates = headerView.findViewById(R.id.tv_drawer_rates);
        }

        // Toggle Drawer on hamburger menu click
        if (btnMenu != null) {
            btnMenu.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        // Refresh vehicle list button
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> loadVehicles());
        }
    }

    private void setupNavigationDrawer() {
        if (navigationView == null) return;

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_dashboard) {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                loadVehicles();
            } else if (id == R.id.nav_check_in) {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                launchCheckIn();
            } else if (id == R.id.nav_rates) {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showRatesDialog();
            } else if (id == R.id.nav_terminal) {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                showTerminalDialog();
            } else if (id == R.id.nav_logout) {
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                confirmLogout();
            }

            return true;
        });
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void loadAgentProfile() {
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

        String deviceDisplay = PosDeviceHelper.getDeviceDisplayInfo(this);

        if (currentUser != null) {
            String name = (currentUser.getAgentName() != null) ? currentUser.getAgentName().trim() : "";
            int agentId = currentUser.getAgentId();
            String agency = (currentUser.getAgencyName() != null) ? currentUser.getAgencyName().trim() : "";
            String location = (currentUser.getLocation() != null) ? currentUser.getLocation().trim() : "";
            String areaCode = (currentUser.getAreaCode() != null) ? currentUser.getAreaCode().trim() : "";

            twoWheelerRate = currentUser.getTwoWheelerRate();
            fourWheelerRate = currentUser.getFourWheelerRate();

            // Navbar subtitle
            if (tvNavbarSubtitle != null) {
                if (!location.isEmpty() && !name.isEmpty()) {
                    tvNavbarSubtitle.setText(location + " • " + name);
                } else if (!name.isEmpty()) {
                    tvNavbarSubtitle.setText(name);
                } else {
                    tvNavbarSubtitle.setText(location);
                }
            }

            // Populate Navigation Drawer Header (Original UI)
            if (tvDrawerAgentName != null) {
                tvDrawerAgentName.setText(name);
            }
            if (tvDrawerAgentIdAgency != null) {
                String idPart = agentId > 0 ? "Agent ID: " + agentId : "";
                if (!idPart.isEmpty() && !agency.isEmpty()) {
                    tvDrawerAgentIdAgency.setText(idPart + " • " + agency);
                } else {
                    tvDrawerAgentIdAgency.setText(!idPart.isEmpty() ? idPart : agency);
                }
            }
            if (tvDrawerLocation != null) {
                if (!location.isEmpty() && !areaCode.isEmpty()) {
                    tvDrawerLocation.setText(location + " (Area: " + areaCode + ")");
                } else {
                    tvDrawerLocation.setText(!location.isEmpty() ? location : areaCode);
                }
            }
            if (tvDrawerTerminal != null) {
                String devIdStr = currentUser.getDeviceId() > 0 ? " (ID: " + currentUser.getDeviceId() + ")" : "";
                String uidInfo = (currentUser.getDeviceUid() != null && !currentUser.getDeviceUid().isEmpty())
                        ? " [" + currentUser.getDeviceUid() + "]"
                        : "";
                tvDrawerTerminal.setText("Terminal: " + deviceDisplay + devIdStr + uidInfo);
            }
            if (tvDrawerRates != null) {
                tvDrawerRates.setText(String.format(Locale.getDefault(), "Rates: 2W ₹%.0f  |  4W ₹%.0f", twoWheelerRate, fourWheelerRate));
            }
            if (tvDrawerAvatarInitials != null) {
                tvDrawerAvatarInitials.setText(extractInitials(name));
            }
        } else {
            if (tvNavbarSubtitle != null) tvNavbarSubtitle.setText("");
            if (tvDrawerAgentName != null) tvDrawerAgentName.setText("");
            if (tvDrawerAgentIdAgency != null) tvDrawerAgentIdAgency.setText("");
            if (tvDrawerLocation != null) tvDrawerLocation.setText("");
            if (tvDrawerTerminal != null) tvDrawerTerminal.setText("Terminal: " + deviceDisplay);
            if (tvDrawerRates != null) tvDrawerRates.setText("");
            if (tvDrawerAvatarInitials != null) tvDrawerAvatarInitials.setText("");
        }
    }

    private String extractInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "AG";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2 && parts[0].length() > 0 && parts[1].length() > 0) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.getDefault());
        } else if (name.length() >= 2) {
            return name.substring(0, 2).toUpperCase(Locale.getDefault());
        }
        return name.toUpperCase(Locale.getDefault());
    }

    private void setupRecyclerView() {
        vehicleAdapter = new VehicleAdapter(vehicleList, this);
        rvParkedVehicles.setLayoutManager(new LinearLayoutManager(this));
        rvParkedVehicles.setAdapter(vehicleAdapter);

        // Attach Swipe to Checkout (Swipe Right)
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            private final Paint paint = new Paint();

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position >= 0 && position < vehicleList.size()) {
                    ParkedVehicle vehicle = vehicleList.get(position);
                    requestCheckoutBill(vehicle, position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0) {
                    View itemView = viewHolder.itemView;

                    // Emerald green background for checkout
                    paint.setColor(Color.parseColor("#10B981"));
                    RectF background = new RectF(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + dX, itemView.getBottom());
                    c.drawRoundRect(background, 24f, 24f, paint);

                    // Draw text "CHECK OUT →"
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(38f);
                    paint.setFakeBoldText(true);
                    paint.setAntiAlias(true);
                    float textY = itemView.getTop() + (itemView.getHeight() / 2f) + 12f;
                    c.drawText("CHECK OUT →", itemView.getLeft() + 48f, textY, paint);
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvParkedVehicles);
    }

    private void setupFloatingCheckInButton() {
        if (fabCheckIn != null) {
            fabCheckIn.setOnClickListener(v -> launchCheckIn());
        }
    }

    private void launchCheckIn() {
        Intent intent = new Intent(MainActivity.this, CheckInActivity.class);
        if (currentUser != null) {
            intent.putExtra("USER_DATA", currentUser);
        }
        startActivity(intent);
    }

    private void setupSearchBar() {
        if (etSearchVehicles == null) return;

        etSearchVehicles.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s != null ? s.toString().trim() : "";
                if (btnClearSearch != null) {
                    btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }
                filterVehicles(query);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                etSearchVehicles.setText("");
                filterVehicles("");
            });
        }

        etSearchVehicles.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = etSearchVehicles.getText() != null ? etSearchVehicles.getText().toString().trim() : "";
                filterVehicles(query);
                return true;
            }
            return false;
        });
    }

    private void filterVehicles(String query) {
        vehicleList.clear();
        if (query == null || query.isEmpty()) {
            vehicleList.addAll(allVehicleList);
        } else {
            String lowerQuery = query.toLowerCase(Locale.getDefault());
            for (ParkedVehicle v : allVehicleList) {
                boolean match = (v.getVehicleNumber() != null && v.getVehicleNumber().toLowerCase(Locale.getDefault()).contains(lowerQuery))
                        || (v.getSlotName() != null && v.getSlotName().toLowerCase(Locale.getDefault()).contains(lowerQuery))
                        || (v.getOwnerName() != null && v.getOwnerName().toLowerCase(Locale.getDefault()).contains(lowerQuery))
                        || (v.getOwnerContactNo() != null && v.getOwnerContactNo().contains(lowerQuery))
                        || (v.getVehicleType() != null && v.getVehicleType().toLowerCase(Locale.getDefault()).contains(lowerQuery))
                        || (v.getBookingNo() != null && v.getBookingNo().toLowerCase(Locale.getDefault()).contains(lowerQuery));
                if (match) {
                    vehicleList.add(v);
                }
            }
        }
        if (vehicleAdapter != null) {
            vehicleAdapter.notifyDataSetChanged();
        }
        updateDashboardState();
    }

    /**
     * Fetches all checked-in vehicles from backend API.
     */
    private void loadVehicles() {
        if (layoutLoadingVehicles != null) {
            layoutLoadingVehicles.setVisibility(View.VISIBLE);
        } else if (progressVehicles != null) {
            progressVehicles.setVisibility(View.VISIBLE);
        }
        if (rvParkedVehicles != null) rvParkedVehicles.setVisibility(View.GONE);
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);

        if (currentUser == null) {
            currentUser = sessionManager.getUserData();
        }
        int userId = (currentUser != null) ? currentUser.getAgentId() : 0;
        String bearerToken = tokenManager.getToken();

        BookingNetworkService.getAllCheckinVehicle(userId, "%", bearerToken, new BookingNetworkService.FetchVehiclesCallback() {
            @Override
            public void onResponse(List<ParkedVehicle> vehicles, String status, String message, boolean isSuccess) {
                if (layoutLoadingVehicles != null) {
                    layoutLoadingVehicles.setVisibility(View.GONE);
                } else if (progressVehicles != null) {
                    progressVehicles.setVisibility(View.GONE);
                }

                allVehicleList.clear();
                if (isSuccess && vehicles != null && !vehicles.isEmpty()) {
                    allVehicleList.addAll(vehicles);
                }

                String currentQuery = (etSearchVehicles != null && etSearchVehicles.getText() != null)
                        ? etSearchVehicles.getText().toString().trim()
                        : "";
                filterVehicles(currentQuery);
            }

            @Override
            public void onError(String errorMessage) {
                if (layoutLoadingVehicles != null) {
                    layoutLoadingVehicles.setVisibility(View.GONE);
                } else if (progressVehicles != null) {
                    progressVehicles.setVisibility(View.GONE);
                }
                Log.e(TAG, "Failed to load checked in vehicles: " + errorMessage);
                allVehicleList.clear();
                filterVehicles("");
            }
        });
    }

    private void updateDashboardState() {
        int count = vehicleList.size();
        int total = allVehicleList.size();
        String query = (etSearchVehicles != null && etSearchVehicles.getText() != null)
                ? etSearchVehicles.getText().toString().trim()
                : "";

        if (tvVehiclesCount != null) {
            if (!query.isEmpty() && count != total) {
                tvVehiclesCount.setText(count + " of " + total + " in lot");
            } else {
                tvVehiclesCount.setText(total + (total == 1 ? " in lot" : " in lot"));
            }
        }

        if (count == 0) {
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
            if (rvParkedVehicles != null) rvParkedVehicles.setVisibility(View.GONE);

            if (!query.isEmpty()) {
                if (tvEmptyTitle != null) tvEmptyTitle.setText("No Matching Vehicles");
                if (tvEmptySubtitle != null) {
                    tvEmptySubtitle.setText("No parked vehicle matches \"" + query + "\"");
                }
            } else {
                if (tvEmptyTitle != null) tvEmptyTitle.setText("No Checked-In Vehicles");
                if (tvEmptySubtitle != null) {
                    tvEmptySubtitle.setText("Tap the Check In button below to issue tickets");
                }
            }
        } else {
            if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
            if (rvParkedVehicles != null) rvParkedVehicles.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onCheckoutRequested(ParkedVehicle vehicle, int position) {
        if (vehicle == null) return;
        requestCheckoutBill(vehicle, position);
    }

    /**
     * Calls getCheckoutAmount API to calculate parking fee, duration, and fine before confirming checkout.
     */
    private void requestCheckoutBill(ParkedVehicle vehicle, int position) {
        if (currentUser == null) {
            currentUser = sessionManager.getUserData();
        }
        String bearerToken = tokenManager.getToken();
        int bookingId = vehicle.getBookingId();

        if (bookingId <= 0) {
            showDirectCheckoutConfirmDialog(vehicle, position, 1, 0.0, 0.0, vehicle.getFormattedTime(), "");
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Calculating bill for " + vehicle.getVehicleNumber() + "...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        BookingNetworkService.getCheckoutAmount(bookingId, bearerToken, new BookingNetworkService.CheckoutAmountCallback() {
            @Override
            public void onResponse(JSONObject amountData, String status, String message, boolean isSuccess) {
                if (!isFinishing() && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                if (isSuccess && amountData != null) {
                    showCheckoutBillDialog(vehicle, amountData, position);
                } else {
                    String displayMsg = (message != null && !message.trim().isEmpty())
                            ? message : "Unable to calculate checkout bill.";
                    Toast.makeText(MainActivity.this, displayMsg, Toast.LENGTH_LONG).show();
                    showDirectCheckoutConfirmDialog(vehicle, position, 1, 0.0, 0.0, vehicle.getFormattedTime(), "");
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (!isFinishing() && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                String displayErr = (errorMessage != null && !errorMessage.trim().isEmpty())
                        ? errorMessage : "Error fetching checkout bill from server.";
                Toast.makeText(MainActivity.this, displayErr, Toast.LENGTH_LONG).show();
                showDirectCheckoutConfirmDialog(vehicle, position, 1, 0.0, 0.0, vehicle.getFormattedTime(), "");
            }
        });
    }

    /**
     * Displays executive calculated parking bill dialog with payment options (Cash, Card, UPI).
     */
    private void showCheckoutBillDialog(ParkedVehicle vehicle, JSONObject bill, int position) {
        int hours = bill.optInt("total_hours", 1);
        double rate = bill.optDouble("hourly_rate", 0.0);
        double parkingAmt = bill.optDouble("parking_amount", 0.0);
        double fineAmt = bill.optDouble("fine_amount", 0.0);
        double promoAmt = bill.optDouble("promo_amount", 0.0);
        double payableAmt = bill.optDouble("payable_amount", parkingAmt + fineAmt - promoAmt);
        String inTime = bill.optString("checkin_time", vehicle.getCheckinTime());
        String outTime = bill.optString("checkout_time", "");
        if (outTime == null || outTime.isEmpty()) {
            outTime = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(new Date());
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_checkout_billing, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Bind Views
        TextView tvVehicleNo = dialogView.findViewById(R.id.tv_checkout_vehicle_no);
        TextView tvSlotType = dialogView.findViewById(R.id.tv_checkout_slot_type);
        TextView tvInTime = dialogView.findViewById(R.id.tv_checkout_in_time);
        TextView tvOutTime = dialogView.findViewById(R.id.tv_checkout_out_time);
        TextView tvDuration = dialogView.findViewById(R.id.tv_checkout_duration);
        TextView tvRate = dialogView.findViewById(R.id.tv_checkout_rate);
        LinearLayout layoutFine = dialogView.findViewById(R.id.layout_checkout_fine);
        TextView tvFine = dialogView.findViewById(R.id.tv_checkout_fine);
        TextView tvTotalAmount = dialogView.findViewById(R.id.tv_checkout_total_amount);

        com.google.android.material.button.MaterialButton btnCash = dialogView.findViewById(R.id.btn_pay_cash);
        com.google.android.material.button.MaterialButton btnCard = dialogView.findViewById(R.id.btn_pay_card);
        com.google.android.material.button.MaterialButton btnUpi = dialogView.findViewById(R.id.btn_pay_upi);
        com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_checkout);

        // Populate Data
        tvVehicleNo.setText(vehicle.getVehicleNumber());
        String slot = (vehicle.getSlotName() != null && !vehicle.getSlotName().isEmpty()) ? vehicle.getSlotName() : "General";
        tvSlotType.setText(vehicle.getVehicleType() + " • Slot: " + slot);
        tvInTime.setText(inTime);
        tvOutTime.setText(outTime);
        tvDuration.setText(hours + " Hour(s)");
        tvRate.setText(rate > 0 ? String.format(Locale.getDefault(), "₹%.2f / hr", rate) : "Standard");

        if (fineAmt > 0) {
            layoutFine.setVisibility(View.VISIBLE);
            tvFine.setText(String.format(Locale.getDefault(), "₹%.2f", fineAmt));
        } else {
            layoutFine.setVisibility(View.GONE);
        }

        tvTotalAmount.setText(String.format(Locale.getDefault(), "₹%.2f", payableAmt));

        final String finalInTime = inTime;
        final String finalOutTime = outTime;
        final double finalPayableAmt = payableAmt;

        // 1. CASH PAYMENT
        btnCash.setOnClickListener(v -> {
            dialog.dismiss();
            performVehicleCheckout(vehicle, position, hours, rate, finalPayableAmt, finalInTime, finalOutTime,
                    PosPaymentManager.PAYMENT_MODE_CASH, "0", "CASH");
        });

        // 2. CARD PAYMENT (PineLabs POS)
        btnCard.setOnClickListener(v -> {
            dialog.dismiss();
            initiateCheckoutPosPayment(vehicle, position, hours, rate, finalPayableAmt, finalInTime, finalOutTime,
                    PosPaymentManager.PAYMENT_MODE_CARD, "CARD");
        });

        // 3. UPI PAYMENT (PineLabs POS)
        btnUpi.setOnClickListener(v -> {
            dialog.dismiss();
            initiateCheckoutPosPayment(vehicle, position, hours, rate, finalPayableAmt, finalInTime, finalOutTime,
                    PosPaymentManager.PAYMENT_MODE_UPI, "UPI");
        });

        // CANCEL
        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            vehicleAdapter.notifyItemChanged(position);
        });

        dialog.setOnCancelListener(d -> vehicleAdapter.notifyItemChanged(position));
        dialog.show();
    }

    /**
     * Launches POS payment (Card or UPI) via PosPaymentManager & EzeAPI.
     */
    private void initiateCheckoutPosPayment(ParkedVehicle vehicle,
                                           int position,
                                           int hours,
                                           double rate,
                                           double payableAmt,
                                           String inTime,
                                           String outTime,
                                           int paymentModeId,
                                           String paymentModeName) {
        if (currentUser == null) {
            currentUser = sessionManager.getUserData();
        }
        String location = (currentUser != null && currentUser.getLocation() != null)
                ? currentUser.getLocation() : "sParking Station";

        Toast.makeText(this, "Connecting to POS terminal for " + paymentModeName + " payment...", Toast.LENGTH_SHORT).show();

        PosPaymentManager.initiatePayment(
                MainActivity.this,
                payableAmt,
                vehicle.getBookingNo(),
                vehicle.getVehicleNumber(),
                inTime,
                vehicle.getOwnerContactNo(),
                location,
                vehicle.getSlotName(),
                paymentModeId,
                new PosPaymentManager.PaymentCallback() {
                    @Override
                    public void onPaymentSuccess(String txnId, String rawResponse) {
                        Log.i(TAG, "POS Payment Approved -> TxnId: " + txnId + " | completing checkout on backend...");
                        Toast.makeText(MainActivity.this, paymentModeName + " Payment Approved!", Toast.LENGTH_SHORT).show();
                        performVehicleCheckout(vehicle, position, hours, rate, payableAmt, inTime, outTime,
                                paymentModeId, txnId, paymentModeName);
                    }

                    @Override
                    public void onPaymentCancelled(String errorCode, String errorMessage) {
                        Log.w(TAG, "POS Payment Cancelled -> [" + errorCode + "] " + errorMessage);
                        vehicleAdapter.notifyItemChanged(position);
                        String cleanReason = (errorMessage != null && !errorMessage.isEmpty())
                                ? errorMessage : "Payment was cancelled on the POS terminal.";
                        showPaymentStatusDialog("Payment Cancelled",
                                "Transaction was aborted on the POS terminal.",
                                cleanReason,
                                false);
                    }

                    @Override
                    public void onPaymentFailed(String errorCode, String errorMessage) {
                        Log.e(TAG, "POS Payment Failed -> [" + errorCode + "] " + errorMessage);
                        vehicleAdapter.notifyItemChanged(position);
                        String cleanReason = (errorMessage != null && !errorMessage.isEmpty())
                                ? errorMessage : "POS transaction could not be completed.";
                        showPaymentStatusDialog("Payment Failed",
                                "POS transaction could not be processed.",
                                cleanReason,
                                true);
                    }
                }
        );
    }

    private void showDirectCheckoutConfirmDialog(ParkedVehicle vehicle, int position, int hours, double rate, double payableAmt, String inTime, String outTime) {
        String msg = "• Vehicle No: " + vehicle.getVehicleNumber() + "\n"
                + "• Slot: " + (vehicle.getSlotName().isEmpty() ? "General" : vehicle.getSlotName()) + "\n"
                + "• Vehicle Type: " + vehicle.getVehicleType() + "\n"
                + "• Check-In Time: " + vehicle.getFormattedTime() + "\n\n"
                + "Select payment method to Check-Out this vehicle:";

        new AlertDialog.Builder(this)
                .setTitle("Confirm Vehicle Check-Out")
                .setMessage(msg)
                .setPositiveButton("CASH CHECKOUT", (dialog, which) -> {
                    performVehicleCheckout(vehicle, position, hours, rate, payableAmt, inTime, outTime,
                            PosPaymentManager.PAYMENT_MODE_CASH, "0", "CASH");
                })
                .setNeutralButton("POS CARD / UPI", (dialog, which) -> {
                    initiateCheckoutPosPayment(vehicle, position, hours, rate, payableAmt, inTime, outTime,
                            PosPaymentManager.PAYMENT_MODE_CARD, "CARD");
                })
                .setNegativeButton("CANCEL", (dialog, which) -> {
                    vehicleAdapter.notifyItemChanged(position);
                })
                .setOnCancelListener(dialog -> vehicleAdapter.notifyItemChanged(position))
                .show();
    }

    /**
     * Executes vehicle checkout API and prompts for receipt printing upon success.
     */
    private void performVehicleCheckout(ParkedVehicle vehicle,
                                       int position,
                                       int hours,
                                       double rate,
                                       double payableAmt,
                                       String inTime,
                                       String outTime,
                                       int paymentModeId,
                                       String transactionId,
                                       String paymentModeName) {
        if (currentUser == null) {
            currentUser = sessionManager.getUserData();
        }
        int userId = (currentUser != null) ? currentUser.getAgentId() : 0;
        String bearerToken = tokenManager.getToken();

        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Processing Check-Out (" + paymentModeName + ") for " + vehicle.getVehicleNumber() + "...");
        pd.setCancelable(false);
        pd.show();

        BookingNetworkService.checkoutVehicle(
                vehicle.getBookingId(),
                userId,
                vehicle.getVehicleNumber(),
                paymentModeId,
                transactionId,
                1,
                payableAmt,
                bearerToken,
                new BookingNetworkService.CheckoutCallback() {
                    @Override
                    public void onResponse(String status, String message, String rawData, boolean isSuccess) {
                        if (!isFinishing() && pd.isShowing()) {
                            pd.dismiss();
                        }

                        if (isSuccess) {
                            Toast.makeText(MainActivity.this, "Check-Out Successful: " + vehicle.getVehicleNumber(), Toast.LENGTH_LONG).show();

                            if (position >= 0 && position < vehicleList.size()) {
                                ParkedVehicle removed = vehicleList.remove(position);
                                allVehicleList.remove(removed);
                                vehicleAdapter.notifyItemRemoved(position);
                            } else {
                                vehicleList.remove(vehicle);
                                allVehicleList.remove(vehicle);
                                vehicleAdapter.notifyDataSetChanged();
                            }
                            updateDashboardState();

                            // Show modern Check-Out Success & Print Receipt dialog
                            showCheckoutSuccessReceiptDialog(vehicle, hours, rate, payableAmt, inTime, outTime, paymentModeName, transactionId);

                        } else {
                            String serverMsg = (message != null && !message.trim().isEmpty())
                                    ? message : "Server could not complete vehicle check-out.";
                            vehicleAdapter.notifyItemChanged(position);
                            showPaymentStatusDialog("Check-Out Rejected",
                                    "Server was unable to complete check-out.",
                                    serverMsg,
                                    true);
                            Toast.makeText(MainActivity.this, serverMsg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (!isFinishing() && pd.isShowing()) {
                            pd.dismiss();
                        }
                        String cleanErr = (errorMessage != null && !errorMessage.trim().isEmpty())
                                ? errorMessage : "Network or server connection failed.";
                        vehicleAdapter.notifyItemChanged(position);
                        showPaymentStatusDialog("Check-Out Error",
                                "Unable to communicate with the server.",
                                cleanErr,
                                true);
                        Toast.makeText(MainActivity.this, cleanErr, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    /**
     * Backward-compatible performVehicleCheckout overload defaulting to Cash.
     */
    private void performVehicleCheckout(ParkedVehicle vehicle, int position, int hours, double rate, double payableAmt, String inTime, String outTime) {
        performVehicleCheckout(vehicle, position, hours, rate, payableAmt, inTime, outTime,
                PosPaymentManager.PAYMENT_MODE_CASH, "0", "CASH");
    }

    /**
     * Shows modern Check-Out Success & Print Receipt popup with executive details.
     */
    private void showCheckoutSuccessReceiptDialog(ParkedVehicle vehicle,
                                                 int hours,
                                                 double rate,
                                                 double payableAmt,
                                                 String inTime,
                                                 String outTime,
                                                 String paymentModeName,
                                                 String transactionId) {
        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_checkout_success, null);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            TextView tvVehicleNo = dialogView.findViewById(R.id.tv_success_vehicle_no);
            TextView tvAmount = dialogView.findViewById(R.id.tv_success_amount);
            TextView tvDuration = dialogView.findViewById(R.id.tv_success_duration);
            TextView tvPaymentMode = dialogView.findViewById(R.id.tv_success_payment_mode);
            TextView tvSlot = dialogView.findViewById(R.id.tv_success_slot);
            TextView tvTxnRef = dialogView.findViewById(R.id.tv_success_txn_ref);
            TextView tvPrintFeedback = dialogView.findViewById(R.id.tv_print_feedback);
            com.google.android.material.button.MaterialButton btnPrint = dialogView.findViewById(R.id.btn_print_receipt);
            com.google.android.material.button.MaterialButton btnDone = dialogView.findViewById(R.id.btn_done_checkout);

            tvVehicleNo.setText(vehicle.getVehicleNumber());
            tvAmount.setText(String.format(Locale.getDefault(), "₹%.2f", payableAmt));
            tvDuration.setText(hours + " Hr(s)");
            tvPaymentMode.setText(paymentModeName != null ? paymentModeName.toUpperCase(Locale.getDefault()) : "CASH");
            tvSlot.setText((vehicle.getSlotName() != null && !vehicle.getSlotName().isEmpty())
                    ? vehicle.getSlotName() : "General");

            if (transactionId != null && !transactionId.isEmpty() && !"0".equals(transactionId)) {
                tvTxnRef.setVisibility(View.VISIBLE);
                tvTxnRef.setText("Txn Ref: " + transactionId);
            } else {
                tvTxnRef.setVisibility(View.GONE);
            }

            btnPrint.setOnClickListener(v -> {
                btnPrint.setEnabled(false);
                btnPrint.setText("Printing Receipt...");
                tvPrintFeedback.setVisibility(View.VISIBLE);
                tvPrintFeedback.setTextColor(Color.parseColor("#475569"));
                tvPrintFeedback.setText("Sending ticket to thermal printer...");

                String location = (currentUser != null && currentUser.getLocation() != null)
                        ? currentUser.getLocation() : "sParking Station";

                ReceiptPrinter.printCheckOutTicket(
                        MainActivity.this,
                        vehicle.getVehicleNumber(),
                        inTime,
                        outTime,
                        vehicle.getBookingNo(),
                        vehicle.getVehicleType(),
                        location,
                        vehicle.getSlotName(),
                        hours,
                        rate,
                        payableAmt,
                        paymentModeName,
                        transactionId,
                        (printSuccess, printMsg) -> {
                            runOnUiThread(() -> {
                                if (printSuccess) {
                                    tvPrintFeedback.setTextColor(Color.parseColor("#16A34A"));
                                    tvPrintFeedback.setText("✅ Receipt printed successfully!");
                                    Toast.makeText(MainActivity.this, "Receipt printed successfully", Toast.LENGTH_SHORT).show();

                                    // Auto-dismiss the popup dialog once print completes
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        try {
                                            if (!isFinishing() && dialog.isShowing()) {
                                                dialog.dismiss();
                                            }
                                        } catch (Exception ignored) {}
                                    }, 600);
                                } else {
                                    btnPrint.setEnabled(true);
                                    btnPrint.setText("🖨️   Retry Print Ticket");
                                    tvPrintFeedback.setTextColor(Color.parseColor("#DC2626"));
                                    tvPrintFeedback.setText(printMsg != null ? printMsg : "Printer error");
                                    Toast.makeText(MainActivity.this, printMsg != null ? printMsg : "Printer error", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                );
            });

            btnDone.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing checkout success dialog: " + e.getMessage());
            Toast.makeText(MainActivity.this, "Vehicle checked out successfully", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shows modern premium alert when payment is cancelled or fails.
     */
    private void showPaymentStatusDialog(String title, String subtitle, String reason, boolean isError) {
        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_status, null);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(true)
                    .create();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            FrameLayout layoutBadge = dialogView.findViewById(R.id.layout_status_badge);
            ImageView ivIcon = dialogView.findViewById(R.id.iv_status_icon);
            TextView tvTitle = dialogView.findViewById(R.id.tv_status_title);
            TextView tvSubtitle = dialogView.findViewById(R.id.tv_status_subtitle);
            TextView tvReason = dialogView.findViewById(R.id.tv_status_reason);
            ImageView ivGuardIcon = dialogView.findViewById(R.id.iv_status_guard_icon);
            TextView tvGuardText = dialogView.findViewById(R.id.tv_status_guard_text);
            com.google.android.material.button.MaterialButton btnOk = dialogView.findViewById(R.id.btn_status_ok);

            tvTitle.setText(title);
            tvSubtitle.setText(subtitle);
            tvReason.setText(reason);

            if (isError) {
                layoutBadge.setBackgroundResource(R.drawable.bg_logout_icon_circle);
                ivIcon.setImageResource(R.drawable.ic_close);
                ivIcon.setColorFilter(Color.parseColor("#DC2626"));
                ivGuardIcon.setColorFilter(Color.parseColor("#DC2626"));
                tvGuardText.setTextColor(Color.parseColor("#DC2626"));
                tvGuardText.setText("Check-Out Incomplete");
                btnOk.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DC2626")));
                btnOk.setText("Close");
            } else {
                layoutBadge.setBackgroundResource(R.drawable.bg_warning_icon_circle);
                ivIcon.setImageResource(R.drawable.ic_alert_circle);
                ivIcon.setColorFilter(Color.parseColor("#D97706"));
                ivGuardIcon.setColorFilter(Color.parseColor("#16A34A"));
                tvGuardText.setTextColor(Color.parseColor("#16A34A"));
                tvGuardText.setText("Vehicle Remains Parked");
                btnOk.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0F172A")));
                btnOk.setText("Understood");
            }

            btnOk.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing payment status dialog: " + e.getMessage());
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(subtitle + "\n\n" + reason)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void showPaymentErrorDialog(String title, String message) {
        showPaymentStatusDialog(title, "Transaction update", message, true);
    }

    /**
     * Dialog showing active parking tariffs & rules.
     */
    private void showRatesDialog() {
        String msg = String.format(Locale.getDefault(),
                "• 2-Wheeler Tariff: ₹%.2f\n" +
                "• 4-Wheeler Tariff: ₹%.2f\n" +
                "• Free Parking Facility: %s\n" +
                "• Special Pass Enabled: %s",
                twoWheelerRate,
                fourWheelerRate,
                (currentUser != null && currentUser.getFreeParkingFacility() == 1 ? "Yes" : "No"),
                (currentUser != null && currentUser.getIsSpecialPassAvailable() == 1 ? "Yes" : "No")
        );

        new AlertDialog.Builder(this)
                .setTitle("Parking Rates & Rules")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Dialog showing POS Terminal and Hardware configuration.
     */
    private void showTerminalDialog() {
        String deviceDisplay = PosDeviceHelper.getDeviceDisplayInfo(this);
        String uid = (currentUser != null && currentUser.getDeviceUid() != null) ? currentUser.getDeviceUid() : "N/A";
        int deviceId = (currentUser != null) ? currentUser.getDeviceId() : 0;

        String msg = "• POS Terminal: " + deviceDisplay + "\n"
                + "• Hardware Device ID: " + (deviceId > 0 ? String.valueOf(deviceId) : "N/A") + "\n"
                + "• Device UID: " + uid + "\n"
                + "• Connection Status: ONLINE\n"
                + "• Active Agent: " + (currentUser != null && currentUser.getAgentName() != null ? currentUser.getAgentName() : "N/A");

        new AlertDialog.Builder(this)
                .setTitle("POS Terminal Details")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showParkingSummaryDialog() {
        String todayDate = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
        String agentName = (currentUser != null && currentUser.getAgentName() != null) ? currentUser.getAgentName() : "";
        String agencyName = (currentUser != null && currentUser.getAgencyName() != null) ? currentUser.getAgencyName() : "";

        String msg = "• Shift Date: " + todayDate + "\n"
                + (!agentName.isEmpty() ? "• Agent: " + agentName + "\n" : "")
                + (!agencyName.isEmpty() ? "• Agency: " + agencyName + "\n" : "")
                + "• Current In-Lot Vehicles: " + vehicleList.size() + "\n"
                + "• 2-Wheeler Rate: ₹" + String.format(Locale.getDefault(), "%.0f", twoWheelerRate) + "\n"
                + "• 4-Wheeler Rate: ₹" + String.format(Locale.getDefault(), "%.0f", fourWheelerRate) + "\n"
                + "• Shift Status: ACTIVE / ONLINE";

        new AlertDialog.Builder(this)
                .setTitle("Parking Summary")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private void confirmLogout() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm_logout);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        TextView tvOperator = dialog.findViewById(R.id.tv_logout_operator_name);
        TextView tvLot = dialog.findViewById(R.id.tv_logout_lot_name);
        TextView tvMessage = dialog.findViewById(R.id.tv_dialog_message);
        View btnCancel = dialog.findViewById(R.id.btn_cancel_logout);
        View btnConfirm = dialog.findViewById(R.id.btn_confirm_logout);

        String agentName = (currentUser != null && currentUser.getAgentName() != null && !currentUser.getAgentName().isEmpty())
                ? currentUser.getAgentName()
                : "Active Operator";
        String lotName = (currentUser != null && currentUser.getLocation() != null && !currentUser.getLocation().isEmpty())
                ? currentUser.getLocation()
                : "sParking Station";

        if (tvOperator != null) tvOperator.setText(agentName);
        if (tvLot != null) tvLot.setText(lotName);
        if (tvMessage != null) {
            tvMessage.setText("Are you sure you want to end your shift for " + agentName + "? You will need your credentials to log in again.");
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                performLogout();
            });
        }

        dialog.show();
    }

    private void performLogout() {
        sessionManager.logout();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Styles the system status bar (time, date, battery, etc.) to seamlessly match the rich navy app bar.
     */
    private void setupStatusBar() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        int topBarColor = Color.parseColor("#0F172A");
        window.setStatusBarColor(topBarColor);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false); // Light (white) text & icons for dark status bar
        }

        if (drawerLayout != null) {
            drawerLayout.setStatusBarBackgroundColor(topBarColor);
        }
    }

    /**
     * Updates live formatted current date and time on the app bar.
     */
    private void updateNavbarDateTime() {
        if (tvNavbarDatetime != null) {
            String currentDateTime = new SimpleDateFormat("EEE, dd MMM yyyy • hh:mm a", Locale.getDefault()).format(new Date());
            tvNavbarDatetime.setText(currentDateTime);
        }
    }
}