package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.Version
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*

@Composable
fun SettingsScreen(app: AppState) {
    val repo = app.repo
    val tabs = listOf("Business profile", "Users & permissions", "Branches", "Subscription", "About")
    var tab by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Settings", "How the business is set up and who can use it")
        TabBar(tabs, tab, { tab = it })
        Spacer(Modifier.height(16.dp))
        when (tab) {
            0 -> BusinessProfileTab(app)
            1 -> UsersTab(app, tick) { tick++ }
            2 -> BranchesTab(app, tick) { tick++ }
            3 -> SubscriptionTab(repo)
            4 -> AboutTab(app)
        }
    }
}

@Composable
private fun BusinessProfileTab(app: AppState) {
    val repo = app.repo
    val p = remember(app.tick) { repo.profile() }
    var name by remember(p) { mutableStateOf(p.str("name")) }
    var phone by remember(p) { mutableStateOf(p.str("phone")) }
    var email by remember(p) { mutableStateOf(p.str("email")) }
    var address by remember(p) { mutableStateOf(p.str("address")) }
    var tin by remember(p) { mutableStateOf(p.str("tin")) }
    var motto by remember(p) { mutableStateOf(p.str("motto")) }

    Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        CardBox(Modifier.weight(1.4f)) {
            Text("Business details", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Text("These appear on receipts, statements and reports.", fontSize = 11.sp, color = Da.Muted)
            Spacer(Modifier.height(14.dp))
            Field("Business name", name, { name = it }, placeholder = "Kyenjojo Hardware Ltd")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Phone", phone, { phone = it }, Modifier.weight(1f))
                Field("Email", email, { email = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Field("Address / location", address, { address = it }, Modifier.weight(1f))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("TIN", tin, { tin = it }, Modifier.weight(1f))
                Field("Motto", motto, { motto = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Btn("Save business profile", {
                runCatching {
                    repo.saveProfile(mapOf(
                        "name" to name.trim(), "phone" to phone.trim(), "email" to email.trim(),
                        "address" to address.trim(), "tin" to tin.trim(), "motto" to motto.trim()
                    ))
                    app.say("Business profile saved")
                    app.refresh()
                }.onFailure { app.say(it.message ?: "Could not save") }
            }, Icons.Default.Save, BtnKind.PRIMARY)
        }
        CardBox(Modifier.weight(1f)) {
            Text("Receipt preview", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(Da.Surface2).padding(14.dp)
            ) {
                Text(name.ifBlank { "Your business name" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                if (address.isNotBlank()) Text(address, fontSize = 11.sp, color = Da.Muted)
                if (phone.isNotBlank()) Text(phone, fontSize = 11.sp, color = Da.Muted)
                if (tin.isNotBlank()) Text("TIN: $tin", fontSize = 11.sp, color = Da.Muted)
                Spacer(Modifier.height(8.dp))
                Text("— — — — — — — — — — —", fontSize = 10.sp, color = Da.Dim)
                Text("RECEIPT  KYN-20261115-0001", fontSize = 11.sp, color = Da.Text)
                Text("Cashier: ${app.userName}", fontSize = 11.sp, color = Da.Muted)
                Text("2 items            ${Fmt.money(24000.0)}", fontSize = 11.sp, color = Da.Text)
                Text("TOTAL             ${Fmt.money(24000.0)}", fontSize = 12.sp, color = Da.Green, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun UsersTab(app: AppState, tick: Int, onChanged: () -> Unit) {
    val repo = app.repo
    var rows by remember(tick, app.tick) { mutableStateOf(repo.db.q("SELECT * FROM users ORDER BY role, full_name")) }
    var add by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Staff who can sign in on this computer", fontSize = 12.sp, color = Da.Muted, modifier = Modifier.weight(1f))
            Btn("Add user", { add = true }, Icons.Default.PersonAdd, BtnKind.PRIMARY)
        }
        CardBox(Modifier.fillMaxWidth(), padding = 0.dp) {
            Table(
                listOf("Name", "Username", "Role", "Branch", "PIN", "Active"),
                listOf(2.2f, 1.4f, 1.1f, 1.2f, 1f, 0.8f),
                rows.map { u ->
                    listOf(
                        Cell(u.str("full_name")),
                        Cell(u.str("username"), Da.Muted),
                        Cell(u.str("role")),
                        Cell(repo.db.one("SELECT name FROM branches WHERE id = ?", u.str("branch_id"))?.str("name") ?: "—", Da.Muted),
                        Cell(if (u.str("pin_hash").isBlank()) "none" else "set", Da.Muted),
                        Cell(if (u.int("is_active") == 1) "Yes" else "No", if (u.int("is_active") == 1) Da.Green else Da.Red)
                    )
                }
            )
        }
        if (rows.isEmpty()) EmptyState("No users yet")
    }

    if (add) AddUserDialog(app) { add = false; onChanged() }
}

@Composable
private fun AddUserDialog(app: AppState, onClose: () -> Unit) {
    val repo = app.repo
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("CASHIER") }
    var branch by remember { mutableStateOf(app.repo.branchId()) }
    val branches = remember { repo.branches().map { it.str("id") to it.str("name") } }
    var branchPick by remember { mutableStateOf(branches.firstOrNull()?.second ?: "Main Branch") }

    DialogBox("Add user", onClose) {
        Field("Full name", fullName, { fullName = it })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Username", username, { username = it }, Modifier.weight(1f))
            Field("PIN (4-6 digits)", pin, { pin = it.filter { c -> c.isDigit() }.take(6) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Drop("Role", listOf("CASHIER", "MANAGER", "OWNER"), role, { role = it }, Modifier.weight(1f))
            Drop("Branch", branches.map { it.second }, branchPick, { branchPick = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text("Cashiers can sell, take stock and see their shift. They cannot open the books, reports or settings.",
            fontSize = 11.sp, color = Da.Dim)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Create user", {
                runCatching {
                    if (fullName.isBlank() || username.isBlank()) error("Name and username are required")
                    if (pin.length < 4) error("PIN must be at least 4 digits")
                    val exists = repo.db.one("SELECT id FROM users WHERE username = ?", username.trim().lowercase())
                    if (exists != null) error("That username is already taken")
                    repo.db.insert("users", mapOf(
                        "id" to Uuid.v4(), "username" to username.trim().lowercase(),
                        "pin_hash" to Uuid.hash(pin), "full_name" to fullName.trim(), "role" to role,
                        "branch_id" to (branches.firstOrNull { it.second == branchPick }?.first ?: repo.branchId()),
                        "is_active" to 1, "created_at" to Clock.nowIso()
                    ))
                    app.say("User created"); app.refresh(); onClose()
                }.onFailure { app.say(it.message ?: "Could not create user") }
            }, Icons.Default.Check, BtnKind.PRIMARY)
            Btn("Cancel", onClose, kind = BtnKind.GHOST)
        }
    }
}

@Composable
private fun BranchesTab(app: AppState, tick: Int, onChanged: () -> Unit) {
    val repo = app.repo
    var rows by remember(tick, app.tick) { mutableStateOf(repo.branches()) }
    var add by remember { mutableStateOf(false) }

    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Shops under this business. Stock and sales are tracked per branch.", fontSize = 12.sp, color = Da.Muted, modifier = Modifier.weight(1f))
            Btn("Add branch", { add = true }, Icons.Default.AddBusiness, BtnKind.PRIMARY)
        }
        CardBox(Modifier.fillMaxWidth(), padding = 0.dp) {
            Table(
                listOf("Branch", "Location", "Active"),
                listOf(2f, 2f, 1f),
                rows.map { b ->
                    listOf(
                        Cell(b.str("name")),
                        Cell(b.str("location"), Da.Muted),
                        Cell(if (b.int("is_active") == 1) "Yes" else "No", Da.Green)
                    )
                }
            )
        }
    }

    if (add) {
        var name by remember { mutableStateOf("") }
        var location by remember { mutableStateOf("") }
        DialogBox("Add branch", { add = false }) {
            Field("Branch name", name, { name = it })
            Spacer(Modifier.height(10.dp))
            Field("Location", location, { location = it })
            Spacer(Modifier.height(16.dp))
            Btn("Save branch", {
                runCatching {
                    if (name.isBlank()) error("Branch name is required")
                    repo.db.insert("branches", mapOf(
                        "id" to Uuid.v4(), "name" to name.trim(), "location" to location.trim(),
                        "is_active" to 1, "created_at" to Clock.nowIso()
                    ))
                    app.say("Branch added"); app.refresh(); onChanged(); add = false
                }.onFailure { app.say(it.message ?: "Could not add branch") }
            }, Icons.Default.Check, BtnKind.PRIMARY)
        }
    }
}

@Composable
private fun SubscriptionTab(repo: Repo) {
    val plans = listOf(
        Triple("Starter", "10,000", "1 shop · 1 user · stock, sales, receipts"),
        Triple("Business", "20,000", "1 shop · 3 users · purchases, customers, VAT"),
        Triple("Professional", "35,000", "2 shops · 5 users · full accounting suite"),
        Triple("Multi-Branch", "60,000", "Unlimited shops · payroll, budgets, consolidation")
    )
    val current = repo.setting("plan", "Business")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        plans.forEach { (name, price, line) ->
            CardBox(Modifier.weight(1f), background = if (name == current) Da.Surface2 else Da.Surface) {
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text("UGX $price", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Da.Green)
                Text("per month", fontSize = 10.sp, color = Da.Dim)
                Spacer(Modifier.height(8.dp))
                Text(line, fontSize = 11.sp, color = Da.Muted)
                Spacer(Modifier.height(10.dp))
                if (name == current) Pill("Current plan", Da.Green) else Text("Contact DeryCode to switch", fontSize = 10.sp, color = Da.Dim)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    CardBox(Modifier.fillMaxWidth()) {
        Text("Licensing", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
        Text("The desktop app is licensed per business and works fully offline. Activation codes are issued by DeryCode on 0762 306 675 or derycode.online. " +
            "Your books always stay readable on this computer, whatever happens to the licence.",
            fontSize = 11.5.sp, color = Da.Muted, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun AboutTab(app: AppState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        CardBox(Modifier.weight(1f)) {
            Text("DeryAccount for Windows", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Text("Version ${Version.name} (build ${Version.code})", fontSize = 11.5.sp, color = Da.Muted)
            Spacer(Modifier.height(10.dp))
            FormRow("Business", app.repo.profile().str("name").ifBlank { "Not set" })
            FormRow("Branch", app.repo.branchName(app.branchId))
            FormRow("Data file", app.repo.dbFile().name)
            FormRow("Offline", "Yes — no internet needed")
            Spacer(Modifier.height(12.dp))
            Text("DeryCode Technologies · Kyenjojo, Uganda", fontSize = 11.sp, color = Da.Dim)
            Text("derycode.online · 0762 306 675", fontSize = 11.sp, color = Da.Dim)
        }
        CardBox(Modifier.weight(1f)) {
            Text("What this build includes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Point of sale with receipts, held bills and mobile money",
                "Stock: deliveries, counts, write-offs, transfers, batches, serials",
                "Purchases, purchase orders and supplier returns",
                "Customers, suppliers, credit balances and aging",
                "Double-entry books: journal, trial balance, profit & loss, balance sheet, VAT",
                "Reports: best sellers, dead stock, profit by product, branches",
                "Fixed assets with monthly depreciation, payroll, budgets, bank reconciliation",
                "Backup, restore and CSV import/export"
            ).forEach { Text("•  $it", fontSize = 11.5.sp, color = Da.Muted, modifier = Modifier.padding(vertical = 2.dp)) }
        }
    }
}
