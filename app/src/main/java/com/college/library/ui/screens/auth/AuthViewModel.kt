package com.college.library.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

enum class UserRole {
    DIRECTORATE_ADMIN, COLLEGE_ADMIN, LIBRARIAN, DIRECTOR, OWNER, STAFF, GUEST
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Error(val message: String) : AuthState()
    object NeedsOnboarding : AuthState()
    data class Authenticated(val role: UserRole, val institutionId: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val auth get() = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    private val db get() = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    // Convenience shims — keep backward compat with MainActivity.kt
    val isAuthenticated: kotlinx.coroutines.flow.StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false).also { isAuth ->
            viewModelScope.launch {
                _authState.collect { state ->
                    isAuth.value = state is AuthState.Authenticated
                }
            }
        }

    val institutionId: kotlinx.coroutines.flow.StateFlow<String> =
        kotlinx.coroutines.flow.MutableStateFlow("").also { instId ->
            viewModelScope.launch {
                _authState.collect { state ->
                    instId.value = if (state is AuthState.Authenticated) state.institutionId else ""
                }
            }
        }

    val currentRole: kotlinx.coroutines.flow.StateFlow<UserRole> =
        kotlinx.coroutines.flow.MutableStateFlow(UserRole.GUEST).also { role ->
            viewModelScope.launch {
                _authState.collect { state ->
                    role.value = if (state is AuthState.Authenticated) state.role else UserRole.GUEST
                }
            }
        }

    init {
        // Attempt to restore session from persisted prefs (offline)
        val savedInstitutionId = prefs.getString("institution_id", "") ?: ""
        val savedRole = prefs.getString("role", UserRole.GUEST.name) ?: UserRole.GUEST.name

        // Restore session only if we have a real, previously-saved institutionId and role.
        if (savedInstitutionId.isNotEmpty() && savedRole != UserRole.GUEST.name) {
            val role = try { UserRole.valueOf(savedRole) } catch (e: Exception) { UserRole.STAFF }
            _authState.value = AuthState.Authenticated(role, savedInstitutionId)
        }
    }

    /**
     * Real Firebase Auth email/password sign-in.
     * After sign-in, reads users/{uid}.institutionId and role from Firestore.
     * If institutionId is missing → NeedsOnboarding state.
     *
     * NOTE: the previous hardcoded "admin@gdc.edu" / "admin" dev bypass that
     * force-assigned institutionId = "gdc11" has been removed. It was causing
     * every device that ever used that test login to silently attach itself
     * to a shared "gdc11" institution document, which broke per-college data
     * isolation and made real-time sync look broken across colleges.
     */
    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val firebaseAuth = auth ?: throw Exception("Firebase Auth not configured")
                val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid ?: throw Exception("Authentication failed")
                resolveUserProfile(uid)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    /**
     * Reads users/{uid} from Firestore to resolve institutionId + role.
     * Navigates to onboarding if no institutionId is set.
     *
     * NOTE: previously, if Firestore was unavailable this silently fell back
     * to institutionId = "gdc11". That fallback has been removed — an
     * unreachable Firestore now surfaces as a clear error instead of quietly
     * attaching the device to the wrong institution.
     */
    suspend fun resolveUserProfile(uid: String) {
        val firestoreDb = db ?: run {
            _authState.value = AuthState.Error("Cannot connect to server. Check your internet connection and try again.")
            return
        }
        val userDoc = firestoreDb.collection("users").document(uid).get().await()
        val instId = userDoc.getString("institutionId") ?: userDoc.getString("collegeId") ?: ""
        val roleStr = userDoc.getString("role") ?: "staff"
        val role = mapRoleString(roleStr)

        if (instId.isEmpty()) {
            _authState.value = AuthState.NeedsOnboarding
        } else {
            persistSession(instId, role)
            _authState.value = AuthState.Authenticated(role, instId)
        }
    }

    /**
     * Called by OnboardingScreen after the user creates or joins an institution.
     */
    fun onOnboardingComplete(institutionId: String, role: UserRole) {
        persistSession(institutionId, role)
        _authState.value = AuthState.Authenticated(role, institutionId)
    }

    fun logout() {
        auth?.signOut()
        prefs.edit().remove("institution_id").remove("role").apply()
        _authState.value = AuthState.Idle
    }

    private fun persistSession(institutionId: String, role: UserRole) {
        prefs.edit()
            .putString("institution_id", institutionId)
            .putString("role", role.name)
            .apply()
    }

    private fun mapRoleString(roleStr: String): UserRole = when (roleStr.lowercase()) {
        "owner"              -> UserRole.OWNER
        "college_admin", "admin" -> UserRole.COLLEGE_ADMIN
        "librarian"          -> UserRole.LIBRARIAN
        "director"           -> UserRole.DIRECTOR
        "directorate_admin"  -> UserRole.DIRECTORATE_ADMIN
        else                 -> UserRole.STAFF
    }

    // ── Authorization helpers (unchanged API) ──────────────────────────────
    fun canEditBooks()     = currentRole.value in listOf(UserRole.COLLEGE_ADMIN, UserRole.DIRECTOR, UserRole.LIBRARIAN, UserRole.OWNER)
    fun canEditMembers()   = currentRole.value in listOf(UserRole.COLLEGE_ADMIN, UserRole.DIRECTOR, UserRole.LIBRARIAN, UserRole.OWNER)
    fun canAccessSettings()= currentRole.value in listOf(UserRole.COLLEGE_ADMIN, UserRole.DIRECTOR, UserRole.OWNER, UserRole.LIBRARIAN, UserRole.STAFF)
    fun canViewReports()   = currentRole.value in listOf(UserRole.COLLEGE_ADMIN, UserRole.DIRECTOR, UserRole.DIRECTORATE_ADMIN, UserRole.OWNER)
}