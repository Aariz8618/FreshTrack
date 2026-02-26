package com.aariz.freshtrack;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.google.android.material.button.MaterialButton;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BarcodeScannerActivity extends AppCompatActivity {

    private static final String TAG = "BarcodeScannerActivity";

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private ProductCacheRepository productCacheRepository;
    private GS1Parser gs1Parser;
    private ImageCapture imageCapture;
    private boolean isProcessing = false;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startCamera();
                } else {
                    Toast.makeText(this, "Camera permission required for barcode scanning", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_scanner);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsExtensions.applyHeaderInsets(findViewById(R.id.header_section));
        WindowInsetsExtensions.applyBottomNavInsets(findViewById(R.id.bottom_bar));

        previewView = findViewById(R.id.preview_view);
        productCacheRepository = new ProductCacheRepository(this);
        gs1Parser = new GS1Parser();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_CODABAR,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_DATA_MATRIX,
                        Barcode.FORMAT_PDF417,
                        Barcode.FORMAT_AZTEC
                ).build();
        barcodeScanner = BarcodeScanning.getClient(options);

        cameraExecutor = Executors.newSingleThreadExecutor();
        setupButtons();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setupButtons() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ((MaterialButton) findViewById(R.id.btn_manual_entry))
                .setOnClickListener(v -> showManualEntryDialog());
    }

    private void showManualEntryDialog() {
        EditText editText = new EditText(this);
        editText.setHint("Enter barcode/GS1 data manually");
        editText.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Manual Entry")
                .setMessage("Enter barcode number or GS1 data")
                .setView(editText)
                .setPositiveButton("Process", (dialog, which) -> {
                    String input = editText.getText().toString().trim();
                    if (!input.isEmpty()) {
                        processScannedData(input, "MANUAL_ENTRY");
                    } else {
                        Toast.makeText(this, "Please enter valid data", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder().build();
                imageAnalyzer.setAnalyzer(cameraExecutor, new BarcodeAnalyzer((rawData, format) -> {
                    if (!isProcessing) {
                        isProcessing = true;
                        processScannedData(rawData, format);
                    }
                }));

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture, imageAnalyzer);

            } catch (Exception e) {
                Log.e(TAG, "Use case binding failed", e);
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processScannedData(String rawData, String format) {
        Log.d(TAG, "Processing: " + rawData + " (" + format + ")");

        runOnUiThread(() -> Toast.makeText(this, "Processing barcode...", Toast.LENGTH_SHORT).show());

        new Thread(() -> {
            boolean isGS1 = gs1Parser.isGS1Format(rawData);
            GS1ParsedData gs1Data = null;
            String primaryBarcode = rawData;

            if (isGS1) {
                gs1Data = gs1Parser.parseGS1Data(rawData);
                primaryBarcode = gs1Parser.extractPrimaryBarcode(gs1Data);
                Log.d(TAG, "GS1 primary barcode: " + primaryBarcode);
            }

            final String finalPrimaryBarcode = primaryBarcode;
            final GS1ParsedData finalGs1Data = gs1Data;

            if (finalPrimaryBarcode.isEmpty() || finalPrimaryBarcode.length() < 8) {
                runOnUiThread(() ->
                        handleProcessingResult(rawData, finalPrimaryBarcode, finalGs1Data, null, format));
                return;
            }

            productCacheRepository.getProductInfo(finalPrimaryBarcode, (result, error) ->
                    runOnUiThread(() ->
                            handleProcessingResult(rawData, finalPrimaryBarcode, finalGs1Data, result, format)));
        }).start();
    }

    private void handleProcessingResult(String originalData, String primaryBarcode,
                                        GS1ParsedData gs1Data, ProductLookupResult productResult,
                                        String format) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("barcode", primaryBarcode);
        resultIntent.putExtra("original_data", originalData);
        resultIntent.putExtra("barcode_format", format);
        resultIntent.putExtra("is_gs1", gs1Data != null);

        if (gs1Data != null) {
            resultIntent.putExtra("gs1_expiry_date", gs1Data.expiryDate);
            resultIntent.putExtra("gs1_batch_lot", gs1Data.batchLot);
            resultIntent.putExtra("gs1_serial_number", gs1Data.serialNumber);
            resultIntent.putExtra("gs1_production_date", gs1Data.productionDate);
            resultIntent.putExtra("gs1_best_before_date", gs1Data.bestBeforeDate);
            resultIntent.putExtra("gs1_gtin", gs1Data.gtin);
        }

        if (productResult != null && productResult.productInfo != null) {
            resultIntent.putExtra("product_name", productResult.productInfo.productName);
            resultIntent.putExtra("brands", productResult.productInfo.brands);
            resultIntent.putExtra("suggested_category", productResult.productInfo.suggestedCategory);
            resultIntent.putExtra("image_url", productResult.productInfo.imageUrl);
            resultIntent.putExtra("product_found", true);
            resultIntent.putExtra("data_source", productResult.source.name());
            resultIntent.putExtra("is_offline_data", productResult.isOfflineData);
        } else if (productResult != null) {
            resultIntent.putExtra("product_found", false);
            resultIntent.putExtra("product_not_found", true);
        } else {
            resultIntent.putExtra("product_found", false);
            resultIntent.putExtra("lookup_failed", true);
        }

        String message;
        if (gs1Data != null && !gs1Data.expiryDate.isEmpty() && productResult != null && productResult.productInfo != null) {
            message = "Barcode scanned! Product info and expiry date loaded.";
        } else if (gs1Data != null && !gs1Data.expiryDate.isEmpty()) {
            message = "Barcode scanned! Expiry date found: " + gs1Data.expiryDate;
        } else if (productResult != null && productResult.productInfo != null) {
            message = "Barcode scanned! Product information loaded.";
        } else {
            message = "Barcode scanned! Please enter product details manually.";
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    // ─────────────────────────────────────────────────────────
    //  Inner analyser
    // ─────────────────────────────────────────────────────────

    interface BarcodeListener {
        void onBarcodeDetected(String rawData, String format);
    }

    private class BarcodeAnalyzer implements ImageAnalysis.Analyzer {
        private final BarcodeListener listener;

        BarcodeAnalyzer(BarcodeListener listener) {
            this.listener = listener;
        }

        @Override
        @OptIn(markerClass = ExperimentalGetImage.class)
        public void analyze(ImageProxy imageProxy) {
            android.media.Image mediaImage = imageProxy.getImage();
            if (mediaImage == null) {
                imageProxy.close();
                return;
            }

            InputImage image = InputImage.fromMediaImage(
                    mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String value = barcode.getRawValue();
                            if (value != null) {
                                String fmt = formatName(barcode.getFormat());
                                Log.d(TAG, "Detected: " + value + " (" + fmt + ")");
                                listener.onBarcodeDetected(value, fmt);
                                return;
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Barcode scanning failed", e))
                    .addOnCompleteListener(task -> imageProxy.close());
        }

        private String formatName(int format) {
            switch (format) {
                case Barcode.FORMAT_EAN_13:    return "EAN_13";
                case Barcode.FORMAT_EAN_8:     return "EAN_8";
                case Barcode.FORMAT_UPC_A:     return "UPC_A";
                case Barcode.FORMAT_UPC_E:     return "UPC_E";
                case Barcode.FORMAT_CODE_128:  return "CODE_128";
                case Barcode.FORMAT_CODE_39:   return "CODE_39";
                case Barcode.FORMAT_CODE_93:   return "CODE_93";
                case Barcode.FORMAT_CODABAR:   return "CODABAR";
                case Barcode.FORMAT_ITF:       return "ITF";
                case Barcode.FORMAT_QR_CODE:   return "QR_CODE";
                case Barcode.FORMAT_DATA_MATRIX: return "DATA_MATRIX";
                case Barcode.FORMAT_PDF417:    return "PDF417";
                case Barcode.FORMAT_AZTEC:     return "AZTEC";
                default:                       return "UNKNOWN";
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        barcodeScanner.close();
    }
}