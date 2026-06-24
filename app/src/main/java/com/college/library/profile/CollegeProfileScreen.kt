package com.college.library.profile

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.college.library.ui.theme.Gold
import com.college.library.ui.theme.NavyBlue

private val LightNavyBg = Color(0xFFE8EAF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollegeProfileScreen(
    onNavigateBack: () -> Unit,
    onSetupComplete: () -> Unit = {},
    isOnboarding: Boolean = false,
    viewModel: CollegeProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission so the URI remains accessible
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers don't support persistable permissions
            }
            viewModel.updateProfile(profile.copy(logoUri = it.toString()))
        }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            Toast.makeText(context, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
            viewModel.resetSaveSuccess()
            if (isOnboarding) {
                onSetupComplete()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isOnboarding) "Setup Your College" else "College Profile",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    if (!isOnboarding) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyBlue
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview Card
            ProfilePreviewCard(profile = profile)

            // Onboarding message
            if (isOnboarding) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Welcome! Set up your college details to personalize the library system. You can change these anytime from Settings.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF5D4037)
                    )
                }
            }

            // Logo Picker
            SectionHeader("Logo")
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(LightNavyBg)
                    .border(2.dp, Gold, CircleShape)
                    .clickable { imagePickerLauncher.launch("image/*") }
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (profile.logoUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(profile.logoUri)),
                        contentDescription = "College Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Pick Logo",
                            tint = NavyBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        Text("Tap", fontSize = 10.sp, color = NavyBlue)
                    }
                }
            }

            // College Information
            SectionHeader("College Information")

            ProfileTextField(
                value = profile.collegeName,
                onValueChange = { viewModel.updateProfile(profile.copy(collegeName = it)) },
                label = "College Name (Short)",
                icon = Icons.Filled.School
            )
            ProfileTextField(
                value = profile.collegeFullName,
                onValueChange = { viewModel.updateProfile(profile.copy(collegeFullName = it)) },
                label = "College Full Name"
            )
            ProfileTextField(
                value = profile.tagline,
                onValueChange = { viewModel.updateProfile(profile.copy(tagline = it)) },
                label = "Tagline / Motto"
            )
            ProfileTextField(
                value = profile.establishedYear,
                onValueChange = { viewModel.updateProfile(profile.copy(establishedYear = it)) },
                label = "Established Year"
            )

            // Library Details
            SectionHeader("Library Details")

            ProfileTextField(
                value = profile.libraryName,
                onValueChange = { viewModel.updateProfile(profile.copy(libraryName = it)) },
                label = "Library Name"
            )
            ProfileTextField(
                value = profile.address,
                onValueChange = { viewModel.updateProfile(profile.copy(address = it)) },
                label = "Address",
                icon = Icons.Filled.LocationOn
            )
            ProfileTextField(
                value = profile.phone,
                onValueChange = { viewModel.updateProfile(profile.copy(phone = it)) },
                label = "Phone",
                icon = Icons.Filled.Phone
            )
            ProfileTextField(
                value = profile.email,
                onValueChange = { viewModel.updateProfile(profile.copy(email = it)) },
                label = "Email",
                icon = Icons.Filled.Email
            )
            ProfileTextField(
                value = profile.website,
                onValueChange = { viewModel.updateProfile(profile.copy(website = it)) },
                label = "Website",
                icon = Icons.Filled.Public
            )

            // Staff Information
            SectionHeader("Staff Information")

            ProfileTextField(
                value = profile.principalName,
                onValueChange = { viewModel.updateProfile(profile.copy(principalName = it)) },
                label = "Principal Name",
                icon = Icons.Filled.Person
            )
            ProfileTextField(
                value = profile.librarianName,
                onValueChange = { viewModel.updateProfile(profile.copy(librarianName = it)) },
                label = "Librarian Name",
                icon = Icons.Filled.Person
            )

            // System Settings
            SectionHeader("System Settings")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileTextField(
                    value = profile.currency,
                    onValueChange = { viewModel.updateProfile(profile.copy(currency = it)) },
                    label = "Currency",
                    modifier = Modifier.weight(1f)
                )
                ProfileTextField(
                    value = profile.fineUnit,
                    onValueChange = { viewModel.updateProfile(profile.copy(fineUnit = it)) },
                    label = "Fine Unit",
                    modifier = Modifier.weight(1f)
                )
            }
            ProfileTextField(
                value = profile.memberIdPrefix,
                onValueChange = { viewModel.updateProfile(profile.copy(memberIdPrefix = it)) },
                label = "Member ID Prefix (e.g. STU, LIB)"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = { viewModel.saveProfile() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, tint = Gold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isOnboarding) "Complete Setup" else "Save Profile",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfilePreviewCard(profile: CollegeProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(NavyBlue, Color(0xFF283593))
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (profile.logoUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(Uri.parse(profile.logoUri)),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(2.dp, Gold, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = profile.collegeFullName.ifEmpty { "College Name" },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (profile.tagline.isNotEmpty()) {
                    Text(
                        text = "\"${profile.tagline}\"",
                        color = Gold,
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.libraryName.ifEmpty { "Library" },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                if (profile.address.isNotEmpty()) {
                    Text(
                        text = profile.address,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                if (profile.establishedYear.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Est. ${profile.establishedYear}",
                        color = Gold.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = NavyBlue,
        modifier = Modifier.padding(top = 8.dp)
    )
    HorizontalDivider(color = Gold.copy(alpha = 0.5f), thickness = 1.dp)
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = icon?.let {
            { Icon(it, contentDescription = null, tint = NavyBlue) }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NavyBlue,
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
            focusedLabelColor = NavyBlue,
            cursorColor = NavyBlue
        ),
        singleLine = true
    )
}
