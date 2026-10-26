package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.AppPreferencesManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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
    object PasswordResetSent : AuthResult()
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
                displayName = currentFirebaseUser.displayName ?: currentFirebaseUser.email?.substringBefore("@") ?: "Google User",
                photoUrl = currentFirebaseUser.photoUrl?.toString()
            )
            _currentUser.value = user
            cacheUser(user)
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

                if (FirebaseApp.getApps(context).isEmpty()) {
                    val syncedOptions = FirebaseOptions.Builder()
                        .setApplicationId("1:712978117654:android:0d1edcac25ad1a7252b137")
                        .setProjectId(context.getString(com.example.R.string.firebase_project_id))
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

    private fun resolveWebClientId(): String? {
        return try {
            context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "default_web_client_id resource not found: ${e.message}")
            null
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    /**
     * Silent Sign-In attempt upon application launch using Credential Manager GetGoogleIdOption.
     */
    suspend fun attemptSilentSignIn(): AuthUser? {
        val auth = getAuth()
        val existingUser = auth?.currentUser
        if (existingUser != null) {
            val user = AuthUser(
                uid = existingUser.uid,
                email = existingUser.email,
                displayName = existingUser.displayName ?: existingUser.email?.substringBefore("@") ?: "Google User",
                photoUrl = existingUser.photoUrl?.toString()
            )
            _currentUser.value = user
            cacheUser(user)
            return user
        }

        val clientId = resolveWebClientId() ?: return null
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = awaitTask { auth!!.signInWithCredential(authCredential) }
                val fUser = authResult.user ?: return null
                val user = AuthUser(
                    uid = fUser.uid,
                    email = fUser.email,
                    displayName = fUser.displayName ?: fUser.email?.substringBefore("@") ?: "Google User",
                    photoUrl = fUser.photoUrl?.toString()
                )
                _currentUser.value = user
                cacheUser(user)
                user
            } else {
                null
            }
        } catch (e: NoCredentialException) {
            Log.d(TAG, "Silent auto sign-in: no authorized Google account on device yet")
            null
        } catch (e: Exception) {
            Log.d(TAG, "Silent auto sign-in skipped: ${e.message}")
            null
        }
    }

    /**
     * Interactive Google Sign-In using Jetpack Credential Manager (GetSignInWithGoogleOption).
     * Uses a single credential option per request for API 36 responsiveness.
     */
    suspend fun signInWithGoogle(activityContext: Context): AuthResult {
        val auth = getAuth()
            ?: return AuthResult.Error("Firebase Authentication service unavailable on device.")
        val clientId = resolveWebClientId()
            ?: return AuthResult.Error("Google Sign-In configuration missing: default_web_client_id not found.")

        val hostActivity = activityContext.findActivity() ?: activityContext
        val credentialManager = CredentialManager.create(hostActivity)

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(hostActivity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdCred = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleIdCred.idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val firebaseResult = awaitTask { auth.signInWithCredential(authCredential) }
                val fUser = firebaseResult.user
                    ?: return AuthResult.Error("Google Sign-In failed: Firebase user profile empty.")

                val user = AuthUser(
                    uid = fUser.uid,
                    email = fUser.email ?: googleIdCred.id,
                    displayName = fUser.displayName ?: googleIdCred.displayName ?: fUser.email?.substringBefore("@") ?: "Google User",
                    photoUrl = (fUser.photoUrl ?: googleIdCred.profilePictureUri)?.toString()
                )
                _currentUser.value = user
                cacheUser(user)
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Unexpected credential type received from Credential Manager.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            AuthResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account found on device: ${e.message}", e)
            AuthResult.Error("No Google account found on this device. Please add a Google account in Android Settings -> Accounts, or ensure SHA-1 certificate A5:A6:75:2F:68:69:31:C0:F7:33:BC:27:56:31:D3:C9:AD:51:F5:95 is registered in Firebase Console.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            AuthResult.Error(e.localizedMessage ?: "Google Sign-In failed. Please try again.", e)
        }
    }

    /**
     * Email and Password Sign-In via Firebase Auth.
     */
    suspend fun signInWithEmail(email: String, pass: String): AuthResult {
        val auth = getAuth()
            ?: return AuthResult.Error("Firebase Authentication service unavailable on device.")
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || pass.isBlank()) {
            return AuthResult.Error("Please enter both email and password.")
        }
        return try {
            val result = awaitTask { auth.signInWithEmailAndPassword(cleanEmail, pass) }
            val fUser = result.user ?: return AuthResult.Error("Sign in failed: user profile is empty.")
            val user = AuthUser(
                uid = fUser.uid,
                email = fUser.email,
                displayName = fUser.displayName ?: fUser.email?.substringBefore("@") ?: "User",
                photoUrl = fUser.photoUrl?.toString()
            )
            _currentUser.value = user
            cacheUser(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Email Sign In failed: ${e.message}", e)
            AuthResult.Error(formatAuthErrorMessage(e), e)
        }
    }

    /**
     * Email and Password Sign-Up (Account Registration) via Firebase Auth.
     */
    suspend fun signUpWithEmail(email: String, pass: String, name: String = ""): AuthResult {
        val auth = getAuth()
            ?: return AuthResult.Error("Firebase Authentication service unavailable on device.")
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || pass.isBlank()) {
            return AuthResult.Error("Please enter both email and password.")
        }
        if (pass.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters.")
        }
        return try {
            val result = awaitTask { auth.createUserWithEmailAndPassword(cleanEmail, pass) }
            val fUser = result.user ?: return AuthResult.Error("Account creation failed: user profile is empty.")
            val cleanName = name.trim()
            if (cleanName.isNotBlank()) {
                try {
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                    awaitTask { fUser.updateProfile(profileUpdates) }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update profile name: ${e.message}")
                }
            }
            val user = AuthUser(
                uid = fUser.uid,
                email = fUser.email,
                displayName = cleanName.ifBlank { fUser.displayName ?: fUser.email?.substringBefore("@") ?: "User" },
                photoUrl = fUser.photoUrl?.toString()
            )
            _currentUser.value = user
            cacheUser(user)
            AuthResult.Success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Email Sign Up failed: ${e.message}", e)
            AuthResult.Error(formatAuthErrorMessage(e), e)
        }
    }

    /**
     * Sends password reset email link via Firebase Auth.
     */
    suspend fun sendPasswordReset(email: String): AuthResult {
        val auth = getAuth()
            ?: return AuthResult.Error("Firebase Authentication service unavailable on device.")
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return AuthResult.Error("Please enter your email to receive a password reset link.")
        }
        return try {
            awaitTask { auth.sendPasswordResetEmail(cleanEmail) }
            AuthResult.PasswordResetSent
        } catch (e: Exception) {
            Log.e(TAG, "Password reset failed: ${e.message}", e)
            AuthResult.Error(formatAuthErrorMessage(e), e)
        }
    }

    private fun formatAuthErrorMessage(e: Exception): String {
        val msg = e.message ?: return "Authentication failed. Please check your credentials."
        return when {
            msg.contains("invalid-credential", ignoreCase = true) ||
            msg.contains("wrong-password", ignoreCase = true) ->
                "Incorrect email or password. Please verify and try again."
            msg.contains("user-not-found", ignoreCase = true) ->
                "No account found with this email. Switch to Create Account to register."
            msg.contains("email-already-in-use", ignoreCase = true) ->
                "An account already exists with this email. Please sign in instead."
            msg.contains("weak-password", ignoreCase = true) ->
                "Password is too weak. Please use at least 6 characters."
            msg.contains("invalid-email", ignoreCase = true) ->
                "Please enter a valid email address."
            msg.contains("network", ignoreCase = true) ->
                "Network connection issue. Please check your internet connection."
            else -> msg
        }
    }

    fun signOut() {
        try {
            getAuth()?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Error during Firebase signOut: ${e.message}")
        }
        scope.launch {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear CredentialManager state: ${e.message}")
            }
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
