@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.derycode.deryaccount.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.ui.theme.DaGreen
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Aging reports — WHO owes you money (and who you owe), bucketed by how
 * long the oldest open invoice has been unpaid: 0-30 / 31-60 / 61-90 / 90+.
 */

private fun ugx(v: Double) = "UGX %,d".format(v.toLong())

private fun parseIso(iso: String): Date? = try {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(iso)
} catch (_: Exception) { null }

private fun daysAgo(iso: String): Int {
    val d = parseIso(iso) ?: return 9999
    return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - d.time).toInt()
}

private fun bucketOf(days: Int): Int = when {
    days <= 30 -> 0
    days <= 60 -> 1
    days <= 90 -> 2
    else -> 3
}

private val BUCKET_LABELS = listOf("0 – 30 days", "31 – 60 days", "61 – 90 days", "Over 90 days")
private val BUCKET_SHORT = listOf("Current", "31-60", "61-90", "90+")

/** Per-debtor row: customer, their balance, oldest open credit invoice. */
private data class AgingRow(
    val name: String, val phone: String?, val balance: Double,
    val oldest: String, val days: Int, val openInvoiceTotal: Double)

@Composable
private fun AgingBuckets(rows: List<AgingRow>, emptyText: String, whoText: String) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val buckets = BUCKET_LABELS.indices.map { b -> rows.filter { bucketOf(it.days) == b } }
    Column {
        buckets.forEachIndexed { b, list ->
            if (list.isNotEmpty()) {
                Text("${BUCKET_LABELS[b]} — ${ugx(list.sumOf { it.balance })}",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (b >= 2) MaterialTheme.colorScheme.error else DaGreen,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                list.forEach { r ->
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(whoText + " open invoices: ${ugx(r.openInvoiceTotal)}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(ugx(r.balance), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = if (bucketOf(r.days) >= 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                Text("${BUCKET_SHORT[bucketOf(r.days)]} · oldest ${r.days}d",
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerAgingScreen(db: AppDatabase, branchId: String) {
    var rows by remember { mutableStateOf<List<AgingRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        val customers = db.customerDao().allOnce()
            .filter { it.balance > 0.0 }
        val byId = customers.associateBy { it.id }
        val open = db.analyticsDao().openCreditSales(branchId.ifBlank { "" })
        val openByCustomer = open.groupBy { it.customerId ?: "" }
        rows = customers.mapNotNull { c ->
            val invoices = openByCustomer[c.id] ?: emptyList()
            val oldest = invoices.minByOrNull { it.soldAt }
            AgingRow(
                name = c.name, phone = c.phone, balance = c.balance,
                oldest = oldest?.soldAt ?: "",
                days = if (oldest != null) daysAgo(oldest.soldAt) else 9999,
                openInvoiceTotal = invoices.sumOf { it.remaining })
        }.sortedByDescending { it.days }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Customer Aging", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Debtors bucketed by the age of their oldest unpaid credit sale",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Total receivable: ${ugx(rows.sumOf { it.balance })}",
            fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DaGreen,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
        Text("Repayments reduce the customer balance but are not tied to one invoice — " +
            "the balance is the authority; invoice ages show how long debt has stood.",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.padding(top = 6.dp)) {
            item { AgingBuckets(rows, "No customer owes you money. Well collected!", "Their") }
        }
    }
}

@Composable
fun SupplierAgingScreen(db: AppDatabase, branchId: String) {
    var rows by remember { mutableStateOf<List<AgingRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        val supplierList = db.supplierDao().observeAll().first()
        val byId = supplierList.associateBy { it.id }
        val open = db.analyticsDao().openPurchases(branchId.ifBlank { "" })
        val openBySupplier = open.groupBy { it.supplierId ?: "" }
        rows = byId.values.mapNotNull { s ->
            val invoices = openBySupplier[s.id] ?: emptyList()
            if (s.balance <= 0.0 && invoices.isEmpty()) return@mapNotNull null
            val oldest = invoices.minByOrNull { it.receivedAt }
            AgingRow(
                name = s.name, phone = s.phone, balance = s.balance,
                oldest = oldest?.receivedAt ?: "",
                days = if (oldest != null) daysAgo(oldest.receivedAt) else 9999,
                openInvoiceTotal = invoices.sumOf { it.remaining })
        }.sortedByDescending { it.days }
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Supplier Aging", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("What you owe, bucketed by the age of the oldest unpaid purchase",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Total payable: ${ugx(rows.sumOf { it.balance })}",
            fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
        LazyColumn(Modifier.padding(top = 6.dp)) {
            item { AgingBuckets(rows, "You owe no supplier money.", "Open") }
        }
    }
}
