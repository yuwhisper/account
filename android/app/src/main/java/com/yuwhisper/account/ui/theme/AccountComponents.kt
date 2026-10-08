package com.yuwhisper.account.ui.theme

import com.yuwhisper.account.ui.theme.AccountSerif

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yuwhisper.account.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun AccountCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(), color = Color.Transparent,
        shape = RectangleShape,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

@Composable
fun AccountSectionHeader(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp))
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AccountEmptyState(icon: ImageVector, title: String, description: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AccountOrchidArt(Modifier.width(220.dp))
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun AccountOrchidArt(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(if (LocalAccountDarkTheme.current) R.drawable.orchid_night else R.drawable.orchid_day),
        contentDescription = null,
        modifier = modifier.aspectRatio(1.5f),
    )
}

@Composable
fun AccountSeal(modifier: Modifier = Modifier) {
    val color = if (LocalAccountDarkTheme.current) Color(0xFFC99985) else Color(0xFFA54B3B)
    Box(modifier.size(width = 28.dp, height = 32.dp).border(0.7.dp, color).padding(3.dp).border(0.5.dp, color.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
        Text("兰", fontFamily = AccountSerif, fontSize = 18.sp, color = color)
    }
}

@Composable
fun AccountPageHeading(title: String, subtitle: String? = null, showSeal: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AccountMutedColor) }
            Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 46.sp))
        }
        if (showSeal) AccountSeal()
    }
}

@Composable
fun AccountAppearanceSelector() {
    val mode by rememberAccountAppearance()
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("system" to "跟随系统", "light" to "白天", "dark" to "黑夜").forEach { (value, label) ->
            val isSelected = mode == value
            TextButton(
                onClick = { setAccountAppearance(context, value) },
                modifier = Modifier.weight(1f).semantics { selected = isSelected },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else AccountMutedColor,
                ),
            ) { Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
fun AccountDialog(
    onDismissRequest: () -> Unit,
    title: String,
    description: String? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                content()
            }
        },
        confirmButton = confirmButton, dismissButton = dismissButton,
        shape = MaterialTheme.shapes.extraLarge, containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp, titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
