package com.college.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.library.data.SyncStatus

@Composable
fun SyncStatusBadge(status: SyncStatus, modifier: Modifier = Modifier) {
    val (dotColor, label) = when (status) {
        SyncStatus.Synced -> Color(0xFF10B981) to "Synced"
        SyncStatus.Syncing -> Color(0xFFF59E0B) to "Syncing..."
        SyncStatus.Offline -> Color(0xFFEF4444) to "Offline"
        SyncStatus.NotLinked -> Color(0xFFEF4444) to "Not linked — sign in again"
        is SyncStatus.Error -> Color(0xFFEF4444) to "Sync Error"
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(dotColor, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
