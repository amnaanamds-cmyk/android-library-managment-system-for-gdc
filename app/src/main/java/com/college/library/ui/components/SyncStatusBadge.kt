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
import com.college.library.ui.theme.*

@Composable
fun SyncStatusBadge(status: SyncStatus, modifier: Modifier = Modifier) {
    val (dotColor, label) = when (status) {
        SyncStatus.Synced -> Positive to "Synced"
        SyncStatus.Syncing -> Warning to "Syncing..."
        SyncStatus.Offline -> Danger to "Offline"
        is SyncStatus.Error -> Danger to "Sync Error"
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
