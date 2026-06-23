package com.college.library.ui.screens.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Member
import com.college.library.ui.theme.NavyBlue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class AddEditMemberViewModel @Inject constructor(
    private val memberDao: MemberDao
) : ViewModel() {
    private val _memberState = MutableStateFlow(Member(0, "", "", "", "", "", "Student", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), LocalDate.now().plusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE), 0))
    val memberState = _memberState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun loadMember(id: Long) {
        if (id == 0L) return
        viewModelScope.launch {
            memberDao.getMemberById(id)?.let { _memberState.value = it }
        }
    }

    fun updateField(updater: (Member) -> Member) {
        _memberState.value = updater(_memberState.value)
    }

    fun saveMember(onComplete: () -> Unit) {
        val member = _memberState.value
        if (member.memberId.trim().isBlank()) {
            _errorMessage.value = "Failed to save: Member ID cannot be empty"
            return
        }
        if (member.name.trim().isBlank()) {
            _errorMessage.value = "Failed to save: Name cannot be empty"
            return
        }
        viewModelScope.launch {
            try {
                if (_memberState.value.id == 0L) {
                    memberDao.insertMember(_memberState.value)
                } else {
                    memberDao.updateMember(_memberState.value)
                }
                onComplete()
            } catch (e: Exception) {
                // E.g., SQLiteConstraintException for duplicate memberId
                _errorMessage.value = "Failed to save: Member ID must be unique"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMemberScreen(
    memberId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: AddEditMemberViewModel = hiltViewModel()
) {
    val member by viewModel.memberState.collectAsState()
    val error by viewModel.errorMessage.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.updateField { m -> m.copy(photoUri = it.toString()) } }
    }

    LaunchedEffect(memberId) { viewModel.loadMember(memberId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (memberId == 0L) "Add Member" else "Edit Member", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                LaunchedEffect(error) {
                    kotlinx.coroutines.delay(3000)
                    viewModel.clearError()
                }
            }

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (member.photoUri != null) {
                        AsyncImage(
                            model = member.photoUri,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile Picture Placeholder",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("Upload Photo")
                    }
                }
            }

            OutlinedTextField(
                value = member.memberId,
                onValueChange = { viewModel.updateField { m -> m.copy(memberId = it) } },
                label = { Text("Member ID") },
                modifier = Modifier.fillMaxWidth(),
                isError = error != null
            )
            OutlinedTextField(
                value = member.name,
                onValueChange = { viewModel.updateField { m -> m.copy(name = it) } },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = member.email,
                onValueChange = { viewModel.updateField { m -> m.copy(email = it) } },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = member.phone,
                onValueChange = { viewModel.updateField { m -> m.copy(phone = it) } },
                label = { Text("Phone Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Member Type Dropdown
            var typeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = member.memberType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Member Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    listOf("Student", "Faculty", "Staff").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                val newId = if (option == "Faculty" && member.memberId.isBlank()) "FAC-${System.currentTimeMillis() % 100000}" else member.memberId
                                viewModel.updateField { m -> m.copy(memberType = option, memberId = newId) }
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            if (member.memberType == "Student") {
                OutlinedTextField(
                    value = member.fatherName,
                    onValueChange = { viewModel.updateField { m -> m.copy(fatherName = it) } },
                    label = { Text("Father's Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = member.className,
                    onValueChange = { viewModel.updateField { m -> m.copy(className = it) } },
                    label = { Text("Class / Semester") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = member.classNo,
                    onValueChange = { viewModel.updateField { m -> m.copy(classNo = it) } },
                    label = { Text("Class No / Roll No") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = member.address,
                    onValueChange = { viewModel.updateField { m -> m.copy(address = it) } },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = member.pin,
                    onValueChange = { viewModel.updateField { m -> m.copy(pin = it) } },
                    label = { Text("OPAC 4-Digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (member.memberType == "Faculty") {
                OutlinedTextField(
                    value = member.designation,
                    onValueChange = { viewModel.updateField { m -> m.copy(designation = it) } },
                    label = { Text("Designation") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = member.bps,
                    onValueChange = { viewModel.updateField { m -> m.copy(bps = it) } },
                    label = { Text("BPS (Basic Pay Scale)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = member.department,
                onValueChange = { viewModel.updateField { m -> m.copy(department = it) } },
                label = { Text(if (member.memberType == "Faculty") "Department / Subject" else "Department") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = member.joinDate,
                onValueChange = { viewModel.updateField { m -> m.copy(joinDate = it) } },
                label = { Text("Date of Membership (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Icon(Icons.Default.DateRange, "Date") }
            )
            OutlinedTextField(
                value = member.expiryDate,
                onValueChange = { viewModel.updateField { m -> m.copy(expiryDate = it) } },
                label = { Text("Date of Expiry (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Icon(Icons.Default.DateRange, "Date") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.saveMember(onNavigateBack) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlue)
            ) {
                Text(if (memberId == 0L) "Save Member" else "Update Member", fontSize = 16.sp)
            }
        }
    }
}
