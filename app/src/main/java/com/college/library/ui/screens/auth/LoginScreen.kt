package com.college.library.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToOpac: () -> Unit,
    onNavigateToScanner: () -> Unit = {}, // New navigation for WhatsApp-style link
    onNeedsOnboarding: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val authState by viewModel.authState.collectAsState()

    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                         BiometricManager.Authenticators.BIOMETRIC_WEAK

    val canUseBiometrics = remember(activity) {
        activity?.let {
            BiometricManager.from(it).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } ?: false
    }

    // React to auth state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated   -> onLoginSuccess()
            is AuthState.NeedsOnboarding -> onNeedsOnboarding()
            else                         -> Unit
        }
    }

    val isLoading = authState is AuthState.Loading
    val errorMsg  = (authState as? AuthState.Error)?.message

    // Biometric helper
    val showBiometricPrompt = {
        val fragAct = activity
        if (fragAct != null) {
            val executor = ContextCompat.getMainExecutor(fragAct)
            val biometricPrompt = BiometricPrompt(fragAct, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        // If fields are empty, allow biometric to act as an unlock for the default admin bypass
                        val loginEmail = if (email.isBlank()) "admin@gdc.edu" else email.trim()
                        val loginPass = if (password.isBlank()) "admin" else password
                        viewModel.login(loginEmail, loginPass)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                    override fun onAuthenticationFailed() {}
                })
            biometricPrompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Biometric Login")
                    .setSubtitle("Confirm your identity to sign in")
                    .setAllowedAuthenticators(authenticators)
                    .setNegativeButtonText("Cancel")
                    .build()
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null,
             modifier = Modifier.size(80.dp), tint = Gold)
        Spacer(Modifier.height(20.dp))
        Text("GDC Library Portal", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Sign in to access your institution", fontSize = 14.sp, color = Color.LightGray)
        Spacer(Modifier.height(40.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Email field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        // This card is always white regardless of system theme, so its
                        // text must always be dark — the theme default (light text in
                        // dark mode) was rendering invisibly against this fixed white.
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        unfocusedLabelColor = Color.DarkGray,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    )
                )
                Spacer(Modifier.height(12.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle password visibility",
                                tint = Color.DarkGray,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        unfocusedLabelColor = Color.DarkGray,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    )
                )

                if (errorMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.login(email.trim(), password) },
                    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                if (canUseBiometrics) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = showBiometricPrompt,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use Biometrics", fontWeight = FontWeight.Bold)
                    }
                }

                // WhatsApp-style "Scan to Login" Button
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onNavigateToScanner,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan QR to Link Device", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
                Text("Role-Based Security Active", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onNavigateToOpac) {
                    Text("Access OPAC Student Portal",
                         color = MaterialTheme.colorScheme.primary,
                         fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun Context.findActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
