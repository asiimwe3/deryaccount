package com.derycode.deryaccount.ui.subscription

/**
 * SubscriptionScreen — DeryAccount Pricing.
 * Shows the 4 subscription tiers (Starter, Business, Professional,
 * Multi-Branch), the current plan/trial status, an activation-code
 * entry box, and "Choose <Plan>" buttons that open WhatsApp to DeryCode
 * support with a pre-filled request (no in-app payment — the app is
 * offline-first, so billing happens over mobile money / bank and a
 * code is sent back to unlock the plan).
 */

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.billing.ActivationResult
import com.derycode.deryaccount.billing.LicenseManager
import com.derycode.deryaccount.billing.PlanTier
import com.derycode.deryaccount.ui.theme.*

private const val SUPPORT_WHATSAPP = "256762306675"

private fun planColor(plan: PlanTier): Color = when (plan) {
    PlanTier.STARTER -> DaGreen
    PlanTier.BUSINESS -> DaBlue
    PlanTier.PROFESSIONAL -> Color(0xFF8B5CF6)
    PlanTier.MULTI_BRANCH -> Color(0xFFF97316)
}

private fun planIcon(plan: PlanTier) = when (plan) {
    PlanTier.STARTER -> Icons.Default.Storefront
    PlanTier.BUSINESS -> Icons.Default.Apartment
    PlanTier.PROFESSIONAL -> Icons.Default.TrendingUp
    PlanTier.MULTI_BRANCH -> Icons.Default.AccountTree
}

private fun money(v: Long) = "UGX %,d".format(v)

@Composable
fun SubscriptionScreen(onBack: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var state by remember { mutableStateOf(LicenseManager.state(context)) }
    var code by remember { mutableStateOf("") }
    var activateMsg by remember { mutableStateOf<String?>(null) }
    var activateOk by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(DaBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // ---- header ----
        Text("DeryAccount Pricing", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
            color = DaTextPrimary)
        Text("Powerful Accounting, Inventory & POS for Every Business",
            fontSize = 13.sp, color = DaTextMuted)
        Spacer(Modifier.height(10.dp))

        // ---- current status card ----
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DaSurface)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = planColor(state.effectivePlan))
                    Spacer(Modifier.width(8.dp))
                    Text("Current plan: ${state.effectivePlan.displayName}",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        state.isLicensed -> "Licensed — active until " + shortDate(state.expiresAt)
                        state.isTrial -> "Free trial — ${state.trialDaysLeft} day(s) left. Every feature is unlocked."
                        else -> "Trial ended — running on Starter features. Choose a plan below to unlock more."
                    },
                    fontSize = 12.sp, color = DaTextMuted
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---- founding 100 banner ----
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DaGreen.copy(alpha = 0.14f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, DaGreen.copy(alpha = 0.5f))
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = DaAmber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("FOUNDING 100 OFFER — Special prices for the first 100 businesses",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary)
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- plan cards ----
        PlanTier.values().forEach { plan ->
            PlanCard(
                plan = plan,
                isCurrent = state.effectivePlan == plan && (state.isLicensed || state.isTrial),
                onChoose = {
                    val msg = "Hi DeryCode, I'd like to subscribe to the ${plan.displayName} plan " +
                        "for DeryAccount (${money(plan.monthlyUgx)}/month or " +
                        "${money(plan.foundingYearlyUgx)}/year Founding 100 offer). Please send payment details."
                    openWhatsApp(context, SUPPORT_WHATSAPP, msg)
                }
            )
            Spacer(Modifier.height(12.dp))
        }

        // ---- trust footer ----
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DaSurface)) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TrustItem(Icons.Default.Shield, "Safe & Secure", "Your data is encrypted")
                TrustItem(Icons.Default.CloudOff, "Works Offline", "Keep working without internet")
                TrustItem(Icons.Default.Call, "Great Support", "We are here to help you grow")
                TrustItem(Icons.Default.TrendingUp, "Grow Your Business", "Powerful tools to profit")
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("All plans include: Regular Updates, New Features, Technical Support, Data Backup",
            fontSize = 11.sp, color = DaTextMuted, modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(18.dp))

        // ---- activation code entry ----
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DaSurface)) {
            Column(Modifier.padding(14.dp)) {
                Text("Already paid? Enter your activation code", fontSize = 14.sp,
                    fontWeight = FontWeight.Bold, color = DaTextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code, onValueChange = { code = it; activateMsg = null },
                    placeholder = { Text("DERY-XXXX-XXXX-XXXX-XXXX") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        when (val r = LicenseManager.activate(context, code)) {
                            is ActivationResult.Success -> {
                                activateOk = true
                                activateMsg = "Activated: ${r.plan.displayName} until ${shortDate(r.expiresAt)} ✓"
                                state = LicenseManager.state(context)
                                code = ""
                            }
                            is ActivationResult.Expired -> {
                                activateOk = false
                                activateMsg = "That code has already expired — contact support for a new one."
                            }
                            is ActivationResult.InvalidCode -> {
                                activateOk = false
                                activateMsg = "That code doesn't look right — check it and try again."
                            }
                        }
                    },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Activate") }
                activateMsg?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 12.sp, color = if (activateOk) DaGreen else DaRed)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlanCard(plan: PlanTier, isCurrent: Boolean, onChoose: () -> Unit) {
    val color = planColor(plan)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DaSurface),
        border = androidx.compose.foundation.BorderStroke(
            if (plan.popular || isCurrent) 2.dp else 1.dp,
            if (isCurrent) DaGreen else if (plan.popular) color else DaOutline)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (plan.popular) {
                Surface(color = color, shape = RoundedCornerShape(6.dp)) {
                    Text("★ MOST POPULAR", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            if (isCurrent) {
                Surface(color = DaGreen, shape = RoundedCornerShape(6.dp)) {
                    Text("✓ CURRENT PLAN", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.18f)) {
                    Icon(planIcon(plan), null, tint = color, modifier = Modifier.padding(8.dp).size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(plan.displayName.uppercase(), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                        color = color)
                    Text(plan.tagline, fontSize = 11.sp, color = DaTextMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(money(plan.monthlyUgx), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = DaTextPrimary)
            Text("per month", fontSize = 11.sp, color = DaTextMuted)
            Spacer(Modifier.height(8.dp))
            Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(10.dp).fillMaxWidth()) {
                    Text("FOUNDING 100 PRICE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(money(plan.foundingYearlyUgx) + " / year", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = DaTextPrimary)
                        Spacer(Modifier.width(8.dp))
                        Surface(color = color, shape = RoundedCornerShape(4.dp)) {
                            Text("Save 17%", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            plan.features.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(f, fontSize = 12.sp, color = DaTextPrimary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onChoose,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Choose ${plan.displayName}") }
        }
    }
}

@Composable
private fun TrustItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
        Icon(icon, null, tint = DaGreen, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DaTextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(sub, fontSize = 9.sp, color = DaTextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

private fun shortDate(millis: Long?): String {
    if (millis == null) return "-"
    return java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US).format(java.util.Date(millis))
}

private fun openWhatsApp(context: android.content.Context, phone: String, message: String) {
    try {
        val uri = Uri.parse("https://wa.me/$phone?text=" + Uri.encode(message))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val uri = Uri.parse("https://wa.me/$phone?text=" + Uri.encode(message))
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) { }
    }
}
