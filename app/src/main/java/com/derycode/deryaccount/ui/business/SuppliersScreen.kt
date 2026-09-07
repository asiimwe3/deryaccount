package com.derycode.deryaccount.ui.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Supplier
import com.derycode.deryaccount.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SuppliersScreen — manage suppliers (who you buy your stock from).
 * Lists every supplier with the balance you owe them, and lets you add,
 * edit or remove a supplier. Payments to suppliers post from
 * More → Supplier Payments; purchase orders and purchase returns
 * pick suppliers from this list.
 */
@Composable
fun SuppliersScreen(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Supplier?>(null) }
    var deleting by remember { mutableStateOf<Supplier?>(null) }

    LaunchedEffect(tick) {
        try { suppliers = db.supplierDao().observeAll().first() } catch (_: Exception) {}
    }

    if (showAdd) { SupplierDialog(db, null) { showAdd = false; tick++ } }
    editing?.let { s -> SupplierDialog(db, s) { editing = null; tick++ } }
    deleting?.let { s ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("Delete ${s.name}?") },
            text = { Text("The supplier is removed from your list. Existing purchase "
                + "records and payments stay in the books.") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    try { db.supplierDao().upsert(s.copy(isDeleted = true)) } catch (_: Exception) {}
                    deleting = null; tick++
                }
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Suppliers", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("${suppliers.size} supplier(s) · what you owe: UGX %,d".format(
                    suppliers.sumOf { it.balance }.toLong()),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("  Add Supplier")
            }
        }
        Spacer(Modifier.height(12.dp))

        if (suppliers.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Business, null, tint = DaGreen, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No suppliers yet", fontWeight = FontWeight.Bold)
                    Text("Add the shops and wholesalers you buy your stock from — "
                        + "then purchase orders, purchase returns and supplier payments can use them.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showAdd = true }) { Text("+ Add your first supplier") }
                }
            }
        } else {
            LazyColumn {
                items(suppliers, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                s.phone?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (s.balance > 0) Text("You owe: UGX %,d".format(s.balance.toLong()),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DaAmber)
                                else Text("No balance", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { editing = s }) { Icon(Icons.Default.Edit, "Edit") }
                            IconButton(onClick = { deleting = s }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }
}

/** Add / edit a supplier (name + phone). */
@Composable
fun SupplierDialog(db: AppDatabase, existing: Supplier?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDone,
        title = { Text(if (existing == null) "Add Supplier" else "Edit Supplier") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Supplier name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it },
                label = { Text("Phone (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        } },
        confirmButton = { TextButton(onClick = {
            if (name.isBlank()) { error = "Name is required."; return@TextButton }
            scope.launch {
                val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    java.util.Locale.US).format(java.util.Date())
                try {
                    db.supplierDao().upsert(existing?.copy(name = name.trim(), phone = phone.trim(),
                        updatedAt = now)
                        ?: Supplier(id = UUID.randomUUID().toString(), name = name.trim(),
                            phone = phone.trim().ifBlank { null }, balance = 0.0,
                            createdAt = now, updatedAt = now))
                } catch (_: Exception) {}
                onDone()
            }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}
