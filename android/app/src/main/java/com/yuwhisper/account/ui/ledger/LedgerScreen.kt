package com.yuwhisper.account.ui.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuwhisper.account.data.LedgerRepository
import com.yuwhisper.account.ui.LedgerDayGroup
import com.yuwhisper.account.ui.LedgerItemUi
import com.yuwhisper.account.ui.centsToYuanText
import com.yuwhisper.account.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(days: List<LedgerDayGroup>, onAddClick: () -> Unit, onDeleteItem: (LedgerItemUi) -> Unit) {
    var pendingDelete by remember { mutableStateOf<LedgerItemUi?>(null) }
    val currentMonth = YearMonth.now()
    val monthDays = days.filter { YearMonth.from(it.date) == currentMonth }
    val monthItems = monthDays.flatMap { it.items }
    val monthExpense = monthDays.sumOf { it.expenseCents }
    val monthIncome = monthItems.filter { it.type == LedgerRepository.TYPE_INCOME }.sumOf { it.amountCents }

    pendingDelete?.let { item ->
        AccountDialog(
            onDismissRequest = { pendingDelete = null }, title = "删除这笔流水？",
            description = "删除后无法撤销，请确认这笔记录。",
            confirmButton = {
                TextButton(onClick = { onDeleteItem(item); pendingDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("删除流水")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("保留") } },
        ) {
            Text(item.merchant, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Text("¥${item.amountCents.centsToYuanText()}", style = MaterialTheme.typography.headlineMedium,
                fontFamily = AccountSerif, color = MaterialTheme.colorScheme.onSurface)
            Text(item.categoryName, style = MaterialTheme.typography.bodyMedium)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
        ) {
            item(key = "heading") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AccountPageHeading("流水", currentMonth.format(DateTimeFormatter.ofPattern("yyyy年M月")))
                    }
                    IconButton(onClick = onAddClick) {
                        Icon(Icons.Default.Add, contentDescription = "记一笔", modifier = Modifier.size(22.dp))
                    }
                }
            }
            item(key = "summary") {
                MonthSummary(monthExpense, monthIncome, monthItems.size)
            }
            if (days.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("从第一笔开始", style = MaterialTheme.typography.titleLarge)
                        Text("手动记一笔，或在付款后确认入账。", style = MaterialTheme.typography.bodyMedium, color = AccountMutedColor)
                        OutlinedButton(onClick = onAddClick, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("记一笔")
                        }
                    }
                }
            } else {
                days.forEach { day ->
                    item(key = "day_${day.date}") { DayHeading(day) }
                    items(day.items, key = { it.localId }) { item ->
                        Column(Modifier.animateItem()) {
                            TransactionRow(item, onDelete = { pendingDelete = item })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSummary(expenseCents: Long, incomeCents: Long, count: Int) {
    Box(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 28.dp)) {
        if (LocalDensity.current.fontScale < 1.3f) {
            AccountOrchidArt(Modifier.width(174.dp).align(Alignment.TopEnd).offset(x = 20.dp, y = (-16).dp).alpha(0.5f))
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("本月支出", style = MaterialTheme.typography.bodyMedium, color = AccountMutedColor)
            Text("¥${expenseCents.centsToYuanText()}",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 54.sp),
                color = MaterialTheme.colorScheme.onBackground)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("收入 ¥${incomeCents.centsToYuanText()}", style = MaterialTheme.typography.bodySmall,
                    color = AccountMutedColor, modifier = Modifier.weight(1f))
                Text("$count 笔", style = MaterialTheme.typography.bodySmall, color = AccountMutedColor)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
}

@Composable
private fun DayHeading(day: LedgerDayGroup) {
    val today = LocalDate.now()
    val dateLabel = when (day.date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> day.date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    }
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = AccountMutedColor, modifier = Modifier.weight(1f))
        Text("支出 ¥${day.expenseCents.centsToYuanText()}", style = MaterialTheme.typography.bodySmall, color = AccountMutedColor)
    }
}

@Composable
private fun TransactionRow(item: LedgerItemUi, onDelete: () -> Unit) {
    val isExpense = item.type == LedgerRepository.TYPE_EXPENSE
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        if (maxWidth < 280.dp || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.merchant, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除${item.merchant}的流水", tint = AccountMutedColor, modifier = Modifier.size(18.dp))
                    }
                }
                Text(item.categoryName + " · " + item.occurredAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = AccountMutedColor, style = MaterialTheme.typography.bodySmall)
                if (item.note.isNotBlank()) Text(item.note, color = AccountMutedColor, style = MaterialTheme.typography.bodySmall)
                Text("${if (isExpense) "−" else "+"}¥${item.amountCents.centsToYuanText()}",
                    color = if (isExpense) MaterialTheme.colorScheme.onSurface else AccountIncomeColor,
                    fontWeight = FontWeight.Normal, fontFamily = AccountSerif,
                    style = MaterialTheme.typography.titleLarge)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.merchant, style = MaterialTheme.typography.bodyLarge)
                    Text(item.categoryName, color = AccountMutedColor, style = MaterialTheme.typography.bodySmall)
                    if (item.note.isNotBlank()) Text(item.note, color = AccountMutedColor, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${if (isExpense) "−" else "+"}¥${item.amountCents.centsToYuanText()}",
                        color = if (isExpense) MaterialTheme.colorScheme.onSurface else AccountIncomeColor,
                        fontWeight = FontWeight.Normal, fontFamily = AccountSerif,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp))
                    Text(item.occurredAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.bodySmall, color = AccountMutedColor)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除${item.merchant}的流水", tint = AccountMutedColor, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
