package com.derycode.deryaccount.util

import android.content.Context
import com.derycode.deryaccount.data.local.AppDatabase
import com.derycode.deryaccount.data.local.entity.Sale
import com.derycode.deryaccount.data.local.entity.SaleItem
import java.io.File

/**
 * Reprint — rebuild any past receipt exactly as it was printed the first time.
 * Loads the Sale + its SaleItems from the local database, rebuilds the
 * business header (profile name, tagline, TIN, footer...), and produces a
 * fresh PDF via the same PdfExport engine used at checkout — so the reprint
 * is line-for-line identical to the original. 100% offline.
 */
object Reprint {

    data class Loaded(val sale: Sale, val items: List<SaleItem>)

    suspend fun load(db: AppDatabase, saleId: String): Loaded? {
        val sale = db.saleDao().get(saleId) ?: return null
        val items = try { db.saleItemDao().forSale(saleId) } catch (_: Exception) { emptyList() }
        return Loaded(sale, items)
    }

    /** Business header rebuilt the same way checkout builds it. */
    private suspend fun header(context: Context, db: AppDatabase, branchId: String): EscPosPrinter.BizHeader {
        val profile = try {
            SessionManager(context).businessProfileNow()
        } catch (_: Exception) { null }
        val branch = try {
            runCatching { db.branchDao().get(branchId) }.getOrNull()
        } catch (_: Exception) { null }
        return EscPosPrinter.BizHeader(
            name = profile?.name?.ifBlank { null } ?: branch?.name ?: "My Shop",
            tagline = profile?.tagline ?: "",
            phone = profile?.phone ?: "",
            location = profile?.location ?: branch?.location ?: "",
            tin = profile?.tin ?: "",
            footer = profile?.footer ?: "Thank you! Karibu tena!")
    }

    /** Regenerate the receipt PDF and open the system print dialog
     *  (print to any connected printer, or save/share the PDF). */
    suspend fun printPdf(context: Context, db: AppDatabase, branchId: String, saleId: String): File? {
        val loaded = load(db, saleId) ?: return null
        val h = header(context, db, branchId)
        return try {
            val f = PdfExport.receiptPdf(
                context, h.name, loaded.sale.receiptNo,
                loaded.items.map { Triple(it.name, it.qty, it.lineTotal) },
                loaded.sale.total, loaded.sale.amountPaid,
                loaded.sale.changeGiven, loaded.sale.paymentMethod, h)
            PdfExport.printPdf(context, f, "Receipt ${loaded.sale.receiptNo}")
            f
        } catch (_: Exception) { null }
    }
}
