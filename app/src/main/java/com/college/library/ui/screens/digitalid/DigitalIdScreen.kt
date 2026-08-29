package com.college.library.ui.screens.digitalid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.college.library.data.db.MemberDao
import com.college.library.data.model.Member
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class DigitalIdViewModel @Inject constructor(
    private val memberDao: MemberDao
) : ViewModel() {

    private val _member = MutableStateFlow<Member?>(null)
    val member = _member.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap = _qrBitmap.asStateFlow()

    fun loadMember(id: Long) {
        viewModelScope.launch {
            val m = memberDao.getMemberById(id)
            _member.value = m
            if (m != null) generateQr(m)
        }
    }

    private fun generateQr(member: Member) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val json = JSONObject().apply {
                    put("id", member.memberId)
                    put("name", member.name)
                    put("dept", member.department)
                    put("type", member.memberType)
                    put("exp", member.expiryDate)
                }.toString()

                val writer = MultiFormatWriter()
                val matrix: BitMatrix = writer.encode(json, BarcodeFormat.QR_CODE, 300, 300)
                val width = matrix.width
                val height = matrix.height
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                _qrBitmap.value = bmp
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalIdScreen(
    memberId: Long,
    onNavigateBack: () -> Unit,
    viewModel: DigitalIdViewModel = hiltViewModel()
) {
    val member by viewModel.member.collectAsState()
    val qrBitmap by viewModel.qrBitmap.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(memberId) { viewModel.loadMember(memberId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🪪 Digital ID Card", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    member?.let { m ->
                        IconButton(onClick = {
                            val shareText = "📚 GDC Library Member Card\n👤 ${m.name}\n🆔 ${m.memberId}\n🏛️ ${m.department} (${m.memberType})\n📅 Valid till: ${m.expiryDate}"
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share ID Card"))
                        }) {
                            Icon(Icons.Default.Share, "Share", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (member == null) {
                CircularProgressIndicator()
            } else {
                val m = member!!
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // The ID Card itself
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F1E3D), Color(0xFF1A3A6E), Color(0xFF0F1E3D))
                                )
                            )
                            .padding(24.dp)
                    ) {
                        // Gold border highlight
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📚", fontSize = 28.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "GDC LIBRARY50",
                                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFC8A84B), letterSpacing = 1.sp
                                    )
                                    Text(
                                        "Government Degree College",
                                        fontSize = 11.sp, color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFC8A84B).copy(alpha = 0.4f))

                            // Member Info
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Badge, null, tint = Color(0xFFC8A84B), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(m.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF2563EB).copy(alpha = 0.3f)) {
                                    Text(
                                        m.memberType.uppercase(),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF93C5FD)
                                    )
                                }
                            }

                            // QR Code
                            qrBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .padding(8.dp)
                                ) {
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.fillMaxSize())
                                }
                                Text("Scan to verify membership", fontSize = 10.sp, color = Color(0xFF64748B))
                            } ?: CircularProgressIndicator(color = Color(0xFFC8A84B), modifier = Modifier.size(80.dp))

                            HorizontalDivider(color = Color(0xFF334155))

                            // Details row
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                IdCardField("MEMBER ID", m.memberId)
                                IdCardField("DEPARTMENT", m.department)
                                IdCardField("VALID TILL", m.expiryDate)
                            }
                        }
                    }

                    // Info text
                    Text(
                        "This digital ID card is verified by GDC Library System.\nShare or scan the QR code to verify membership.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun IdCardField(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(value.ifBlank { "—" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
    }
}
