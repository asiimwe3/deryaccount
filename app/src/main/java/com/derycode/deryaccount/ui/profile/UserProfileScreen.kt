package com.derycode.deryaccount.ui.profile

/**
 * UserProfileScreen — the owner's personal profile: name, phone, email.
 * The phone doubles as the default MSISDN for mobile-money subscription
 * payments, and the name personalises receipts & greetings.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.util.SessionManager
import kotlinx.coroutines.launch

@Composable
fun UserProfileScreen(session: SessionManager) {
    val scope = rememberCoroutineScope()
    val profile by session.userProfile.collectAsState(initial = null)
    var edit by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        // avatar with initials
        val initials = (profile?.name ?: "DA").trim().split(Regex("\\s+"))
            .take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(initials.ifBlank { "DA" }, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        Text(profile?.name ?: "Set up your profile", fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold)
        Text(profile?.email ?: "Add your details so subscriptions and receipts know who you are",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("My details", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                DetailRow(Icons.Default.Person, "Name", profile?.name?.takeIf { it.isNotBlank() } ?: "Not set")
                DetailRow(Icons.Default.Call, "Phone (used for Mobile Money)",
                    profile?.phone?.takeIf { it.isNotBlank() } ?: "Not set")
                DetailRow(Icons.Default.Email, "Email", profile?.email?.takeIf { it.isNotBlank() } ?: "Not set")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { edit = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp))
                    Text(if (profile == null) "Create my profile" else "Edit my profile")
                }
                if (savedMsg) {
                    Spacer(Modifier.height(6.dp))
                    Text("Saved ✓", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Why we ask", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Your phone number is the default number for paying subscriptions " +
                        "with MTN MoMo or Airtel Money.\n" +
                        "• Your name appears on the dashboard and helps support find your " +
                        "payments quickly.\n" +
                        "• Everything stays on this device — DeryAccount works offline.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (edit) {
        EditProfileDialog(session, initial = profile) {
            edit = false
            savedMsg = true
            scope.launch { /* saved inside dialog */ }
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector,
                     label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EditProfileDialog(session: SessionManager,
                              initial: SessionManager.UserProfile?,
                              onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (initial == null) "Create my profile" else "Edit my profile",
            fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone (07xx or +256…)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    scope.launch {
                        session.saveUserProfile(SessionManager.UserProfile(
                            name.trim(), phone.trim(), email.trim()))
                        onDone()
                    }
                }
            }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}
