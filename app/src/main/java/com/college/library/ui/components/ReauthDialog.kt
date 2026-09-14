package com.college.library.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.library.ui.theme.DangerRed
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Password re-entry gate for irreversible actions.
 *
 * A confirmation dialog only proves someone tapped twice; it does not prove
 * the person tapping is the account holder. An unattended, already-signed-in
 * phone at a circulation desk is the normal case in this deployment, so
 * anything that destroys data asks for the password again first.
 *
 * Verification goes through Firebase re-authentication rather than a local
 * comparison, so there is no password stored on the device to check against
 * — and a stale session that no longer has valid credentials fails closed.
 */
@Composable
fun ReauthDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text("Your password") },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = DangerRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val user = FirebaseAuth.getInstance().currentUser
                        val email = user?.email
                        if (user == null || email.isNullOrBlank()) {
                            error = "No signed-in account to verify against."
                            busy = false
                            return@launch
                        }
                        val ok = runCatching {
                            user.reauthenticate(
                                EmailAuthProvider.getCredential(email, password)
                            ).await()
                        }.isSuccess
                        busy = false
                        if (ok) {
                            onVerified()
                        } else {
                            error = "Incorrect password."
                        }
                    }
                },
            ) {
                Text(
                    if (busy) "VERIFYING…" else confirmLabel,
                    color = DangerRed,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("CANCEL") }
        },
    )
}
