package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.ui.*

private data class DocTopic(val title: String, val sections: List<Pair<String, String>>)

private val topics = listOf(
    DocTopic("Getting started", listOf(
        "Your first ten minutes" to
            "1. Open Settings → Business profile and put in the shop name, phone, address and TIN. Those details print on every receipt.\n" +
            "2. Go to Stock → Products and add what you sell, with the buying price and the selling price.\n" +
            "3. Open Shift & cash and open a shift with the cash you start the day with.\n" +
            "4. Sell. Everything else — the books, the reports, the VAT — fills itself in from your sales.",
        "Where your data lives" to
            "Everything is one database file inside your Documents folder, in DeryAccount/deryaccount.db. It works with no internet. " +
            "Copy that file to a flash disk or to Google Drive every evening: that is your backup. Data & backup has a one-click button for it."
    )),
    DocTopic("Selling", listOf(
        "Taking a sale" to
            "Search for a product or type its barcode, then click it to add it to the bill. Change the quantity with + and -. " +
            "Pick the customer when selling on credit, choose Cash, MTN, Airtel, Bank or Credit, and type how much they handed you. " +
            "The change is calculated for you.",
        "Wholesale prices and discounts" to
            "Switch the sale type to Wholesale and each product automatically uses its wholesale price once the quantity reaches its wholesale minimum. " +
            "A discount on the whole bill goes to its own account, so your profit report still tells the truth.",
        "Holding a bill" to
            "If a customer goes back for more items, press Hold bill. The cart is parked and the next customer can be served. " +
            "Open Held bills to bring it back.",
        "Receipts" to
            "After every sale you can save the receipt as a PDF. Reprint any past receipt from the Sales register — open the sale and choose Save PDF."
    )),
    DocTopic("Stock", listOf(
        "Adding stock" to
            "Stock arrives through Purchases (which also records what you owe the supplier) or through Stock → Add stock for a quick delivery. " +
            "Both post the correct entries in the books.",
        "Counts, write-offs and transfers" to
            "A stock count compares what the shelf says with what the system says and posts the difference to Stock losses. " +
            "Write-offs remove damaged or expired goods. Transfers move stock to another branch.",
        "Batches and serials" to
            "For medicines, cosmetics and drinks, record the batch number and expiry so you can sell the oldest first. " +
            "For electronics and machines, record serial numbers and mark them sold when they leave the shop."
    )),
    DocTopic("Customers and suppliers", listOf(
        "Credit customers" to
            "Add the customer with a credit limit. A credit sale increases their balance, a payment reduces it. " +
            "The aging report shows who is overdue so you know who to call.",
        "Suppliers" to
            "Purchases on credit increase what you owe. Record supplier payments from the Suppliers screen. " +
            "Returning goods to a supplier can refund cash or reduce what you owe."
    )),
    DocTopic("The books", listOf(
        "Double entry, done for you" to
            "Every sale, purchase, expense and payment posts to the journal automatically, debits equal credits, always. " +
            "You only write a journal entry by hand for things the system cannot guess, such as owner capital or a bank charge.",
        "Trial balance, profit and loss, balance sheet" to
            "Books of account shows all four, for any date range. Profit and loss reads: sales, less returns, less discounts, less cost of goods sold, equals gross profit, " +
            "less expenses, equals net profit. The balance sheet always balances because it is built from the same journal.",
        "VAT" to
            "Set the tax rate on a product and the VAT report collects output tax for the month. Remitting it to URA posts the payment so the liability clears."
    )),
    DocTopic("Reports", listOf(
        "What to look at every week" to
            "Best sellers tells you what to reorder. Dead stock tells you what is tying up your money. " +
            "Profit by product and by category tells you which lines actually make money. Stock value tells you what your shelves are worth.",
        "Budget against actual" to
            "Set a monthly sales budget and expense budgets in Assets & staff → Budgets. Reports then show how far ahead or behind you are."
    )),
    DocTopic("Working safely", listOf(
        "Backups" to
            "Data & backup → Back up now writes a dated copy. Do it every evening before closing. " +
            "If a computer dies, install DeryAccount on the new one and use Restore to load that file.",
        "Users and PINs" to
            "Give each cashier their own user with the CASHIER role. They can sell, add stock and close their shift, but cannot change prices, " +
            "open the books or see settings. Change the default 1234 PIN on day one.",
        "Shifts" to
            "Open a shift with the opening cash, record cash in and cash out during the day, then close with a Z-report. " +
            "The variance line shows whether the drawer matches the day's sales."
    )),
    DocTopic("Support", listOf(
        "Getting help" to
            "DeryCode Technologies, Kyenjojo, Uganda.\nPhone and WhatsApp: 0762 306 675\nEmail: support@derycode.online\nWebsite: derycode.online\n\n" +
            "When reporting a problem, say what you were doing and what you expected, and attach the database backup if the numbers look wrong."
    ))
)

@Composable
fun DocsScreen(app: AppState) {
    var selected by remember { mutableStateOf(0) }
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.width(230.dp)) {
            Text("Documentation", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            Text("Plain answers, no jargon.", fontSize = 11.5.sp, color = Da.Muted)
            Spacer(Modifier.height(14.dp))
            topics.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (i == selected) Da.Surface2 else Da.Surface)
                        .clickable { selected = i }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(t.title, fontSize = 12.5.sp, color = if (i == selected) Da.Green else Da.Text,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Version ${com.derycode.deryaccount.desktop.Version.name}", fontSize = 10.sp, color = Da.Dim)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CardBox(Modifier.fillMaxWidth()) {
                Text(topics[selected].title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Spacer(Modifier.height(14.dp))
                topics[selected].sections.forEach { (heading, body) ->
                    Text(heading, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Da.Green)
                    Spacer(Modifier.height(5.dp))
                    Text(body, fontSize = 12.5.sp, color = Da.Muted, lineHeight = 20.sp)
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
fun LegalScreen(app: AppState) {
    var which by remember { mutableStateOf(0) }
    val tabs = listOf("Terms of use", "Privacy", "Feedback")
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Terms, privacy and feedback", "DeryAccount is a DeryCode Technologies product")
        TabBar(tabs, which, { which = it })
        Spacer(Modifier.height(14.dp))
        CardBox(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (which) {
                0 -> Text(
                    "TERMS OF USE\n\n" +
                        "1. Licence. DeryCode Technologies grants this business a licence to use DeryAccount on its own computers for its own books. " +
                        "You may install it on the number of computers covered by your plan.\n\n" +
                        "2. Your data is yours. Records you enter belong to your business. DeryCode has no access to them and does not receive copies. " +
                        "You are responsible for keeping backups.\n\n" +
                        "3. Support. Telephone and WhatsApp support is available on 0762 306 675 during business hours. " +
                        "Backup issues and data-loss recovery are attempted where a backup exists.\n\n" +
                        "4. No warranty of fitness for a particular accounting standard. The books follow double-entry principles used by Ugandan bookkeepers; " +
                        "your accountant remains responsible for statutory filings.\n\n" +
                        "5. Law. These terms are governed by the laws of the Republic of Uganda.",
                    fontSize = 12.5.sp, color = Da.Muted, lineHeight = 21.sp
                )
                1 -> Text(
                    "PRIVACY\n\n" +
                        "DeryAccount for Windows is offline-first. Your sales, stock, customers, suppliers and books are stored in a single database file on this computer. " +
                        "Nothing is uploaded to DeryCode unless you yourself export a file and send it.\n\n" +
                        "There is no tracking, no analytics and no advertising inside the app. The app does not connect to the internet on its own.\n\n" +
                        "Because everything is local, deleting the app or losing the computer loses the data unless you kept a backup. " +
                        "Use Data & backup → Back up now regularly.\n\n" +
                        "If you send us a backup for support, we use it only to fix the problem you reported and delete it afterwards.",
                    fontSize = 12.5.sp, color = Da.Muted, lineHeight = 21.sp
                )
                else -> Column {
                    Text("Tell us what is working and what is not", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Da.Text)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "WhatsApp or call 0762 306 675, or email support@derycode.online. " +
                            "Screenshot the screen if you can, and say which version you are on (Settings → About).",
                        fontSize = 12.5.sp, color = Da.Muted, lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Developed by DeryCode Technologies, Kyenjojo, Uganda.", fontSize = 11.sp, color = Da.Dim)
                }
            }
        }
    }
}
