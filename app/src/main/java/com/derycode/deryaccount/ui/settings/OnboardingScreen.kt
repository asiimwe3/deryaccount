package com.derycode.deryaccount.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.ui.theme.*
import com.derycode.deryaccount.util.LogoStore
import com.derycode.deryaccount.util.SessionManager
import kotlinx.coroutines.launch

/**
 * Onboarding step 2 of 3: the business profile with logo.
 * Flow after install: create account (login screen) → HERE → Activate
 * Subscription → Dashboard. The app is free for the first 3 days.
 */
@Composable
fun OnboardingScreen(session: SessionManager, onDone: () -> Unit, onSkipToSub: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var tagline by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var tin by remember { mutableStateOf("") }
    var logoPath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val obContext = androidx.compose.ui.platform.LocalContext.current
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { logoPath = LogoStore.save(obContext, it.toString()) }
    }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Set up your business", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        Text("Step 2 of 3 · These details and your logo appear on every receipt, invoice and report.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        // logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (logoPath != null) {
                val bmp = LogoStore.bitmapFrom(logoPath)
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), "Logo",
                        modifier = Modifier.size(84.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop)
                } else {
                    Surface(shape = CircleShape, modifier = Modifier.size(84.dp)) {}
                }
            } else {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(84.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Storefront, null, modifier = Modifier.size(36.dp), tint = DaGreen)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                OutlinedButton(onClick = { pickLogo.launch("image/*") }) {
                    Icon(Icons.Default.AddAPhoto, null, Modifier.size(16.dp)); Text("  Add business logo")
                }
                Text("Optional — makes receipts look professional.", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Business name *") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = tagline, onValueChange = { tagline = it }, label = { Text("Tagline (optional)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = phone, onValueChange = { phone = it },
            label = { Text("Phone (WhatsApp)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = tin, onValueChange = { tin = it }, label = { Text("TIN (optional)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        error?.let {
            Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = {
            if (name.isBlank()) { error = "Business name is required."; return@Button }
            scope.launch {
                session.saveBusinessProfile(SessionManager.BusinessProfile(
                    name = name.trim(), tagline = tagline.trim(), phone = phone.trim(),
                    location = location.trim(), tin = tin.trim(), logoPath = logoPath ?: ""))
                onSkipToSub()   // next step: activate subscription
            }
        }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Save & Continue to Subscription", fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkipToSub) { Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(24.dp))
    }
}
