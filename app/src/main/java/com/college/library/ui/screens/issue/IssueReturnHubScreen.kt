package com.college.library.ui.screens.issue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.college.library.ui.theme.CardGreen
import com.college.library.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueReturnHubScreen(
    onNavigateToIssue: () -> Unit,
    onNavigateToReturn: () -> Unit,
    onNavigateToBulk: () -> Unit,
    onNavigateToInventory: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", color = MaterialTheme.colorScheme.onSurface) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().height(150.dp).clickable { onNavigateToIssue() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Send, contentDescription = "Issue Book", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Issue a Book", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(150.dp).clickable { onNavigateToReturn() },
                colors = CardDefaults.cardColors(containerColor = CardGreen.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(2.dp, CardGreen)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CallReceived, contentDescription = "Return Book", tint = CardGreen, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Return a Book", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CardGreen)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(150.dp).clickable { onNavigateToBulk() },
                colors = CardDefaults.cardColors(containerColor = com.college.library.ui.theme.Gold.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(2.dp, com.college.library.ui.theme.Gold)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryAddCheck, contentDescription = "Bulk Issue", tint = com.college.library.ui.theme.Gold, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Bulk Issue", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = com.college.library.ui.theme.Gold)
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(150.dp).clickable { onNavigateToInventory() },
                colors = CardDefaults.cardColors(containerColor = Muted.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(2.dp, Muted)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory, contentDescription = "Inventory", tint = Muted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Inventory / Stocktaking", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Muted)
                }
            }
        }
    }
}
