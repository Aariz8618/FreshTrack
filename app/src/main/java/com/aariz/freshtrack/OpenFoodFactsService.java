package com.aariz.freshtrack;

import android.util.Log;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

// ─────────────────────────────────────────────────────────────
//  Retrofit interface
// ─────────────────────────────────────────────────────────────
interface OpenFoodFactsApi {
    @GET("api/v0/product/{barcode}.json")
    Call<OpenFoodFactsResponse> getProductInfo(@Path("barcode") String barcode);
}

// ─────────────────────────────────────────────────────────────
//  Service
// ─────────────────────────────────────────────────────────────
public class OpenFoodFactsService {

    private static final String TAG = "OpenFoodFactsService";
    private final OpenFoodFactsApi api;

    public OpenFoodFactsService() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://world.openfoodfacts.org/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(OpenFoodFactsApi.class);
    }

    /** Callback interface mirrors the Kotlin Result<CachedProductInfo?> pattern. */
    public interface ProductCallback {
        void onResult(CachedProductInfo productInfo, Exception error);
    }

    public void getProductInfo(String barcode, ProductCallback callback) {
        api.getProductInfo(barcode).enqueue(new Callback<OpenFoodFactsResponse>() {
            @Override
            public void onResponse(Call<OpenFoodFactsResponse> call,
                                   Response<OpenFoodFactsResponse> response) {
                if (response.isSuccessful()) {
                    OpenFoodFactsResponse body = response.body();
                    if (body != null && body.status == 1 && body.product != null) {
                        OpenFoodFactsProduct product = body.product;
                        String imageUrl = (product.imageFrontSmallUrl != null && !product.imageFrontSmallUrl.isEmpty())
                                ? product.imageFrontSmallUrl
                                : (product.imageUrl != null ? product.imageUrl : "");

                        CachedProductInfo info = new CachedProductInfo(
                                barcode,
                                product.productName != null ? product.productName : "",
                                product.brands != null ? product.brands : "",
                                product.categories != null ? product.categories : "",
                                imageUrl,
                                mapCategoriesToAppCategory(product.categories),
                                new Date(),
                                "openfoodfacts"
                        );
                        callback.onResult(info, null);
                    } else {
                        // Product not found
                        callback.onResult(null, null);
                    }
                } else {
                    callback.onResult(null,
                            new Exception("API request failed: " + response.code() + " " + response.message()));
                }
            }

            @Override
            public void onFailure(Call<OpenFoodFactsResponse> call, Throwable t) {
                Log.e(TAG, "API call failed", t);
                callback.onResult(null, new Exception(t));
            }
        });
    }

    private String mapCategoriesToAppCategory(String categories) {
        if (categories == null || categories.isEmpty()) return "Other";
        String c = categories.toLowerCase();

        if (c.contains("dairy") || c.contains("milk") || c.contains("cheese") || c.contains("yogurt"))
            return "Dairy";
        if (c.contains("meat") || c.contains("beef") || c.contains("chicken") || c.contains("pork")
                || c.contains("fish") || c.contains("seafood"))
            return "Meat";
        if (c.contains("vegetable") || c.contains("tomato") || c.contains("onion") || c.contains("carrot"))
            return "Vegetables";
        if (c.contains("fruit") || c.contains("apple") || c.contains("banana") || c.contains("orange"))
            return "Fruits";
        if (c.contains("bread") || c.contains("bakery") || c.contains("cake") || c.contains("pastry"))
            return "Bakery";
        if (c.contains("frozen"))
            return "Frozen";
        if (c.contains("beverage") || c.contains("drink") || c.contains("snack")
                || c.contains("cereal") || c.contains("pasta") || c.contains("rice"))
            return "Pantry";

        return "Other";
    }
}