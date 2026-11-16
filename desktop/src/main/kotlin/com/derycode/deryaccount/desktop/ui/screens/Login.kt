package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.Version
import com.derycode.deryaccount.desktop.data.Clock
import com.derycode.deryaccount.desktop.data.Fmt
import com.derycode.deryaccount.desktop.data.Row
import com.derycode.deryaccount.desktop.ui.*

@Composable
fun LoginScreen(app: AppState) {
    val repo = app.repo
    var username by remember { mutableStateOf("owner") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val profile = remember { repo.profile() }
    val users = remember { repo.db.q("SELECT * FROM users WHERE is_active = 1 ORDER BY role") }

    Box(Modifier.fillMaxSize().background(Da.Black), contentAlignment = Alignment.Center) {
        Row(
            Modifier.width(860.dp).height(480.dp)
                .shadow(16.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Da.Surface)
                .then(Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand panel
            Column(
                Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF1F5FF)).padding(34.dp),
                verticalArrangement = Arrangement.Center
            ) {
                BrandLogo(size = 70.dp, radius = 18.dp)
                Spacer(Modifier.height(20.dp))
                Text("DeryAccount", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text("Business & Hotel Suite for Windows", fontSize = 13.sp, color = Da.Brand, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                Text(
                    "Stock, sales, rooms, bookings, guests, folios and full double-entry books — all on this computer, working without internet.",
                    fontSize = 12.5.sp, color = Da.Muted, lineHeight = 19.sp
                )
                Spacer(Modifier.height(26.dp))
                Text("Version ${Version.name} · ${Version.channel}", fontSize = 11.sp, color = Da.Dim)
                Text("DeryCode Technologies · Kyenjojo, Uganda", fontSize = 11.sp, color = Da.Dim)
            }

            // Sign-in panel
            Column(Modifier.weight(1f).padding(38.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    if (profile.str("name").isBlank()) "Welcome. Sign in to set up your shop."
                    else "Welcome back to ${profile.str("name")}",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Da.Text
                )
                Spacer(Modifier.height(6.dp))
                Text("Choose your name, then enter your PIN.", fontSize = 12.sp, color = Da.Muted)
                Spacer(Modifier.height(22.dp))
                Field("Username", username, { username = it; error = null }, placeholder = "owner")
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                            val u = repo.findUser(username.trim().lowercase(), pin)
                            if (u == null) error = "Wrong username or PIN" else { app.user = u; repo.touchLastLogin(u.str("id")) }
                            true
                        } else false
                    }
                ) {
                    Field("PIN", pin, { pin = it.filter { c -> c.isDigit() }.take(6); error = null }, placeholder = "••••", numeric = true)
                }
                Spacer(Modifier.height(6.dp))
                Text("First run: owner / 1234 — change it in Settings once you are in.",
                    fontSize = 10.5.sp, color = Da.Dim)
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(error!!, fontSize = 11.5.sp, color = Da.Red)
                }
                Spacer(Modifier.height(18.dp))
                Btn("Sign in", {
                    val u = repo.findUser(username.trim().lowercase(), pin)
                    if (u == null) error = "Wrong username or PIN" else {
                        app.user = u
                        repo.touchLastLogin(u.str("id"))
                    }
                }, Icons.Default.Login, BtnKind.PRIMARY, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(18.dp))
                if (users.isNotEmpty()) {
                    Text("ACCOUNTS ON THIS COMPUTER", fontSize = 9.5.sp, color = Da.Dim, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(8.dp))
                    users.take(4).forEach { u: Row ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                .clickable { username = u.str("username"); pin = "" }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, null, tint = Da.Muted, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(u.str("full_name"), fontSize = 12.sp, color = Da.Text, modifier = Modifier.weight(1f))
                            Text(u.str("role"), fontSize = 9.5.sp, color = Da.Dim)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Forgotten PIN? Ask the owner to reset it in Settings → Users.", fontSize = 10.sp, color = Da.Dim)
            }
        }
    }
}
