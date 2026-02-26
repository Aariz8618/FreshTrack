package com.aariz.freshtrack;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class FirestoreRepository {

    private static final String TAG = "FirestoreRepository";

    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    // Callback interfaces
    public interface Callback<T> {
        void onResult(T result);
    }

    public FirestoreRepository() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    // ─── Create or update user profile ───────────────────────────────────────

    public void createUserProfile(User user) {
        createUserProfile(user, null);
    }

    public void createUserProfile(User user, Callback<Boolean> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            if (callback != null) callback.onResult(false);
            return;
        }

        User userWithId = user.withId(currentUser.getUid());
        firestore.collection("users")
                .document(currentUser.getUid())
                .set(userWithId)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "User profile created/updated successfully");
                    if (callback != null) callback.onResult(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating user profile: " + e.getMessage(), e);
                    if (callback != null) callback.onResult(false);
                });
    }

    // ─── Add grocery item ────────────────────────────────────────────────────

    public void addGroceryItemAsync(GroceryItem item, Callback<Boolean> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            if (callback != null) callback.onResult(false);
            return;
        }

        Log.d(TAG, "Attempting to save item: " + item.getName() + " for userId: " + currentUser.getUid());

        DocumentReference docRef = firestore.collection("users")
                .document(currentUser.getUid())
                .collection("grocery_items")
                .document(); // Auto-generated ID

        // Set the auto-generated ID on the item
        item.setId(docRef.getId());

        docRef.set(item)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Item saved successfully with id: " + docRef.getId());
                    if (callback != null) callback.onResult(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving item: " + e.getMessage(), e);
                    if (callback != null) callback.onResult(false);
                });
    }

    // ─── Get user grocery items ───────────────────────────────────────────────

    public void getUserGroceryItemsAsync(Callback<List<GroceryItem>> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            if (callback != null) callback.onResult(null);
            return;
        }

        Log.d(TAG, "Fetching items for userId: " + currentUser.getUid());

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("grocery_items")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<GroceryItem> items = snapshot.toObjects(GroceryItem.class);
                    Log.d(TAG, "Fetched " + items.size() + " items for userId: " + currentUser.getUid());
                    if (callback != null) callback.onResult(items);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching items: " + e.getMessage(), e);
                    if (callback != null) callback.onResult(null);
                });
    }

    // ─── Update grocery item ─────────────────────────────────────────────────

    public void updateGroceryItem(GroceryItem item, Callback<Boolean> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            if (callback != null) callback.onResult(false);
            return;
        }

        if (item.getId() == null || item.getId().isEmpty()) {
            Log.e(TAG, "Cannot update item with empty ID");
            if (callback != null) callback.onResult(false);
            return;
        }

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("grocery_items")
                .document(item.getId())
                .set(item)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Item updated successfully: " + item.getName());
                    if (callback != null) callback.onResult(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating item: " + e.getMessage(), e);
                    if (callback != null) callback.onResult(false);
                });
    }

    // ─── Delete grocery item ─────────────────────────────────────────────────

    public void deleteGroceryItem(String itemId, Callback<Boolean> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            if (callback != null) callback.onResult(false);
            return;
        }

        if (itemId == null || itemId.isEmpty()) {
            Log.e(TAG, "Cannot delete item with empty ID");
            if (callback != null) callback.onResult(false);
            return;
        }

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("grocery_items")
                .document(itemId)
                .delete()
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Item deleted successfully: " + itemId);
                    if (callback != null) callback.onResult(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting item: " + e.getMessage(), e);
                    if (callback != null) callback.onResult(false);
                });
    }

    // ─── Get user profile ────────────────────────────────────────────────────

    public void getUserProfile(Callback<User> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            if (callback != null) callback.onResult(null);
            return;
        }

        firestore.collection("users")
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    User user = snapshot.toObject(User.class);
                    Log.d(TAG, "User profile fetched successfully");
                    if (callback != null) callback.onResult(user);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user profile: " + e.getMessage(), e);
                    if (callback != null) callback.onResult(null);
                });
    }

    // ─── Update item category ────────────────────────────────────────────────

    public void updateItemCategory(String itemId, String newCategory, Callback<Boolean> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            if (callback != null) callback.onResult(false);
            return;
        }

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("grocery_items")
                .document(itemId)
                .update("category", newCategory)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Item category updated successfully: " + itemId);
                    if (callback != null) callback.onResult(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating item category: " + e.getMessage(), e);
                    if (callback != null) callback.onResult(false);
                });
    }

    // ─── Mark item as used ───────────────────────────────────────────────────

    public void markItemAsUsed(String itemId, Callback<Boolean> callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User not authenticated");
            if (callback != null) callback.onResult(false);
            return;
        }

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("grocery_items")
                .document(itemId)
                .update("status", "used")
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Item marked as used: " + itemId);
                    if (callback != null) callback.onResult(true);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error marking item as used: " + e.getMessage(), e);
                    if (callback != null) callback.onResult(false);
                });
    }

    // ─── Delete item (alias) ─────────────────────────────────────────────────

    public void deleteItem(String itemId, Callback<Boolean> callback) {
        deleteGroceryItem(itemId, callback);
    }
}