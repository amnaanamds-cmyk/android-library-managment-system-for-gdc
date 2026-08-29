package com.college.library.ui.screens.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.college.library.data.DatabaseHelper
import com.college.library.data.db.Members
import com.college.library.data.db.adapters.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

import com.college.library.data.SyncManager
import com.college.library.data.SyncDatabaseHelper

@Composable
fun MembersScreen() {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isAddMemberModalOpen by remember { mutableStateOf(false) }

    val database = remember { DatabaseHelper.getDatabase() }
    val syncService = remember { SyncManager.getSyncService(database) }
    
    val members by database.memberQueriesQueries.getAllMembers()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .collectAsState(initial = emptyList<Members>())

    val filteredMembers = members.map { it.toModel() }
        .filter { !it.deleted }
        .filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.memberId.contains(searchQuery, ignoreCase = true) ||
            it.email.contains(searchQuery, ignoreCase = true) 
        }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name, ID, or email...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier.width(400.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        textColor = MaterialTheme.colors.onSurface
                    )
                )

                Button(
                    onClick = { isAddMemberModalOpen = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF059669), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(52.dp).padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Member")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add New Member", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(12.dp),
                elevation = 0.dp,
                backgroundColor = MaterialTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.05f))
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text("Name", modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text("Member ID", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text("Type", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text("Books Issued", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                        Text("Actions", modifier = Modifier.width(100.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    }
                    Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))

                    if (filteredMembers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No members found.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                        }
                    } else {
                        filteredMembers.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(member.name, modifier = Modifier.weight(2f), fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface)
                                Text(member.memberId, modifier = Modifier.weight(1.5f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                                Text(member.memberType, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                                Text(member.booksIssued.toString(), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
                                
                                Row(modifier = Modifier.width(100.dp), horizontalArrangement = Arrangement.Center) {
                                    IconButton(onClick = { /* Edit */ }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colors.primary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { 
                                        coroutineScope.launch(Dispatchers.IO) {
                                            database.memberQueriesQueries.softDeleteMember(System.currentTimeMillis(), member.id)
                                        }
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colors.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
                        }
                    }
                }
            }
        }

        if (isAddMemberModalOpen) {
            AddMemberModal(
                onClose = { isAddMemberModalOpen = false },
                onSave = { data ->
                    coroutineScope.launch(Dispatchers.IO) {
                        database.memberQueriesQueries.insertMember(
                            syncId = UUID.randomUUID().toString(),
                            memberId = data.memberId,
                            name = data.name,
                            email = data.email,
                            phone = data.phone,
                            department = data.department,
                            memberType = data.memberType,
                            joinDate = "2026-07-01",
                            expiryDate = "2027-07-01",
                            booksIssued = 0,
                            fatherName = data.fatherName,
                            className = data.className,
                            classNo = data.classNo,
                            address = "",
                            photoUri = data.photoUri,
                            designation = data.designation,
                            bps = data.bps,
                            pin = data.pin,
                            lastUpdated = System.currentTimeMillis(),
                            deleted = false
                        )
                        isAddMemberModalOpen = false
                        // Trigger sync push immediately
                        try {
                            syncService.pushChanges()
                        } catch (e: Exception) {}
                    }
                }
            )
        }
    }
}

data class MemberFormData(
    val memberId: String, val name: String, val email: String, val phone: String, 
    val department: String, val memberType: String, val fatherName: String, 
    val className: String, val classNo: String, val designation: String, 
    val bps: String, val pin: String, val photoUri: String
)

@Composable
fun AddMemberModal(onClose: () -> Unit, onSave: (MemberFormData) -> Unit) {
    var name by remember { mutableStateOf("") }
    var memberId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var memberType by remember { mutableStateOf("Student") } // Student, Faculty, Staff
    var department by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var classNo by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var bps by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier.width(650.dp).height(700.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = 16.dp,
            backgroundColor = MaterialTheme.colors.surface
        ) {
            Column(modifier = Modifier.padding(32.dp).fillMaxSize()) {
                Text("Add New Member", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colors.primary)
                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    OutlinedTextField(value = fatherName, onValueChange = { fatherName = it }, label = { Text("Father's Name") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = memberId, onValueChange = { memberId = it }, label = { Text("Member ID (Roll No)") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = className, onValueChange = { className = it }, label = { Text("Class Name") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    OutlinedTextField(value = classNo, onValueChange = { classNo = it }, label = { Text("Class Number") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = designation, onValueChange = { designation = it }, label = { Text("Designation (Faculty)") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    OutlinedTextField(value = bps, onValueChange = { bps = it }, label = { Text("BPS") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Login PIN") }, modifier = Modifier.weight(1f), colors = TextFieldDefaults.outlinedTextFieldColors(textColor = MaterialTheme.colors.onSurface))
                    Button(
                        onClick = {
                            val fileChooser = JFileChooser().apply {
                                fileFilter = FileNameExtensionFilter("Images", "jpg", "png", "jpeg")
                                dialogTitle = "Select Member Photo"
                            }
                            if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                photoUri = fileChooser.selectedFile.absolutePath
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp).padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = MaterialTheme.colors.onPrimary)
                    ) {
                        Text(if (photoUri.isEmpty()) "Upload Photo" else "Photo Selected")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose, modifier = Modifier.height(48.dp)) {
                        Text("Cancel", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { 
                            onSave(MemberFormData(memberId, name, email, phone, department, memberType, fatherName, className, classNo, designation, bps, pin, photoUri)) 
                        },
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF059669), contentColor = Color.White)
                    ) {
                        Text("Save Member", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
