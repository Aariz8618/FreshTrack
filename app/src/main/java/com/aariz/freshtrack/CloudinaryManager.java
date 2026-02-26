package com.aariz.freshtrack;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import java.util.HashMap;
import java.util.Map;
public class CloudinaryManager {

    private static final String TAG = "CloudinaryManager";
    private static boolean initialized = false;

    public static void init(Context context) {
        if (!initialized) {
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", BuildConfig.CLOUDINARY_CLOUD_NAME);
            MediaManager.init(context, config);
            initialized = true;
            Log.d(TAG, "Initialized with cloud: " + BuildConfig.CLOUDINARY_CLOUD_NAME);
        }
    }

    public interface UploadListener {
        void onSuccess(String url);
        void onError(String message);
    }

    public static void uploadImage(Context context, Uri imageUri,
                                   UploadListener listener) {
        uploadImage(context, imageUri, "profile_images", "profile_images", listener);
    }

    public static void uploadImage(Context context, Uri imageUri,
                                   String uploadPreset, String folder,
                                   UploadListener listener) {
        init(context);

        try {
            MediaManager.get().upload(imageUri)
                    .unsigned(uploadPreset)
                    .option("folder", folder)
                    .option("resource_type", "image")
                    .callback(new UploadCallback() {
                        @Override
                        public void onStart(String requestId) {
                            Log.d(TAG, "Upload started: " + requestId);
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {
                            int progress = totalBytes > 0 ? (int) (bytes * 100 / totalBytes) : 0;
                            Log.d(TAG, "Upload progress: " + progress + "% (" + bytes + "/" + totalBytes + ")");
                        }

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            Log.d(TAG, "Upload successful: " + resultData);
                            String url = (String) resultData.get("secure_url");
                            if (url != null) {
                                listener.onSuccess(url);
                            } else {
                                Log.e(TAG, "No secure_url in result");
                                listener.onError("Failed to get image URL from response");
                            }
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            String desc = error.getDescription() != null ? error.getDescription() : "Unknown upload error";
                            Log.e(TAG, "Upload error: " + desc);
                            listener.onError(desc);
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {
                            Log.w(TAG, "Upload rescheduled: " + error.getDescription());
                            listener.onError("Upload rescheduled: " + error.getDescription());
                        }
                    })
                    .dispatch();
        } catch (Exception e) {
            Log.e(TAG, "Exception during upload setup: " + e.getMessage(), e);
            listener.onError("Failed to start upload: " + e.getMessage());
        }
    }

    public static void uploadFeedbackScreenshot(Context context, Uri imageUri,
                                                UploadListener listener) {
        uploadImage(context, imageUri, "feedback_screenshots", "feedback_screenshots", listener);
    }

    public static void uploadProductImage(Context context, Uri imageUri,
                                          UploadListener listener) {
        uploadImage(context, imageUri, "Product_images", "product_images", listener);
    }
}