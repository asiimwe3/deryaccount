package com.derycode.deryaccount.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.util.SessionManager
import kotlinx.coroutines.launch

/**
 * BusinessProfileDialog — the shop owner's own business identity:
 * name, tagline, phone, location, TIN and the thank-you message on receipts.
 * Everything the shop prints carries this identity.
 */
@Composable
fun BusinessProfileDialog(
    session: SessionManager,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var tagline by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var tin by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        session.businessProfileNow()?.let { p ->
            name = p.name; tagline = p.tagline; phone = p.phone
            location = p.location; tin = p.tin; footer = p.footer
        }
        loaded = true
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Business profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("This is printed on every receipt — your shop's own name and details.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Business name *") }, singleLine = true)
                OutlinedTextField(tagline, { tagline = it },
                    label = { Text("Tagline (e.g. Fresh groceries daily)") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true)
                OutlinedTextField(location, { location = it }, label = { Text("Location / address") },
                    singleLine = true)
                OutlinedTextField(tin, { tin = it }, label = { Text("TIN (optional)") }, singleLine = true)
                OutlinedTextField(footer, { footer = it },
                    label = { Text("Receipt thank-you message") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) return@Button
                scope.launch {
                    session.saveBusinessProfile(SessionManager.BusinessProfile(
                        name = name.trim(),
                        tagline = tagline.trim(),
                        phone = phone.trim(),
                        location = location.trim(),
                        tin = tin.trim(),
                        footer = footer.trim().ifBlank { "Thank you for shopping with us!" }
                    ))
                    onDone()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}
