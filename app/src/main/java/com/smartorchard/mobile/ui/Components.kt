package com.smartorchard.mobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.TreeStatus
import com.smartorchard.mobile.ui.theme.*

@Composable fun OrchardIcon(@DrawableRes id: Int, modifier: Modifier = Modifier.size(20.dp), tint: Color = LocalContentColor.current, description: String? = null) = Icon(painterResource(id), description, modifier, tint)
@Composable fun ScreenTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = OrchardMuted)
}
@Composable fun BackLink(text: String, onClick: () -> Unit) {
    TextButton(onClick, contentPadding = PaddingValues(0.dp)) { OrchardIcon(R.drawable.ic_arrow_left, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text(text) }
}
@Composable fun OrchardCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, OrchardLine)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}
@Composable fun PrimaryButton(text: String, @DrawableRes icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(12.dp)) {
        if (icon != null) { OrchardIcon(icon, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }; Text(text)
    }
}
@Composable fun SecondaryButton(text: String, @DrawableRes icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 46.dp), enabled, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, OrchardLine), contentPadding = PaddingValues(10.dp)) {
        if (icon != null) { OrchardIcon(icon, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)) }; Text(text)
    }
}
@Composable fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, numeric: Boolean = false, readOnly: Boolean = false, lines: Int = 1, placeholder: String = "") {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), readOnly = readOnly, singleLine = lines == 1,
            minLines = lines, shape = RoundedCornerShape(13.dp), placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder, color = OrchardMuted) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = OrchardLine, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
            textStyle = MaterialTheme.typography.bodyLarge)
    }
}
@Composable fun <T> DropdownField(label: String, value: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, OrchardLine), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
                Text(options.find { it.first == value }?.second ?: "Выберите", Modifier.weight(1f), color = OrchardInk)
                OrchardIcon(R.drawable.ic_chevron_down, Modifier.size(17.dp))
            }
            DropdownMenu(expanded, { expanded = false }) { options.forEach { (key, text) -> DropdownMenuItem({ Text(text) }, { expanded = false; onSelect(key) }) } }
        }
    }
}
@Composable fun StatusBadge(status: TreeStatus) {
    val (bg, fg) = statusColors(status)
    Surface(color = bg, shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OrchardIcon(when (status) { TreeStatus.WARNING -> R.drawable.ic_triangle_alert; TreeStatus.DEAD -> R.drawable.ic_x; TreeStatus.UNKNOWN -> R.drawable.ic_circle_dashed; else -> R.drawable.ic_check }, Modifier.size(13.dp), fg)
            Text(status.label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}
fun statusColors(status: TreeStatus): Pair<Color, Color> = when (status) {
    TreeStatus.HEALTHY -> GoodBackground to Color(0xFF3E704A)
    TreeStatus.WARNING -> WarningBackground to Color(0xFF966617)
    TreeStatus.DEAD -> DeadBackground to Color(0xFFB05349)
    TreeStatus.UNKNOWN -> Color(0xFFF0F2EB) to OrchardMuted
}
@Composable fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = OrchardMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    HorizontalDivider(color = Color(0xFFF0F2EB))
}
@Composable fun DemoBadge(text: String = "Демо") { Surface(color = Color(0xFFEDF1E8), shape = RoundedCornerShape(7.dp)) { Text(text, Modifier.padding(8.dp, 4.dp), style = MaterialTheme.typography.labelSmall, color = OrchardMuted) } }
@Composable fun InfoBox(text: String, @DrawableRes icon: Int = R.drawable.ic_info) {
    Surface(color = Color(0xFFECF1E5), shape = RoundedCornerShape(14.dp)) { Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OrchardIcon(icon, tint = OrchardGreen); Text(text, style = MaterialTheme.typography.bodyMedium) } }
}
@Composable fun ErrorBox(text: String?) { if (text != null) Surface(color = DeadBackground, shape = RoundedCornerShape(10.dp)) { Text(text, Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFF9A4D43), style = MaterialTheme.typography.bodyMedium) } }
@Composable fun SectionLabel(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
