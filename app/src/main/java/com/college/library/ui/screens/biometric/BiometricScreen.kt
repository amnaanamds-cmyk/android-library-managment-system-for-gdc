package com.college.library.ui.screens.biometric

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.LibraryDatabase
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Member
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.random.Random
import com.college.library.ui.theme.*

// ── ViewModel ───────────────────────────────────────────────────────────────
@HiltViewModel
class BiometricViewModel @Inject constructor(
    private val memberDao: MemberDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allMembers: StateFlow<List<Member>> = memberDao.getAllMembers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered: StateFlow<List<Member>> = combine(allMembers, _searchQuery) { list, q ->
        if (q.isBlank()) list
        else list.filter { m ->
            m.name.contains(q, ignoreCase = true) || m.memberId.contains(q, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(q: String) { _searchQuery.value = q }

    fun enrollBiometric(member: Member, hash: String) {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            memberDao.updateMember(
                member.copy(
                    biometricHash = hash,
                    biometricEnrolDate = today,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    fun verifyAndUpdateTimestamp(member: Member) {
        viewModelScope.launch {
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            memberDao.updateMember(
                member.copy(biometricLastVerified = now, lastUpdated = System.currentTimeMillis())
            )
        }
    }

    fun removeBiometric(member: Member) {
        viewModelScope.launch {
            memberDao.updateMember(
                member.copy(
                    biometricHash = "",
                    biometricEnrolDate = "",
                    biometricLastVerified = "",
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }
}

// ── Mock hash generator ──────────────────────────────────────────────────────
fun generateMockBiometricHash(): String {
    val raw = UUID.randomUUID().toString() + System.currentTimeMillis()
    return MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

// ── Fingerprint Scan Dialog ──────────────────────────────────────────────────
@Composable
fun FingerprintScanDialog(
    memberName: String,
    onScanComplete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var scanned by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale"
    )

    LaunchedEffect(scanning) {
        if (scanning) {
            while (progress < 1f) {
                kotlinx.coroutines.delay(60L)
                progress = (progress + Random.nextFloat() * 0.06f).coerceAtMost(1f)
            }
            scanned = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Fingerprint Scan: $memberName",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(if (scanning && !scanned) scale else 1f)
                        .clip(CircleShape)
                        .background(
                            if (scanned) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (scanned) Icons.Default.CheckCircle else Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = if (scanned) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (scanning) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (scanned) "✅ Scan complete!" else "Scanning… ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (!scanned) {
                    Text("Tap 'Simulate Scan' to begin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    if (!scanned) {
                        Button(onClick = { scanning = true }, enabled = !scanning) {
                            Text(if (scanning) "Scanning…" else "Simulate Scan")
                        }
                    } else {
                        Button(onClick = { onScanComplete(generateMockBiometricHash()) }) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
    }
}

// ── Member Card ──────────────────────────────────────────────────────────────
@Composable
fun BiometricMemberCard(
    member: Member,
    onEnroll: () -> Unit,
    onVerify: () -> Unit,
    onRemove: () -> Unit
) {
    val enrolled = member.biometricHash.isNotBlank()
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(
                    if (enrolled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (enrolled) Icons.Default.Fingerprint else Icons.Default.Person,
                    null,
                    tint = if (enrolled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(member.name, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${member.memberId} · ${member.memberType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (enrolled) "🔐 Enrolled${if (member.biometricEnrolDate.isNotBlank()) " · ${member.biometricEnrolDate}" else ""}"
                    else "⏳ Not Enrolled",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enrolled) Positive else Warning
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End) {
                if (!enrolled) {
                    FilledTonalButton(onClick = onEnroll,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("Enroll", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    FilledTonalButton(onClick = onVerify,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("Verify", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = onRemove,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)) {
                        Text("Remove", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricScreen(
    onNavigateBack: () -> Unit,
    viewModel: BiometricViewModel = hiltViewModel()
) {
    val filtered by viewModel.filtered.collectAsState()
    val allMembers by viewModel.allMembers.collectAsState()
    val search by viewModel.searchQuery.collectAsState()

    var enrollTarget by remember { mutableStateOf<Member?>(null) }
    var verifyTarget by remember { mutableStateOf<Member?>(null) }
    var verifyResult by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<Member?>(null) }

    val enrolled = allMembers.count { it.biometricHash.isNotBlank() }
    val total = allMembers.size

    enrollTarget?.let { m ->
        FingerprintScanDialog(
            memberName = m.name,
            onScanComplete = { hash -> viewModel.enrollBiometric(m, hash); enrollTarget = null },
            onDismiss = { enrollTarget = null }
        )
    }

    verifyTarget?.let { m ->
        FingerprintScanDialog(
            memberName = m.name,
            onScanComplete = { _ ->
                val score = Random.nextInt(70, 100)
                verifyResult = if (score >= 75) "✅ VERIFIED — Match ${score}%" else "❌ FAILED — Mismatch ${score}%"
                if (score >= 75) viewModel.verifyAndUpdateTimestamp(m)
                verifyTarget = null
            },
            onDismiss = { verifyTarget = null }
        )
    }

    verifyResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { verifyResult = null },
            title = { Text("Verification Result") },
            text = { Text(msg, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) },
            confirmButton = { TextButton(onClick = { verifyResult = null }) { Text("OK") } }
        )
    }

    removeTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove Biometric") },
            text = { Text("Remove biometric data for ${m.name}?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.removeBiometric(m); removeTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biometric Verification") },
                navigationIcon = { IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Stats strip
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("Total", "$total", MaterialTheme.colorScheme.primary),
                    Triple("Enrolled", "$enrolled", Positive),
                    Triple("Pending", "${total - enrolled}", Warning),
                    Triple("Rate", if (total > 0) "${enrolled * 100 / total}%" else "0%", Color(0xFF8B5CF6))
                ).forEach { (label, value, color) ->
                    Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = color)
                            Text(label, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = viewModel::setSearch,
                placeholder = { Text("Search members…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No members found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filtered, key = { it.syncId }) { member ->
                        BiometricMemberCard(
                            member = member,
                            onEnroll = { enrollTarget = member },
                            onVerify = { verifyTarget = member },
                            onRemove = { removeTarget = member }
                        )
                    }
                }
            }
        }
    }
}
