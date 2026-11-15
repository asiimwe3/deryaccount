package com.derycode.deryaccount.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.derycode.deryaccount.desktop.data.Fmt

/** DeryAccount brand palette — the same deep green the phone app uses. */
object Da {
    val Black = Color(0xFF06100B)
    val Surface = Color(0xFF0D1F15)
    val Surface2 = Color(0xFF122A1C)
    val Header = Color(0xFF0E3B22)
    val Green = Color(0xFF22C55E)
    val GreenDeep = Color(0xFF16A34A)
    val Red = Color(0xFFEF4444)
    val Blue = Color(0xFF3B82F6)
    val Amber = Color(0xFFF59E0B)
    val Purple = Color(0xFFA78BFA)
    val Text = Color(0xFFEAF3EC)
    val Muted = Color(0xFFB9C6BE)
    val Dim = Color(0xFF7E9187)
    val Outline = Color(0xFF1E3A28)
    val RowAlt = Color(0xFF0A1911)
    val Sidebar = Color(0xFF08170F)
}

private val scheme = darkColorScheme(
    primary = Da.Green,
    onPrimary = Color(0xFF03150A),
    primaryContainer = Da.Surface2,
    onPrimaryContainer = Da.Green,
    secondary = Da.Blue,
    background = Da.Black,
    onBackground = Da.Text,
    surface = Da.Surface,
    onSurface = Da.Text,
    surfaceVariant = Da.Surface2,
    onSurfaceVariant = Da.Muted,
    outline = Da.Outline,
    error = Da.Red
)

@Composable
fun DeryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

// ── Layout primitives ────────────────────────────────────────────────────────
@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    background: Color = Da.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, Da.Outline, RoundedCornerShape(14.dp))
            .padding(padding)
    ) { content() }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Da.Text)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = Da.Muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    sub: String? = null,
    accent: Color = Da.Green,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Da.Surface)
            .border(1.dp, Da.Outline, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(title.uppercase(), fontSize = 10.sp, color = Da.Muted, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) {
            Spacer(Modifier.height(4.dp))
            Text(sub, fontSize = 11.sp, color = Da.Dim, maxLines = 2)
        }
    }
}

enum class BtnKind { PRIMARY, GHOST, DANGER, SUBTLE }

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    kind: BtnKind = BtnKind.PRIMARY,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bg = when (kind) {
        BtnKind.PRIMARY -> if (enabled) Da.Green else Da.Surface2
        BtnKind.GHOST -> Color.Transparent
        BtnKind.DANGER -> if (enabled) Da.Red.copy(alpha = 0.16f) else Da.Surface2
        BtnKind.SUBTLE -> Da.Surface2
    }
    val fg = when (kind) {
        BtnKind.PRIMARY -> if (enabled) Color(0xFF03150A) else Da.Dim
        BtnKind.GHOST -> Da.Text
        BtnKind.DANGER -> Da.Red
        BtnKind.SUBTLE -> Da.Text
    }
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(if (kind == BtnKind.GHOST) Modifier.border(1.dp, Da.Outline, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = if (kind == BtnKind.PRIMARY) 11.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    minLines: Int = 1
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Da.Muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = minLines == 1,
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder, fontSize = 13.sp, color = Da.Dim) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Da.Text),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Da.Green,
                unfocusedBorderColor = Da.Outline,
                focusedContainerColor = Da.Surface2,
                unfocusedContainerColor = Da.Surface2,
                cursorColor = Da.Green
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = if (minLines == 1) 44.dp else (44 * minLines).dp)
        )
    }
}

@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Field(label, value, onChange, modifier, placeholder = "0", numeric = true)
}

@Composable
fun Drop(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Da.Muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().height(44.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Da.Surface2)
                    .border(1.dp, Da.Outline, RoundedCornerShape(10.dp))
                    .clickable { open = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected.ifBlank { "Select…" }, fontSize = 13.sp, color = Da.Text, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Default.ArrowDropDown, null, tint = Da.Muted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(o, fontSize = 13.sp) },
                        onClick = { onSelect(o); open = false },
                        trailingIcon = {
                            if (o == selected) Icon(Icons.Default.Check, null, tint = Da.Green, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBar(value: String, onChange: (String) -> Unit, hint: String = "Search", modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(42.dp)
            .clip(RoundedCornerShape(10.dp)).background(Da.Surface2)
            .border(1.dp, Da.Outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = Da.Dim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextFieldCompat(value, onChange, hint)
    }
}

@Composable
private fun RowScope.BasicTextFieldCompat(value: String, onChange: (String) -> Unit, hint: String) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = Da.Text, fontSize = 13.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Da.Green),
        modifier = Modifier.weight(1f),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, color = Da.Dim, fontSize = 13.sp)
                inner()
            }
        }
    )
}

@Composable
fun TabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Da.Surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { i, t ->
            val active = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (active) Da.Header else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t, fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) Da.Green else Da.Muted,
                    textAlign = TextAlign.Center, maxLines = 1
                )
            }
        }
    }
}

@Composable
fun Pill(text: String, color: Color = Da.Green, filled: Boolean = false) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (filled) color.copy(alpha = 0.18f) else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, color.copy(alpha = 0.5f), CircleShape))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ── Table ────────────────────────────────────────────────────────────────────
data class Cell(val text: String, val color: Color = Da.Text, val bold: Boolean = false, val align: TextAlign = TextAlign.Start)

@Composable
fun Table(
    headers: List<String>,
    weights: List<Float>,
    rows: List<List<Cell>>,
    modifier: Modifier = Modifier,
    onRowClick: ((Int) -> Unit)? = null,
    rowKey: ((Int) -> Any)? = null,
    scrollable: Boolean = true,
    maxRows: Int = Int.MAX_VALUE
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(Da.Surface2).padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            headers.forEachIndexed { i, h ->
                Text(
                    h.uppercase(), fontSize = 10.sp, color = Da.Muted, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp, modifier = Modifier.weight(weights.getOrElse(i) { 1f }),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = Da.Dim, fontSize = 13.sp)
            }
        } else if (!scrollable) {
            // Inside a page that already scrolls: plain rows, no nested scroll container.
            rows.take(maxRows).forEachIndexed { index, cells ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (index % 2 == 0) Color.Transparent else Da.RowAlt)
                        .then(if (onRowClick != null) Modifier.clickable { onRowClick(index) } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cells.forEachIndexed { i, c ->
                        Text(
                            c.text, fontSize = 12.5.sp, color = c.color,
                            fontWeight = if (c.bold) FontWeight.Bold else FontWeight.Normal,
                            textAlign = c.align,
                            modifier = Modifier.weight(weights.getOrElse(i) { 1f }),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            LazyColumn(state = rememberLazyListState()) {
                itemsIndexed(rows) { index, cells ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (index % 2 == 0) Color.Transparent else Da.RowAlt)
                            .then(if (onRowClick != null) Modifier.clickable { onRowClick(index) } else Modifier)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        cells.forEachIndexed { i, c ->
                            Text(
                                c.text, fontSize = 12.5.sp,
                                color = c.color,
                                fontWeight = if (c.bold) FontWeight.Bold else FontWeight.Normal,
                                textAlign = c.align,
                                modifier = Modifier.weight(weights.getOrElse(i) { 1f }),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────
@Composable
fun DialogBox(
    title: String,
    onClose: () -> Unit,
    width: Dp = 620.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(width).heightIn(max = 760.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Da.Surface)
                .border(1.dp, Da.Outline, RoundedCornerShape(16.dp))
        ) {
            Row(
                Modifier.fillMaxWidth().background(Da.Header).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Da.Text, modifier = Modifier.weight(1f))
                Text("✕", fontSize = 16.sp, color = Da.Muted, modifier = Modifier.clickable { onClose() }.padding(4.dp))
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) { content() }
        }
    }
}

@Composable
fun FormRow(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 12.sp, color = Da.Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = if (emphasize) Da.Green else Da.Text,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun EmptyState(message: String, action: @Composable (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = Da.Dim, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

/** Simple bar chart built from composables — no extra dependency, scales with the window. */
@Composable
fun BarChart(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    color: Color = Da.Green,
    barHeight: Dp = 150.dp,
    labelEvery: Int = 1,
    valueLabel: (Double) -> String = { Fmt.compact(it) }
) {
    val maxValue = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Row(
        modifier.fillMaxWidth().height(barHeight + 46.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { i, (label, value) ->
            val fraction = (value / maxValue).toFloat().coerceIn(0f, 1f)
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(valueLabel(value), fontSize = 9.sp, color = Da.Dim, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth(0.62f)
                        .height((barHeight.value * fraction).dp.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (fraction <= 0f) Da.Outline else color.copy(alpha = 0.85f))
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (i % labelEvery == 0) label else "", fontSize = 9.sp, color = Da.Dim,
                    maxLines = 1, overflow = TextOverflow.Clip
                )
            }
        }
    }
}

/** Inline progress bar used for budgets and targets. */
@Composable
fun MiniProgress(fraction: Float, color: Color = Da.Green, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Da.Surface2)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .clip(CircleShape).background(color)
        )
    }
}

@Composable
fun Mono(text: String, color: Color = Da.Text, size: Int = 12) {
    Text(text, color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace)
}
