package com.derycode.deryaccount.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.derycode.deryaccount.desktop.data.AdminOps
import com.derycode.deryaccount.desktop.data.Repo
import com.derycode.deryaccount.desktop.data.Reports
import com.derycode.deryaccount.desktop.data.Row

/** Everything the screens share: who is signed in, which page is open, and a refresh counter. */
class AppState(val repo: Repo, val reports: Reports, val admin: AdminOps) {
    var user by mutableStateOf<Row?>(null)
    var screen by mutableStateOf("dashboard")
    var tick by mutableStateOf(0)
    var toast by mutableStateOf<String?>(null)

    val role: String get() = user?.str("role") ?: "OWNER"
    val userName: String get() = user?.str("full_name") ?: "Owner"
    val isCashier: Boolean get() = role == "CASHIER"
    val branchId: String get() = user?.str("branch_id")?.ifBlank { repo.branchId() } ?: repo.branchId()

    fun refresh() { tick++ }

    fun say(message: String) { toast = message }

    fun go(screen: String) { this.screen = screen }
}
