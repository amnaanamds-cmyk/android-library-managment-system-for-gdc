package com.college.library.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One stat-card template for every dashboard tile — see DESIGN_SPEC.md §3.
 * Icon chip (tinted, not the whole card colored) + uppercase label + big
 * value + optional caption + optional progress bar. Card surface/text
 * always come from MaterialTheme.colorScheme, so this is correct in both
 * light and dark mode automatically — the accent color only tints the
 * icon chip and progress fill, per the spec's "categories are neutral,
 * hue is reserved for state" rule.
 */
@Composable
fun StatCard(
    icon: String,
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    /** 0-100, or null for no progress bar. */
    progress: Float? = null,
) {
    var triggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { triggered = true }
    val animatedValue by animateIntAsState(
        targetValue = if (triggered) value else 0,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "statCardCountUp",
    )
    StatCardShell(icon, label, animatedValue.toString(), accent, modifier, sub, progress)
}

/** Same template with a pre-formatted, non-animated value — for currency
 * ("Rs. 1,234.00") or anything else that isn't a plain count. */
@Composable
fun StatCard(
    icon: String,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    progress: Float? = null,
) {
    StatCardShell(icon, label, value, accent, modifier, sub, progress)
}

@Composable
private fun StatCardShell(
    icon: String,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier,
    sub: String?,
    progress: Float?,
) {
    Card(
        modifier = modifier.height(112.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(icon, fontSize = 15.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    label.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.5.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    sub,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
