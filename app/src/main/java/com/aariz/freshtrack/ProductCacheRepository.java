package com.aariz.freshtrack;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProductCacheRepository {

    private static final String TAG = "ProductCacheRepository";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final OpenFoodFactsService openFoodFactsService;
    private final Context context;

    // 7 days hard expiry
    private final long cacheExpiryMs = TimeUnit.DAYS.toMillis(7);
    // 3 days soft expiry – refresh if stale but still usable
    private final long softCacheExpiryMs = TimeUnit.DAYS.toMillis(3);

    public ProductCacheRepository(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.openFoodFactsService = new OpenFoodFactsService();
    }

    // Convenience overload for code paths that don't have a Context
    public ProductCacheRepository() {
        this(null);
    }

    // ─────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────

    /** Async wrapper; result delivered via callback on the calling thread. */
    public interface Callback<T> {
        void onResult(T result, Exception error);
    }

    public void getProductInfo(String barcode, Callback<ProductLookupResult> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onResult(null, new Exception("User not authenticated"));
            return;
        }

        boolean isNetworkAvailable = context != null && NetworkUtils.isNetworkAvailable(context);

        // 1. Try local cache first
        getCachedProductInfo(barcode, (cachedProduct, cacheError) -> {

            // 2. Fresh cache hit (< 3 days)
            if (cachedProduct != null && !isSoftCacheExpired(cachedProduct)) {
                Log.d(TAG, "Found fresh cached product for barcode: " + barcode);
                callback.onResult(
                        new ProductLookupResult(cachedProduct, DataSource.CACHE_FRESH, false),
                        null);
                return;
            }

            // 3. Offline – return whatever cache we have
            if (!isNetworkAvailable) {
                if (cachedProduct != null) {
                    Log.d(TAG, "Network unavailable, returning cached product for barcode: " + barcode);
                    callback.onResult(
                            new ProductLookupResult(cachedProduct, DataSource.CACHE_OFFLINE, true),
                            null);
                } else {
                    Log.d(TAG, "Network unavailable and no cache for barcode: " + barcode);
                    callback.onResult(
                            new ProductLookupResult(null, DataSource.NETWORK_FAILED, true),
                            null);
                }
                return;
            }

            // 4. Network available – hit the API
            Log.d(TAG, "Network available, fetching from API for barcode: " + barcode);
            openFoodFactsService.getProductInfo(barcode, (productInfo, apiError) -> {
                if (apiError == null) {
                    if (productInfo != null) {
                        cacheProductInfo(productInfo);
                        Log.d(TAG, "Fetched and cached product: " + productInfo.productName);
                        callback.onResult(
                                new ProductLookupResult(productInfo, DataSource.API, false),
                                null);
                    } else {
                        Log.d(TAG, "Product not found in API for barcode: " + barcode);
                        callback.onResult(
                                new ProductLookupResult(null, DataSource.API, false),
                                null);
                    }
                } else {
                    // API failed – fall back to cache
                    if (cachedProduct != null) {
                        Log.w(TAG, "API failed, returning cached data for barcode: " + barcode);
                        callback.onResult(
                                new ProductLookupResult(cachedProduct, DataSource.CACHE_FALLBACK, true),
                                null);
                    } else {
                        Log.e(TAG, "API failed and no cache for barcode: " + barcode);
                        callback.onResult(
                                new ProductLookupResult(null, DataSource.NETWORK_FAILED, true),
                                null);
                    }
                }
            });
        });
    }

    public void getProductInfoBatch(List<String> barcodes, Callback<Map<String, ProductLookupResult>> callback) {
        Map<String, ProductLookupResult> results = new HashMap<>();
        int[] pending = {barcodes.size()};

        if (barcodes.isEmpty()) {
            callback.onResult(results, null);
            return;
        }

        for (String barcode : barcodes) {
            getProductInfo(barcode, (result, error) -> {
                if (result != null) results.put(barcode, result);
                pending[0]--;
                if (pending[0] == 0) callback.onResult(results, null);
            });
        }
    }

    public void forceRefreshProduct(String barcode, Callback<ProductLookupResult> callback) {
        openFoodFactsService.getProductInfo(barcode, (productInfo, error) -> {
            if (error == null) {
                if (productInfo != null) {
                    cacheProductInfo(productInfo);
                    Log.d(TAG, "Force refreshed product: " + productInfo.productName);
                    callback.onResult(
                            new ProductLookupResult(productInfo, DataSource.API_FORCE_REFRESH, false),
                            null);
                } else {
                    callback.onResult(
                            new ProductLookupResult(null, DataSource.API_FORCE_REFRESH, false),
                            null);
                }
            } else {
                callback.onResult(null, error);
            }
        });
    }

    public void getCacheStatistics(Callback<CacheStatistics> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onResult(null, new Exception("User not authenticated"));
            return;
        }

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("cached_products")
                .get()
                .addOnSuccessListener(snapshot -> {
                    int total = 0, fresh = 0, stale = 0, expired = 0;
                    Date now = new Date();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        CachedProductInfo p = doc.toObject(CachedProductInfo.class);
                        total++;
                        long age = now.getTime() - p.cachedAt.getTime();
                        if (age <= softCacheExpiryMs)      fresh++;
                        else if (age <= cacheExpiryMs)     stale++;
                        else                                expired++;
                    }
                    callback.onResult(new CacheStatistics(total, fresh, stale, expired, 0.0), null);
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    public void clearExpiredCache(Callback<Void> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onResult(null, new Exception("User not authenticated"));
            return;
        }

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("cached_products")
                .get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = firestore.batch();
                    int[] count = {0};
                    for (QueryDocumentSnapshot doc : snapshot) {
                        CachedProductInfo p = doc.toObject(CachedProductInfo.class);
                        if (isCacheExpired(p)) {
                            batch.delete(doc.getReference());
                            count[0]++;
                        }
                    }
                    if (count[0] > 0) {
                        batch.commit()
                                .addOnSuccessListener(v -> {
                                    Log.d(TAG, "Cleared " + count[0] + " expired cache entries");
                                    callback.onResult(null, null);
                                })
                                .addOnFailureListener(e -> callback.onResult(null, e));
                    } else {
                        callback.onResult(null, null);
                    }
                })
                .addOnFailureListener(e -> callback.onResult(null, e));
    }

    // ─────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────

    private void getCachedProductInfo(String barcode, Callback<CachedProductInfo> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onResult(null, new Exception("User not authenticated"));
            return;
        }
        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("cached_products")
                .document(barcode)
                .get()
                .addOnSuccessListener(doc -> {
                    CachedProductInfo p = doc.exists() ? doc.toObject(CachedProductInfo.class) : null;
                    callback.onResult(p, null);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting cached product: " + e.getMessage(), e);
                    callback.onResult(null, e);
                });
    }

    private void cacheProductInfo(CachedProductInfo productInfo) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("cached_products")
                .document(productInfo.barcode)
                .set(productInfo)
                .addOnSuccessListener(v -> Log.d(TAG, "Cached product: " + productInfo.productName))
                .addOnFailureListener(e -> Log.e(TAG, "Error caching product: " + e.getMessage(), e));
    }

    private boolean isCacheExpired(CachedProductInfo p) {
        return (new Date().getTime() - p.cachedAt.getTime()) > cacheExpiryMs;
    }

    private boolean isSoftCacheExpired(CachedProductInfo p) {
        return (new Date().getTime() - p.cachedAt.getTime()) > softCacheExpiryMs;
    }
}

// ─────────────────────────────────────────────────────────────
//  Supporting types
// ─────────────────────────────────────────────────────────────

class ProductLookupResult {
    public final CachedProductInfo productInfo;
    public final DataSource source;
    public final boolean isOfflineData;

    public ProductLookupResult(CachedProductInfo productInfo, DataSource source, boolean isOfflineData) {
        this.productInfo = productInfo;
        this.source = source;
        this.isOfflineData = isOfflineData;
    }
}

enum DataSource {
    CACHE_FRESH,
    CACHE_OFFLINE,
    CACHE_FALLBACK,
    API,
    API_FORCE_REFRESH,
    NETWORK_FAILED
}

class CacheStatistics {
    public final int totalCachedItems;
    public final int freshItems;
    public final int staleItems;
    public final int expiredItems;
    public final double cacheHitRate;

    public CacheStatistics(int totalCachedItems, int freshItems, int staleItems,
                           int expiredItems, double cacheHitRate) {
        this.totalCachedItems = totalCachedItems;
        this.freshItems = freshItems;
        this.staleItems = staleItems;
        this.expiredItems = expiredItems;
        this.cacheHitRate = cacheHitRate;
    }
}