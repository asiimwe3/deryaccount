package com.derycode.deryaccount.desktop.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.derycode.deryaccount.desktop.data.*
import com.derycode.deryaccount.desktop.ui.*
import java.io.File

@Composable
fun ShiftScreen(app: AppState) {
    val repo = app.repo
    var shift by remember { mutableStateOf(repo.openShiftNow()) }
    var opening by remember { mutableStateOf("50000") }
    var counted by remember { mutableStateOf("") }
    var cashAmount by remember { mutableStateOf("") }
    var cashNote by remember { mutableStateOf("") }
    var cashType by remember { mutableStateOf("CASH_IN") }

    LaunchedEffect(app.tick) { shift = repo.openShiftNow() }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        ScreenHeader("Shift / Z-report", "The cash drawer, counted and explained") {
            if (shift != null) {
                Btn("Cash movement", {
                    try {
                        val amt = cashAmount.toDoubleOrNull() ?: 0.0
                        if (cashType == "CASH_IN") repo.cashIn(amt, cashNote) else repo.cashOut(amt, cashNote)
                        cashAmount = ""; cashNote = ""
                        app.refresh(); app.say("Cash movement recorded")
                    } catch (e: Exception) { app.say(e.message ?: "Could not record") }
                }, Icons.Default.SwapHoriz, BtnKind.GHOST)
            }
        }

        if (shift == null) {
            CardBox(Modifier.width(520.dp)) {
                Text("No shift is open", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                Text("Open a shift at the start of the day with the cash already in the drawer. Every cash sale during the shift counts towards the expected total.",
                    fontSize = 11.5.sp, color = Da.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                MoneyField("Opening float", opening, { opening = it.filter { c -> c.isDigit() || c == '.' } })
                Spacer(Modifier.height(12.dp))
                Btn("Open shift", {
                    repo.openShift(opening.toDoubleOrNull() ?: 0.0, app.user?.str("id") ?: "u-owner")
                    app.refresh(); app.say("Shift opened")
                }, Icons.Default.PlayArrow, BtnKind.PRIMARY)
            }
        } else {
            val s = shift!!
            val z = remember(app.tick) { repo.zReport(s) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KpiCard("Opening float", Fmt.money(s.dbl("opening_cash")), Fmt.dateTime(s.str("opened_at")), Da.Blue, Modifier.weight(1f))
                        KpiCard("Cash sales", Fmt.money(z["CASH"] ?: 0.0), "${z["_count"]?.toInt() ?: 0} receipts total", Da.Green, Modifier.weight(1f))
                        KpiCard("Expected in drawer", Fmt.money(z["_expectedCash"] ?: 0.0), "float + cash in − out", Da.Amber, Modifier.weight(1f))
                    }
                    CardBox(Modifier.fillMaxWidth()) {
                        Text("Z-report", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(10.dp))
                        StatementLine("Cash sales", Fmt.money(z["CASH"] ?: 0.0))
                        StatementLine("MTN MoMo", Fmt.money(z["MTN_MOMO"] ?: 0.0))
                        StatementLine("Airtel Money", Fmt.money(z["AIRTEL_MONEY"] ?: 0.0))
                        StatementLine("Credit sales", Fmt.money(z["CREDIT"] ?: 0.0))
                        StatementLine("Total takings", Fmt.money(z["_total"] ?: 0.0), bold = true)
                        Spacer(Modifier.height(6.dp))
                        StatementLine("Expenses paid", Fmt.money(z["_expenses"] ?: 0.0))
                        StatementLine("Refunds", Fmt.money(z["_returns"] ?: 0.0))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("Save Z-report PDF", {
                                val dir = File(System.getProperty("user.home"), "Documents/DeryAccount/reports")
                                val rows = listOf(
                                    listOf("Cash sales", Fmt.money(z["CASH"] ?: 0.0)),
                                    listOf("MTN MoMo", Fmt.money(z["MTN_MOMO"] ?: 0.0)),
                                    listOf("Airtel Money", Fmt.money(z["AIRTEL_MONEY"] ?: 0.0)),
                                    listOf("Credit", Fmt.money(z["CREDIT"] ?: 0.0)),
                                    listOf("Total takings", Fmt.money(z["_total"] ?: 0.0)),
                                    listOf("Expenses", Fmt.money(z["_expenses"] ?: 0.0)),
                                    listOf("Refunds", Fmt.money(z["_returns"] ?: 0.0)),
                                    listOf("Opening float", Fmt.money(z["_openingFloat"] ?: 0.0)),
                                    listOf("Expected cash", Fmt.money(z["_expectedCash"] ?: 0.0))
                                )
                                Pdf.statement("Z-REPORT", "Shift opened ${Fmt.dateTime(s.str("opened_at"))}",
                                    app.repo.businessName(), rows, listOf("Item", "Amount"),
                                    listOf(2.4f, 1.4f), target = File(dir, "Z-report-${s.str("id").take(8)}.pdf"))
                                app.say("Z-report saved")
                            }, Icons.Default.PictureAsPdf, BtnKind.GHOST)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CardBox(Modifier.fillMaxWidth()) {
                        Text("Cash in / out", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Box(Modifier.weight(1f)) { Drop("Type", listOf("CASH_IN", "CASH_OUT"), cashType, { cashType = it }) }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) { MoneyField("Amount", cashAmount, { cashAmount = it.filter { c -> c.isDigit() || c == '.' } }) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Field("Note", cashNote, { cashNote = it })
                    }
                    CardBox(Modifier.fillMaxWidth()) {
                        Text("Close the shift", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Da.Text)
                        Spacer(Modifier.height(8.dp))
                        MoneyField("Cash counted in drawer", counted, { counted = it.filter { c -> c.isDigit() || c == '.' } })
                        val variance = (counted.toDoubleOrNull() ?: 0.0) - (z["_expectedCash"] ?: 0.0)
                        if (counted.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (kotlin.math.abs(variance) < 1) "The drawer balances exactly."
                                else if (variance < 0) "Short by ${Fmt.money(-variance)}" else "Over by ${Fmt.money(variance)}",
                                fontSize = 11.5.sp, color = if (kotlin.math.abs(variance) < 1) Da.Green else Da.Red
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Btn("Close shift", {
                            repo.closeShift(s.str("id"), counted.toDoubleOrNull() ?: 0.0)
                            app.refresh(); app.say("Shift closed"); counted = ""
                        }, Icons.Default.Stop, BtnKind.DANGER)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        CardBox(Modifier.weight(1f), padding = 0.dp) {
            Table(
                listOf("Opened", "Closed", "Float", "Expected", "Counted", "Variance"),
                listOf(1.6f, 1.6f, 1f, 1f, 1f, 1f),
                repo.shifts().map {
                    val v = it.dbl("variance")
                    listOf(
                        Cell(Fmt.dateTime(it.str("opened_at")), Da.Muted),
                        Cell(if (it.str("closed_at").isBlank()) "open" else Fmt.dateTime(it.str("closed_at")), Da.Dim),
                        Cell(Fmt.money(it.dbl("opening_cash")), Da.Muted, align = TextAlign.End),
                        Cell(Fmt.money(it.dbl("expected_cash")), Da.Text, align = TextAlign.End),
                        Cell(Fmt.money(it.dbl("closing_cash")), Da.Text, align = TextAlign.End),
                        Cell(
                            if (it.str("closed_at").isBlank()) "—" else Fmt.moneySigned(v),
                            if (kotlin.math.abs(v) < 1) Da.Green else Da.Red, align = TextAlign.End
                        )
                    )
                }
            )
        }
    }
}
