package com.college.library.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.college.library.ui.theme.Gold
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.QrCodeScanner
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun OnboardingScreen(
    viewModel: AuthViewModel = hiltViewModel()
) {
    var tab by remember { mutableIntStateOf(0) }   // 0 = Create, 1 = Join
    var collegeName by remember { mutableStateOf("") }
    var collegeId by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val db = remember { runCatching { FirebaseFirestore.getInstance() }.getOrNull() }
    val auth = remember { runCatching { FirebaseAuth.getInstance() }.getOrNull() }
    val context = LocalContext.current

    val scanner = remember {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    fun createInstitution() {
        val name = collegeName.trim()
        val cid = collegeId.trim().uppercase()
        if (name.isBlank()) { errorMsg = "Please enter a college name."; return }
        if (cid.isBlank()) { errorMsg = "Please enter a College Unique ID."; return }
        if (!cid.matches(Regex("^[A-Z0-9\\-]+$"))) {
            errorMsg = "College Unique ID can only contain letters, numbers, and hyphens."
            return
        }
        isLoading = true
        errorMsg = null
        scope.launch {
            try {
                val currentAuth = auth ?: throw Exception("Firebase Auth unavailable")
                val firestoreDb = db ?: throw Exception("Firestore unavailable")
                val uid = currentAuth.currentUser?.uid ?: throw Exception("Not signed in")

                // Check if this ID already exists
                val existingDoc = firestoreDb.collection("institutions").document(cid).get().await()
                if (existingDoc.exists()) {
                    throw Exception("This College Unique ID is already in use. Please choose another or join it.")
                }

                firestoreDb.collection("institutions").document(cid).set(
                    mapOf("name" to name, "inviteCode" to cid,
                          "createdAt" to System.currentTimeMillis())
                ).await()

                firestoreDb.collection("colleges").document(cid).set(
                    mapOf(
                        "name" to name,
                        "college_id" to cid,
                        "created_at" to System.currentTimeMillis(),
                        "booksCount" to 0,
                        "membersCount" to 0,
                        "circulationCount" to 0
                    )
                ).await()

                firestoreDb.collection("users").document(uid).set(
                    mapOf("institutionId" to cid, "role" to "owner",
                          "email" to (currentAuth.currentUser?.email ?: "")),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()

                viewModel.onOnboardingComplete(cid, UserRole.OWNER)
                // The state change in onOnboardingComplete triggers MainActivity recomposition
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to create institution"
                isLoading = false
            }
        }
    }

    fun joinInstitution() {
        val cid = inviteCode.trim().uppercase()
        if (cid.isBlank()) { errorMsg = "Please enter a College Unique ID."; return }
        isLoading = true
        errorMsg = null
        scope.launch {
            try {
                val currentAuth = auth ?: throw Exception("Firebase Auth unavailable")
                val firestoreDb = db ?: throw Exception("Firestore unavailable")
                val uid = currentAuth.currentUser?.uid ?: throw Exception("Not signed in")
                
                val instDoc = firestoreDb.collection("institutions").document(cid).get().await()

                if (!instDoc.exists()) throw Exception("Invalid College Unique ID. Institution not found.")
                val instId = instDoc.id

                firestoreDb.collection("users").document(uid).set(
                    mapOf("institutionId" to instId, "role" to "staff",
                          "email" to (currentAuth.currentUser?.email ?: "")),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()

                viewModel.onOnboardingComplete(instId, UserRole.STAFF)
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to join institution"
                isLoading = false
            }
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
        Text("Welcome to NEXLIB", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Gold)
        Spacer(Modifier.height(6.dp))
        Text("Set up your institution to continue", fontSize = 14.sp, color = Color.LightGray)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // Tab row
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0; errorMsg = null },
                        text = { Text("Create Institution") },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) })
                    Tab(selected = tab == 1, onClick = { tab = 1; errorMsg = null },
                        text = { Text("Join Institution") },
                        icon = { Icon(Icons.Default.Group, contentDescription = null) })
                }
                Spacer(Modifier.height(20.dp))

                when (tab) {
                    0 -> {
                        Text("Create a New Institution", fontWeight = FontWeight.Bold,
                             fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("You'll become the Owner and can use this ID to join on other devices.",
                             fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = collegeName,
                            onValueChange = { collegeName = it },
                            label = { Text("College / Institution Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor  = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = collegeId,
                            onValueChange = { collegeId = it.uppercase() },
                            label = { Text("College Unique ID") },
                            placeholder = { Text("e.g. GDC-MARDAN-01") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor  = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = ::createInstitution,
                            enabled = !isLoading && collegeName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold,
                                                                 contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isLoading) CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp
                            ) else Text("Create Institution", fontWeight = FontWeight.Bold)
                        }
                    }
                    1 -> {
                        Text("Join an Existing Institution", fontWeight = FontWeight.Bold,
                             fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Enter the College Unique ID to join.",
                             fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = { inviteCode = it.uppercase() },
                            label = { Text("College Unique ID") },
                            placeholder = { Text("e.g. GDC-MARDAN-01") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor  = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = ::joinInstitution,
                            enabled = !isLoading && inviteCode.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Gold,
                                                                 contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isLoading) CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp
                            ) else Text("Join Institution", fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(12.dp))
                        
                        OutlinedButton(
                            onClick = {
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        val rawValue = barcode.rawValue ?: return@addOnSuccessListener
                                        if (rawValue.startsWith("NEXLIB_LINK|")) {
                                            val parts = rawValue.split("|")
                                            if (parts.size >= 2) {
                                                inviteCode = parts[1]
                                                joinInstitution()
                                            }
                                        } else {
                                            errorMsg = "Invalid QR code format."
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        errorMsg = "Scan failed: ${e.message}"
                                    }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                            Spacer(Modifier.width(8.dp))
                            Text("Scan QR Code")
                        }
                    }
                }

                if (errorMsg != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }
    }
}
