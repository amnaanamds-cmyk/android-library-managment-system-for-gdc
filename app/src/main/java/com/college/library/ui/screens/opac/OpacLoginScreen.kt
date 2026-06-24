package com.college.library.ui.screens.opac

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Member
import com.college.library.ui.theme.Gold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OpacLoginViewModel @Inject constructor(
    private val memberDao: MemberDao
) : ViewModel() {
    var studentId by androidx.compose.runtime.mutableStateOf("")
    var pin by androidx.compose.runtime.mutableStateOf("")
    var errorMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
    var isLoading by androidx.compose.runtime.mutableStateOf(false)
    var showPin by androidx.compose.runtime.mutableStateOf(false)

    fun login(onSuccess: (Member) -> Unit) {
        if (studentId.isBlank()) { errorMessage = "Please enter your Student/Member ID"; return }
        if (pin.isBlank()) { errorMessage = "Please enter your PIN"; return }
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            val member = memberDao.loginOpacStudent(studentId.trim(), pin.trim())
            isLoading = false
            if (member != null) {
                onSuccess(member)
            } else {
                errorMessage = "Invalid ID or PIN. Contact librarian to reset your PIN."
            }
        }
    }
}

@Composable
fun OpacLoginScreen(
    onLoginSuccess: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: OpacLoginViewModel = hiltViewModel()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF1A3A6B), Color(0xFF0A1A3D))))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier.size(90.dp).clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocalLibrary, contentDescription = null, tint = Gold, modifier = Modifier.size(52.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("OPAC Student Portal", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Online Public Access Catalog", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Text("GDC Library", fontSize = 13.sp, color = Gold.copy(alpha = 0.8f))

            Spacer(modifier = Modifier.height(36.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Gold)

                    if (viewModel.errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Red.copy(alpha = 0.15f)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(viewModel.errorMessage!!, color = Color.Red, fontSize = 13.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = viewModel.studentId,
                        onValueChange = { viewModel.studentId = it; viewModel.errorMessage = null },
                        label = { Text("Student/Member ID", color = Color.White.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Gold) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold, unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = Gold
                        )
                    )

                    OutlinedTextField(
                        value = viewModel.pin,
                        onValueChange = { viewModel.pin = it; viewModel.errorMessage = null },
                        label = { Text("4-Digit PIN", color = Color.White.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Gold) },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.showPin = !viewModel.showPin }) {
                                Icon(if (viewModel.showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                            }
                        },
                        visualTransformation = if (viewModel.showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold, unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = Gold
                        )
                    )

                    Button(
                        onClick = { viewModel.login { member -> onLoginSuccess(member.id) } },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Gold),
                        enabled = !viewModel.isLoading
                    ) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Login, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.08f)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use your Member ID and the PIN set by your librarian", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back to Admin Login", color = Gold, fontSize = 14.sp)
            }
        }
    }
}
