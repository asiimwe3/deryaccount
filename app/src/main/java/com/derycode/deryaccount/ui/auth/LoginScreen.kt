package com.derycode.deryaccount.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.util.SessionManager
import kotlinx.coroutines.launch

/**
 * LoginScreen — username + PIN. Users live in local Room (synced from
 * Supabase once online). First run allows creating the OWNER account
 * locally which then syncs up.
 */
@Composable
fun LoginScreen(
    session: SessionManager,
    db: com.derycode.deryaccount.data.local.AppDatabase,
    onLoggedIn: (userId: String, username: String, role: String, branchId: String, branchName: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isFirstRun by remember { mutableStateOf(false) }
    var branchName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isFirstRun = db.userDao().byUsername("owner") == null
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("DeryAccount", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary)
            Text("Multi-branch Shop Accounting & POS", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))

            if (isFirstRun) {
                OutlinedTextField(
                    value = branchName, onValueChange = { branchName = it },
                    label = { Text("Shop / Branch name (e.g. Kyenjojo Main)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text(if (isFirstRun) "Owner username" else "Username") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = pin, onValueChange = { pin = it },
                label = { Text(if (isFirstRun) "Set PIN (4-6 digits)" else "PIN") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    scope.launch {
                        val now = java.text.SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                            .format(java.util.Date())
                        if (isFirstRun) {
                            if (username.isBlank() || pin.length < 4 || branchName.isBlank()) {
                                error = "Fill all fields (PIN at least 4 digits)"; return@launch
                            }
                            val branchId = java.util.UUID.randomUUID().toString()
                            val userId = java.util.UUID.randomUUID().toString()
                            db.branchDao().upsert(com.derycode.deryaccount.data.local.entity.Branch(
                                id = branchId, name = branchName, location = "",
                                createdAt = now, updatedAt = now))
                            db.userDao().upsert(com.derycode.deryaccount.data.local.entity.User(
                                id = userId, username = username.lowercase(),
                                pinHash = SessionManager.sha256(pin), fullName = "Owner",
                                role = "OWNER", branchId = branchId,
                                createdAt = now, updatedAt = now))
                            session.saveLogin(userId, username.lowercase(), "OWNER", branchId, branchName)
                            onLoggedIn(userId, username.lowercase(), "OWNER", branchId, branchName)
                        } else {
                            val u = db.userDao().byUsername(username.lowercase())
                            if (u == null) { error = "User not found"; return@launch }
                            if (u.pinHash != SessionManager.sha256(pin)) { error = "Wrong PIN"; return@launch }
                            val b = db.branchDao().get(u.branchId)
                            session.saveLogin(u.id, u.username, u.role, u.branchId, b?.name ?: "")
                            onLoggedIn(u.id, u.username, u.role, u.branchId, b?.name ?: "")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = username.isNotBlank() && pin.isNotBlank()
            ) { Text(if (isFirstRun) "Create Owner Account" else "Sign In", fontSize = 16.sp) }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
