package com.derycode.deryaccount.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.ui.theme.*
import com.derycode.deryaccount.util.Share

/** Reusable scrollable page for long documents (docs, terms, privacy). */
@Composable
private fun DocPage(title: String, sections: List<Pair<String, String>>) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        sections.forEach { (heading, body) ->
            Text(heading, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DaGreen)
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
        }
        Text("DeryAccount · by Derycode · support: info@derycode.com · WhatsApp +256 762 306 675",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DocumentationScreen() = DocPage("Documentation — User Guide", listOf(
    "1. Getting started" to
        "After installing, create your owner account, fill in the Business Profile " +
        "(name, tagline, phone, location, TIN) and add your shop logo — the logo is " +
        "printed on every receipt, invoice and report. Then open Stock → Quick Stock " +
        "Setup: pick your business category, tick the items you sell, set the selling " +
        "price, cost and opening stock. Opening stock is recorded as capital (goods you " +
        "already own), not as a purchase.",
    "2. Selling (POS)" to
        "On the Sell screen tap a product to add it to the cart; use the +/− steppers to " +
        "change quantity. Choose CASH, MOBILE MONEY or CREDIT (credit requires a customer). " +
        "Add a discount if needed. Checkout prints the receipt, saves its PDF, and posts " +
        "the sale to the books of account automatically. Hold Sale parks a cart for later.",
    "3. Sales & customers" to
        "The Sales screen lists today's sales — tap any sale to see its items, reprint the " +
        "receipt, share it on WhatsApp, or issue an invoice. The Customers screen tracks " +
        "credit balances and repayments; every repayment posts Dr Cash, Cr Debtors.",
    "4. Stock" to
        "Adjust stock, edit products, write off damage, and print a stock report. Stock you " +
        "buy during the month is recorded as a Purchase. The Stock account in the books " +
        "always equals the physical stock list at cost.",
    "5. Books of account" to
        "Cash Book, Petty Cash Book, Bank/MoMo Book, Ledger for every account, Trial " +
        "Balance, Income Statement (NET SALES = Sales − Returns − Discounts; COGS = Opening " +
        "Stock + Purchases − Purchase Returns − Closing Stock; GROSS PROFIT; NET PROFIT), " +
        "and Balance Sheet (Assets = Liabilities + Equity). Every entry is double-entry: " +
        "debits always equal credits — the app refuses unbalanced entries.",
    "6. Expenses & shift" to
        "Record expenses (Dr Expense, Cr Cash). The Shift screen tracks expected cash in " +
        "the box (opening float + cash sales − shift expenses) and computes the variance at " +
        "close — your daily Z-report.",
    "7. Reports & analytics" to
        "Today's and monthly totals split by cash, mobile money and credit, best sellers, " +
        "dead stock, stock valuation, profit by product/category, customer aging and more.",
    "8. Backup — do this daily" to
        "More → Backup: prepare the backup folder (all receipts, reports, books, invoices " +
        "and the full database in one ZIP) and share it to Google Drive, TeraBox, email or " +
        "WhatsApp. Your data lives only on this phone — a daily backup is your protection.",
    "9. Subscription" to
        "DeryAccount is free for the first 3 days after installation with every feature " +
        "unlocked. After that, activate a plan (Starter remains free; Business, " +
        "Professional and Multi-Branch add more) via activation code or in-app payment.",
    "10. Printing & sharing" to
        "Receipts and invoices print on any Android printer (Bluetooth ESC/POS or system " +
        "print dialog), save as PDF, and share on WhatsApp. Books, stock reports and " +
        "statements also print or save as PDF."
))

@Composable
fun TermsScreen() = DocPage("Terms & Conditions", listOf(
    "1. Licence" to
        "DeryAccount is licensed, not sold. Derycode grants you a non-exclusive licence to " +
        "use the app for your lawful business on the devices you control. The app is " +
        "offline-first: no internet connection is required to run any accounting function.",
    "2. Free trial & plans" to
        "New installations receive a 3-day free trial with all features unlocked. After the " +
        "trial, the Starter plan remains free; paid plans (Business, Professional, " +
        "Multi-Branch) are activated by activation code or in-app mobile-money payment. " +
        "Activation codes are valid for the plan and period stated at purchase and are " +
        "non-transferable.",
    "3. Your data" to
        "All records you enter are stored locally on your device. You are responsible for " +
        "your data and for making regular backups (More → Backup). Derycode is not liable " +
        "for data loss caused by device damage, loss, theft, or failure to back up.",
    "4. Acceptable use" to
        "You agree to use DeryAccount for lawful business records only, not to falsify " +
        "accounts, and not to reverse-engineer, resell or redistribute the app or its " +
        "activation codes.",
    "5. Warranty & liability" to
        "The app is provided as-is. While Derycode applies professional care to the " +
        "accounting logic (strict double-entry, balanced books), you remain responsible " +
        "for reviewing your books and for statutory filings (taxes, URA returns). To the " +
        "maximum extent permitted by law, Derycode's liability is limited to the amount " +
        "you paid for your current plan.",
    "6. Changes" to
        "These terms may be updated with app releases. Continued use after an update means " +
        "you accept the revised terms. Questions: info@derycode.com.",
    "Effective date" to "1 September 2026. Version 1.0."
))

@Composable
fun PrivacyScreen() = DocPage("Privacy Policy", listOf(
    "Your data stays on your phone" to
        "DeryAccount is a local, offline application. All your business records — sales, " +
        "stock, customers, expenses and books of account — are stored in the app's private " +
        "database on your device only. DeryAccount does not send your accounting data to " +
        "any server, and there is no Derycode cloud holding your books.",
    "What we never do" to
        "We do not sell, share, or monetise your data. We do not collect your customers' " +
        "data. We do not track your sales. There are no analytics and no advertising " +
        "identifiers inside the accounting functions.",
    "Permissions the app uses" to
        "Storage: to save receipts, reports and books as files in the visible DeryAccount " +
        "folder, and to attach backups when YOU choose to share them. Camera: only for " +
        "scanning product barcodes. Bluetooth: only for connecting your receipt printer. " +
        "Install packages: only for installing app updates you approve. Phone state " +
        "(optional): not required — every accounting feature works offline.",
    "When you share" to
        "If you tap Share (backup, receipt, invoice), Android opens the app you choose " +
        "(WhatsApp, Gmail, Drive, TeraBox). What happens in those apps is governed by their " +
        "own policies — DeryAccount only attaches the file you selected.",
    "Subscription payments" to
        "If you pay for a plan, the payment is processed by the mobile-money provider; " +
        "DeryAccount stores only the activation status on your device.",
    "Contact" to
        "Any privacy question: info@derycode.com or WhatsApp +256 762 306 675. " +
        "Effective 1 September 2026, Version 1.0."
))

/** Feedback — one tap opens a WhatsApp chat with Derycode support. */
@Composable
fun FeedbackScreen() {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(60.dp))
        Icon(Icons.Default.Chat, null, tint = DaGreen, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("Give Feedback", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("Tell us what you love, what you'd change, or report a problem — " +
            "we reply fast on WhatsApp.", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { Share.feedbackWhatsApp(context) },
            modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Default.Chat, null); Text("  Chat on WhatsApp")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { Share.feedbackWhatsApp(context) },
            modifier = Modifier.fillMaxWidth()) {
            Text("+256 762 306 675 — Derycode Support")
        }
    }
}
