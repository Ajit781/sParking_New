package com.innovus.sparkingnew.scanner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.innovus.sparkingnew.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ultra-fast CameraX + ML Kit OCR Scanner Activity with comprehensive logging.
 * Analyzes frames in real-time, auto-locks on plates or lets user tap to use detected text.
 */
public class VehicleScannerActivity extends AppCompatActivity {

    private static final String TAG = "SPARKING_OCR";

    public static final String EXTRA_VEHICLE_NO = "EXTRA_VEHICLE_NO";
    public static final String EXTRA_MOBILE_NO = "EXTRA_MOBILE_NO";
    public static final String EXTRA_RAW_TEXT = "EXTRA_RAW_TEXT";

    private PreviewView previewView;
    private ScannerOverlayView scannerOverlay;
    private ImageButton btnBack;
    private ImageButton btnTorch;
    private TextView tvDetectedText;
    private MaterialButton btnUseDetected;

    private ExecutorService cameraExecutor;
    private TextRecognizer textRecognizer;
    private Camera camera;
    private boolean isTorchOn = false;
    private final AtomicBoolean isDetected = new AtomicBoolean(false);

    private volatile VehiclePlateParser.ParseResult latestCandidate = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_scanner);
        Log.i(TAG, "===> VehicleScannerActivity onCreate started");

        initViews();
        setupListeners();

        cameraExecutor = Executors.newSingleThreadExecutor();
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        startCamera();
    }

    private void initViews() {
        previewView = findViewById(R.id.preview_view);
        scannerOverlay = findViewById(R.id.scanner_overlay);
        btnBack = findViewById(R.id.btn_back);
        btnTorch = findViewById(R.id.btn_torch);
        tvDetectedText = findViewById(R.id.tv_detected_text);
        btnUseDetected = findViewById(R.id.btn_use_detected);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            Log.d(TAG, "Back pressed by user");
            finish();
        });

        findViewById(R.id.btn_manual_entry).setOnClickListener(v -> {
            Log.d(TAG, "Manual entry chosen by user");
            finish();
        });

        btnTorch.setOnClickListener(v -> toggleTorch());

        // Button to manually confirm currently detected plate
        btnUseDetected.setOnClickListener(v -> {
            if (latestCandidate != null && latestCandidate.hasVehicleNo()) {
                Log.i(TAG, "User explicitly tapped USE DETECTED: " + latestCandidate.getVehicleNo());
                if (isDetected.compareAndSet(false, true)) {
                    handleSuccessfulDetection(latestCandidate);
                }
            }
        });

        // Tap-to-focus
        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && camera != null) {
                try {
                    MeteringPointFactory factory = previewView.getMeteringPointFactory();
                    MeteringPoint point = factory.createPoint(event.getX(), event.getY());
                    FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(2, TimeUnit.SECONDS)
                            .build();
                    camera.getCameraControl().startFocusAndMetering(action);
                } catch (Exception ignored) {}
                v.performClick();
            }
            return true;
        });
    }

    private void toggleTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isTorchOn = !isTorchOn;
            camera.getCameraControl().enableTorch(isTorchOn);
            btnTorch.setImageResource(isTorchOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
            Log.d(TAG, "Torch toggled: " + isTorchOn);
        } else {
            Toast.makeText(this, "Flashlight unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCamera() {
        Log.d(TAG, "Starting Camera Provider...");
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Fatal: CameraProvider start failed", e);
                Toast.makeText(VehicleScannerActivity.this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Size targetResolution = new Size(1280, 720);

        Preview preview = new Preview.Builder()
                .setTargetResolution(targetResolution)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(targetResolution)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            Log.i(TAG, "Camera bound successfully to lifecycle!");
        } catch (Exception e) {
            Log.e(TAG, "Binding failed", e);
            Toast.makeText(this, "Camera binding error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (isDetected.get() || isFinishing()) {
            imageProxy.close();
            return;
        }

        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            textRecognizer.process(inputImage)
                    .addOnSuccessListener(visionText -> {
                        if (!isDetected.get() && !isFinishing()) {
                            processVisionTextFast(visionText);
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "OCR recognition error", e))
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    private void processVisionTextFast(@NonNull Text visionText) {
        if (isDetected.get() || isFinishing()) return;

        // Collect all lines across all text blocks and sort them top-to-bottom by Y coordinate
        List<Text.Line> linesWithCoords = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            linesWithCoords.addAll(block.getLines());
        }

        Collections.sort(linesWithCoords, (l1, l2) -> {
            android.graphics.Rect b1 = l1.getBoundingBox();
            android.graphics.Rect b2 = l2.getBoundingBox();
            if (b1 != null && b2 != null) {
                return Integer.compare(b1.top, b2.top);
            }
            return 0;
        });

        List<String> orderedLines = new ArrayList<>();
        for (Text.Line line : linesWithCoords) {
            String text = line.getText();
            if (text != null && !text.trim().isEmpty()) {
                orderedLines.add(text.trim());
            }
        }

        final String rawText = (visionText.getText() != null) ? visionText.getText() : "";

        if (!rawText.trim().isEmpty()) {
            Log.d(TAG, "Frame OCR text: [" + rawText.replace("\n", " ") + "]");
        }

        // Parse with comprehensive multi-line and complete plate validation
        VehiclePlateParser.ParseResult parseResult = VehiclePlateParser.parse(rawText, orderedLines);

        if (parseResult.hasVehicleNo()) {
            String plate = parseResult.getVehicleNo();
            Log.i(TAG, "Complete plate auto-match SUCCESS: " + plate);
            latestCandidate = parseResult;
            if (isDetected.compareAndSet(false, true)) {
                runOnUiThread(() -> handleSuccessfulDetection(parseResult));
                return;
            }
        } else {
            // Check if any candidate token can be suggested on screen (NOT auto-locked)
            String fallback = VehiclePlateParser.findFallbackCandidate(rawText);
            runOnUiThread(() -> {
                if (!isDetected.get() && !isFinishing()) {
                    String display = (fallback != null) ? fallback : rawText.replaceAll("\\s+", " ").trim();
                    if (display.length() > 22) {
                        display = display.substring(0, 22) + "...";
                    }
                    tvDetectedText.setText("Detected: " + display);

                    if (fallback != null) {
                        latestCandidate = new VehiclePlateParser.ParseResult(fallback, null, rawText);
                        btnUseDetected.setVisibility(View.VISIBLE);
                        btnUseDetected.setText("USE: " + fallback);
                    } else {
                        btnUseDetected.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    private void handleSuccessfulDetection(@NonNull VehiclePlateParser.ParseResult parseResult) {
        String vehNo = parseResult.getVehicleNo();
        Log.i(TAG, "===> LOCKING DETECTION: " + vehNo);

        // Visual lock
        scannerOverlay.setMatchFound(true);
        tvDetectedText.setText("Locked: " + vehNo);
        btnUseDetected.setVisibility(View.GONE);

        // Sound & Vibrate
        playBeepAndVibrate();

        // Send result immediately (50ms delay for visual feedback)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.i(TAG, "===> SENDING RESULT_OK to caller with plate: " + vehNo);
            Intent data = new Intent();
            data.putExtra(EXTRA_VEHICLE_NO, vehNo);
            if (parseResult.hasMobileNo()) {
                data.putExtra(EXTRA_MOBILE_NO, parseResult.getMobileNo());
            }
            data.putExtra(EXTRA_RAW_TEXT, parseResult.getRawText());
            setResult(RESULT_OK, data);
            finish();
        }, 50);
    }

    private void playBeepAndVibrate() {
        try {
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90);
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100);
        } catch (Exception ignored) {}

        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(80);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "VehicleScannerActivity onDestroy");
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (textRecognizer != null) {
            textRecognizer.close();
        }
    }
}
