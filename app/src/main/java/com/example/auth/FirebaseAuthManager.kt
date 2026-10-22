package com.example.auth

import android.content.Context
import android.util.Log
import com.example.data.AppPreferencesManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)

sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class Error(val message: String, val cause: Throwable? = null) : AuthResult()
    object Cancelled : AuthResult()
}

class FirebaseAuthManager(
    private val context: Context,
    private val prefs: AppPreferencesManager
) {
    private val TAG = "FirebaseAuthManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private var firebaseAuthInstance: FirebaseAuth? = null

    init {
        initFirebaseIfNeeded()
        val auth = getAuth()
        val currentFirebaseUser = auth?.currentUser
        if (currentFirebaseUser != null) {
            val user = AuthUser(
                uid = currentFirebaseUser.uid,
                email = currentFirebaseUser.email,
                displayName = currentFirebaseUser.displayName ?: currentFirebaseUser.email?.substringBefore("@") ?: "User",
                photoUrl = currentFirebaseUser.photoUrl?.toString()
            )
            _currentUser.value = user
        } else {
            _currentUser.value = null
        }
    }

    private fun initFirebaseIfNeeded() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(context)
                    Log.d(TAG, "FirebaseApp initialized with default configuration from google-services.json")
                } catch (e: Exception) {
                    Log.w(TAG, "Standard Firebase initialization note: ${e.message}")
                }

                // If still not initialized, consume options directly from the synced google-services.json
                if (FirebaseApp.getApps(context).isEmpty()) {
                    val syncedOptions = loadSyncedFirebaseOptions() ?: FirebaseOptions.Builder()
                        .setApplicationId("1:712978117654:android:0d1edcac25ad1a7252b137")
                        .setProjectId("ledger--mate")
                        .setApiKey("AIzaSyDi5m9Y2VMjj1Ft7SptYYOuxrfSTIGJ-gs")
                        .setStorageBucket("ledger--mate.firebasestorage.app")
                        .setGcmSenderId("712978117654")
                        .build()

                    FirebaseApp.initializeApp(context, syncedOptions)
                    Log.d(TAG, "FirebaseApp initialized with synced project configuration (${syncedOptions.projectId})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseApp initialization note: ${e.message}")
        }
    }

    private fun loadSyncedFirebaseOptions(): FirebaseOptions? {
        return try {
            val jsonString = context.assets.open("google-services.json").bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(jsonString)
            val projInfo = root.getJSONObject("project_info")
            val projectId = projInfo.getString("project_id")
            val projectNumber = projInfo.getString("project_number")
            val firebaseDbUrl = projInfo.optString("firebase_url")
            val storageBucket = projInfo.optString("storage_bucket")

            val clients = root.getJSONArray("client")
            var appId = "1:$projectNumber:android:0d1edcac25ad1a7252b137"
            var apiKey = "AIzaSyDi5m9Y2VMjj1Ft7SptYYOuxrfSTIGJ-gs"

            for (i in 0 until clients.length()) {
                val clientObj = clients.getJSONObject(i)
                val cInfo = clientObj.getJSONObject("client_info")
                val pkgName = cInfo.optJSONObject("android_client_info")?.optString("package_name") ?: ""
                if (pkgName == context.packageName || i == 0) {
                    appId = cInfo.optString("mobilesdk_app_id", appId)
                    val apiKeys = clientObj.optJSONArray("api_key")
                    if (apiKeys != null && apiKeys.length() > 0) {
                        apiKey = apiKeys.getJSONObject(0).optString("current_key", apiKey)
                    }
                    if (pkgName == context.packageName) break
                }
            }

            FirebaseOptions.Builder()
                .setApplicationId(appId)
                .setProjectId(projectId)
                .setApiKey(apiKey)
                .setGcmSenderId(projectNumber)
                .apply {
                    if (firebaseDbUrl.isNotBlank()) setDatabaseUrl(firebaseDbUrl)
                    if (storageBucket.isNotBlank()) setStorageBucket(storageBucket)
                }
                .build()
        } catch (e: Exception) {
            Log.d(TAG, "loadSyncedFirebaseOptions asset notice: ${e.message}")
            null
        }
    }

    fun getAuth(): FirebaseAuth? {
        if (firebaseAuthInstance == null) {
            try {
                initFirebaseIfNeeded()
                firebaseAuthInstance = FirebaseAuth.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseAuth not available: ${e.message}")
            }
        }
        return firebaseAuthInstance
    }

    /**
     * Silent Sign-In attempt upon application launch
     */
    suspend fun attemptSilentSignIn(): AuthUser? {
        val auth = getAuth()
        val existingUser = auth?.currentUser
        if (existingUser != null) {
            val user = AuthUser(
                uid = existingUser.uid,
                email = existingUser.email,
                displayName = existingUser.displayName ?: existingUser.email?.substringBefore("@") ?: "User",
                photoUrl = existingUser.photoUrl?.toString()
            )
            _currentUser.value = user
            cacheUser(user)
            return user
        }
        return null
    }

    /**
     * Sign In with Email and Password using FirebaseAuth.
     * Immediately authenticates without email verification requirements.
     */
    suspend fun signInWithEmail(email: String, pass: String): AuthResult {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return AuthResult.Error("Please enter a valid email address.")
        }
        if (pass.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters.")
        }
        return try {
            val auth = getAuth() ?: return AuthResult.Error("Firebase Authentication service unavailable on device.")
            val result = awaitTask { auth.signInWithEmailAndPassword(cleanEmail, pass) }
            val fUser = result.user ?: return AuthResult.Error("Sign in failed: User not found.")
            val user = AuthUser(
                uid = fUser.uid,
                email = fUser.email ?: cleanEmail,
                displayName = fUser.displayName ?: cleanEmail.substringBefore("@"),
                photoUrl = fUser.photoUrl?.toString()
            )
            _currentUser.value = user
            cacheUser(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmail failed: ${e.message}", e)
            val friendlyMsg = when {
                e is FirebaseAuthInvalidUserException ||
                e.message?.contains("user-not-found", ignoreCase = true) == true ->
                    "No account found with this email. Tap 'Create Account' to sign up."
                e is FirebaseAuthInvalidCredentialsException ||
                e.message?.contains("supplied auth credential", ignoreCase = true) == true ||
                e.message?.contains("credential is incorrect", ignoreCase = true) == true ||
                e.message?.contains("invalid-credential", ignoreCase = true) == true ||
                e.message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true ||
                e.message?.contains("malformed", ignoreCase = true) == true ||
                e.message?.contains("expired", ignoreCase = true) == true ||
                e.message?.contains("wrong-password", ignoreCase = true) == true ->
                    "Incorrect password or email not registered. If you are new, tap 'Create Account' below to sign up."
                e.message?.contains("too-many-requests", ignoreCase = true) == true ->
                    "Too many failed attempts. Access is temporarily suspended. Please wait a moment or try again later."
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your internet connection."
                else -> "Sign in failed: ${e.localizedMessage ?: "Invalid credentials"}"
            }
            AuthResult.Error(friendlyMsg, e)
        }
    }

    /**
     * Create Account with Email and Password using FirebaseAuth.
     * Strict Constraint: No email verification flow. Immediately creates the session and routes user.
     */
    suspend fun createUserWithEmail(email: String, pass: String): AuthResult {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return AuthResult.Error("Please enter a valid email address.")
        }
        if (pass.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters.")
        }
        return try {
            val auth = getAuth() ?: return AuthResult.Error("Firebase Authentication service unavailable on device.")
            val result = awaitTask { auth.createUserWithEmailAndPassword(cleanEmail, pass) }
            val fUser = result.user ?: return AuthResult.Error("Account creation failed.")
            val user = AuthUser(
                uid = fUser.uid,
                email = fUser.email ?: cleanEmail,
                displayName = fUser.displayName ?: cleanEmail.substringBefore("@"),
                photoUrl = fUser.photoUrl?.toString()
            )
            _currentUser.value = user
            cacheUser(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "createUserWithEmail failed: ${e.message}", e)
            val friendlyMsg = when {
                e is FirebaseAuthUserCollisionException ||
                e.message?.contains("email-already-in-use", ignoreCase = true) == true ->
                    "An account with this email already exists. Tap 'Sign In' instead."
                e.message?.contains("weak-password", ignoreCase = true) == true ->
                    "Password is too weak. Please use at least 6 characters."
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your internet connection."
                else -> e.message ?: "Account creation failed"
            }
            AuthResult.Error(friendlyMsg, e)
        }
    }

    fun signOut() {
        try {
            getAuth()?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error during Firebase signOut: ${e.message}")
        }
        _currentUser.value = null
        prefs.cachedUserEmail = ""
        prefs.cachedUserName = ""
        prefs.cachedUserPhotoUrl = ""
    }

    private fun cacheUser(user: AuthUser) {
        prefs.cachedUserEmail = user.email ?: ""
        prefs.cachedUserName = user.displayName ?: ""
        prefs.cachedUserPhotoUrl = user.photoUrl ?: ""
    }

    companion object {
        const val FIREBASE_PROJECT_ID = "ledger--mate"
        const val FIREBASE_PROJECT_NUMBER = "712978117654"

        suspend fun <T> awaitTask(block: () -> com.google.android.gms.tasks.Task<T>): T =
            suspendCancellableCoroutine { continuation ->
                val task = block()
                task.addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                task.addOnFailureListener { exception ->
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
                task.addOnCanceledListener {
                    continuation.cancel()
                }
            }
    }
}
