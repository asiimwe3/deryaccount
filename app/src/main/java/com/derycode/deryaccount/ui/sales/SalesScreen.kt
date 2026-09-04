package com.derycode.deryaccount.ui.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Sale
import com.derycode.deryaccount.data.local.entity.SaleItem
import com.derycode.deryaccount.ui.home.fmtUgx
import com.derycode.deryaccount.ui.theme.*
import kotlinx.coroutines.flow.first

/**
 * SalesScreen — every sale made today, live from the local database.
 * Tap a sale to see its full item breakdown (SaleDetailsScreen).
 */
@Composable
fun SalesScreen(db: AppDatabase, branchId: String) {
    var sales by remember { mutableStateOf(emptyList<Sale>()) }
    var loading by remember { mutableStateOf(true) }
    var openSale by remember { mutableStateOf<Sale?>(null) }
    var cashierNames by remember { mutableStateOf(emptyMap<String, String>()) }

    LaunchedEffect(branchId) {
        val todayStart = todayStartIso(); val todayEnd = todayEndIso()
        val all = db.saleDao().observeRecentSales(branchId).first()
        sales = all.filter { !it.isDeleted && it.soldAt >= todayStart && it.soldAt <= todayEnd }
            .sortedByDescending { it.soldAt }
        val users = try {
            sales.map { it.userId }.distinct().mapNotNull { uid ->
                db.userDao().get(uid)?.let { uid to it.fullName }
            }.toMap()
        } catch (_: Exception) { emptyMap() }
        cashierNames = users
        loading = false
    }

    if (openSale != null) {
        SaleDetailsScreen(db, openSale!!, cashierNames[openSale!!.userId] ?: "—") { openSale = null }
        return
    }

    val totalSales = sales.sumOf { it.total }

    Column(Modifier.fillMaxSize().background(DaBlack)) {
        Surface(color = DaSurface, shape = RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryStat("UGX " + fmtN(totalSales), "Total Sales")
                SummaryStat(sales.size.toString(), "No. of Sales")
            }
        }
        Spacer(Modifier.height(4.dp))
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DaGreen)
            }
        } else if (sales.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No sales recorded today yet.", color = DaTextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.padding(horizontal = 12.dp)) {
                items(sales, key = { it.id }) { s ->
                    SaleRow(s, cashierNames[s.userId] ?: "—") { openSale = s }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DaTextPrimary)
        Text(label, fontSize = 11.sp, color = DaTextMuted)
    }
}

@Composable
private fun SaleRow(sale: Sale, cashier: String, onClick: () -> Unit) {
    Surface(color = DaSurface, shape = RoundedCornerShape(12.dp), onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(sale.receiptNo, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DaTextPrimary)
                Text("${shortTime(sale.soldAt)} · By $cashier", fontSize = 11.sp, color = DaTextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("UGX " + fmtN(sale.total), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DaGreen)
                Text(methodLabel(sale.paymentMethod), fontSize = 11.sp, color = DaTextMuted)
            }
        }
    }
}

@Composable
private fun SaleDetailsScreen(db: AppDatabase, sale: Sale, cashier: String, onBack: () -> Unit) {
    var items by remember { mutableStateOf(emptyList<SaleItem>()) }
    LaunchedEffect(sale.id) {
        items = try { db.saleItemDao().forSale(sale.id) } catch (_: Exception) { emptyList() }
    }
    Column(Modifier.fillMaxSize().background(DaBlack)) {
        Surface(color = DaSurface) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = DaTextPrimary) }
                Column(Modifier.weight(1f)) {
                    Text(sale.receiptNo, fontWeight = FontWeight.Bold, color = DaTextPrimary)
                    Text(fullDate(sale.soldAt), fontSize = 11.sp, color = DaTextMuted)
                }
                IconButton(onClick = {}) { Icon(Icons.Default.Print, null, tint = DaTextPrimary) }
            }
        }
        Surface(color = DaSurface2, modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Customer", fontSize = 12.sp, color = DaTextMuted)
                    Text("Payment Method", fontSize = 12.sp, color = DaTextMuted)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(sale.customerId?.let { "Customer" } ?: "Walk-in Customer", fontWeight = FontWeight.SemiBold, color = DaTextPrimary, fontSize = 13.sp)
                    Text(methodLabel(sale.paymentMethod), fontWeight = FontWeight.SemiBold, color = DaTextPrimary, fontSize = 13.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
            Text("ITEM", Modifier.weight(2f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DaTextMuted)
            Text("QTY", Modifier.weight(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DaTextMuted)
            Text("PRICE", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DaTextMuted)
            Text("AMOUNT", Modifier.weight(1.1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DaTextMuted)
        }
        Divider(color = DaOutline)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            items(items, key = { it.id }) { it2 ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(it2.name, Modifier.weight(2f), fontSize = 13.sp, color = DaTextPrimary)
                    Text(fmtQty(it2.qty), Modifier.weight(0.7f), fontSize = 13.sp, color = DaTextPrimary)
                    Text(fmtN(it2.unitPrice), Modifier.weight(1f), fontSize = 13.sp, color = DaTextPrimary)
                    Text(fmtN(it2.lineTotal), Modifier.weight(1.1f), fontSize = 13.sp, color = DaTextPrimary)
                }
                Divider(color = DaOutline.copy(alpha = 0.5f))
            }
        }
        Surface(color = DaSurface) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total items", fontSize = 13.sp, color = DaTextMuted)
                    Text(items.sumOf { it.qty }.let { fmtQty(it) }, fontSize = 13.sp, color = DaTextPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DaTextPrimary)
                    Text("UGX " + fmtN(sale.total), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = DaGreen)
                }
            }
        }
    }
}

private fun fmtN(v: Double) = "%,d".format(v.toLong())
private fun fmtQty(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
private fun methodLabel(m: String) = when (m) {
    "CASH" -> "Cash"; "MTN_MOMO", "AIRTEL_MONEY" -> "Mobile Money"; "CREDIT" -> "Credit"; else -> m
}
private fun shortTime(iso: String): String = try {
    val d = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(iso)
    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(d!!)
} catch (_: Exception) { iso }
private fun fullDate(iso: String): String = try {
    val d = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).parse(iso)
    java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.US).format(d!!)
} catch (_: Exception) { iso }
private fun todayStartIso(): String {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
    return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(cal.time)
}
private fun todayEndIso(): String {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59)
    cal.set(java.util.Calendar.SECOND, 59); cal.set(java.util.Calendar.MILLISECOND, 999)
    return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(cal.time)
}
