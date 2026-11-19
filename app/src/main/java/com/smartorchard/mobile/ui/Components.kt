package com.smartorchard.mobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartorchard.mobile.R
import com.smartorchard.mobile.data.TreeStatus
import com.smartorchard.mobile.ui.theme.*

@Composable
fun OrchardIcon(
    @DrawableRes id: Int,
    modifier: Modifier,
    tint: Color = LocalContentColor.current,
    description: String? = null
) {
    Icon(
        painter = painterResource(id),
        contentDescription = description,
        modifier = modifier,
        tint = tint
    )
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge
    )
    if (subtitle != null) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = OrchardMuted
        )
    }
}

@Composable
fun BackLink(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp)
    ) {
        OrchardIcon(
            id = R.drawable.ic_arrow_left,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(text)
    }
}

@Composable
fun OrchardCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, OrchardLine)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            content = content
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        if (icon != null) {
            OrchardIcon(
                id = icon,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, OrchardLine),
        contentPadding = PaddingValues(10.dp)
    ) {
        if (icon != null) {
            OrchardIcon(
                id = icon,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    readOnly: Boolean = false,
    lines: Int = 1,
    placeholder: String = ""
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            singleLine = lines == 1,
            minLines = lines,
            shape = RoundedCornerShape(13.dp),
            placeholder = if (placeholder.isNotEmpty()) {
                {
                    Text(
                        text = placeholder,
                        color = OrchardMuted
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = OrchardLine,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            ),
            textStyle = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    value: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, OrchardLine),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
            ) {
                Text(
                    text = options.find { it.first == value }?.second
                        ?: stringResource(R.string.dropdown_placeholder),
                    modifier = Modifier.weight(1f),
                    color = OrchardInk
                )
                OrchardIcon(
                    id = R.drawable.ic_chevron_down,
                    modifier = Modifier.size(17.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onSelect(key)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: TreeStatus) {
    val (bg, fg) = statusColors(status)
    val iconRes = when (status) {
        TreeStatus.WARNING -> R.drawable.ic_triangle_alert
        TreeStatus.DEAD -> R.drawable.ic_x
        TreeStatus.UNKNOWN -> R.drawable.ic_circle_dashed
        TreeStatus.HEALTHY -> R.drawable.ic_check
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            OrchardIcon(
                id = iconRes,
                modifier = Modifier.size(13.dp),
                tint = fg
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

fun statusColors(status: TreeStatus): Pair<Color, Color> = when (status) {
    TreeStatus.HEALTHY -> GoodBackground to Color(0xFF3E704A)
    TreeStatus.WARNING -> WarningBackground to Color(0xFF966617)
    TreeStatus.DEAD -> DeadBackground to Color(0xFFB05349)
    TreeStatus.UNKNOWN -> Color(0xFFF0F2EB) to OrchardMuted
}

@Composable
fun Detail(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = OrchardMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End
        )
    }
    HorizontalDivider(color = Color(0xFFF0F2EB))
}

@Composable
fun DemoBadge(text: String = stringResource(R.string.badge_demo)) {
    Surface(
        color = Color(0xFFEDF1E8),
        shape = RoundedCornerShape(7.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = OrchardMuted
        )
    }
}

@Composable
fun InfoBox(
    text: String,
    @DrawableRes icon: Int = R.drawable.ic_info
) {
    Surface(
        color = Color(0xFFECF1E5),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OrchardIcon(
                id = icon,
                tint = OrchardGreen,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ErrorBox(text: String?) {
    if (text != null) {
        Surface(
            color = DeadBackground,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                color = Color(0xFF9A4D43),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ScrollableScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        content()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun LazyScreen(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Preview(showBackground = true)
@Composable
private fun ComponentsPreview() {
    SmartOrchardTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenTitle(
                title = PreviewSamples.garden.name,
                subtitle = "${PreviewSamples.crop.name} · ${PreviewSamples.crop.variety}"
            )
            OrchardCard {
                SectionLabel("Состояние деревьев")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(TreeStatus.HEALTHY)
                    StatusBadge(TreeStatus.WARNING)
                    StatusBadge(TreeStatus.DEAD)
                }
                Detail("Площадь", "${PreviewSamples.garden.areaHa} га")
                DemoBadge()
            }
            PrimaryButton(
                text = "Начать осмотр",
                icon = R.drawable.ic_camera,
                onClick = {}
            )
            SecondaryButton(
                text = "Печать этикетки",
                onClick = {}
            )
            InfoBox(text = "Данные сохраняются локально на устройстве.")
            ErrorBox(text = "Проверьте корректность введенных данных.")
        }
    }
}
