package com.yuwhisper.account.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuwhisper.account.data.LedgerRepository
import com.yuwhisper.account.ui.LedgerDayGroup
import com.yuwhisper.account.ui.LedgerItemUi
import com.yuwhisper.account.ui.centsToYuanText
import com.yuwhisper.account.ui.theme.AccountExpenseColor
import com.yuwhisper.account.ui.theme.AccountIncomeColor
import com.yuwhisper.account.ui.theme.AccountMutedColor
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    days: List<LedgerDayGroup>,
    onAddClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u6d41\u6c34") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "\u8bb0\u4e00\u7b14")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (days.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("\u8fd8\u6ca1\u6709\u6d41\u6c34", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "\u70b9\u53f3\u4e0b\u89d2\u300c\u8bb0\u4e00\u7b14\u300d\u5f00\u59cb\u8bb0\u5f55",
                    color = AccountMutedColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(days, key = { it.date.toString() }) { day ->
                    DaySection(day)
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun DaySection(day: LedgerDayGroup) {
    val dateLabel = day.date.format(DateTimeFormatter.ofPattern("M\u6708d\u65e5 EEEE"))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(dateLabel, fontWeight = FontWeight.SemiBold)
            Text(
                "\u652f\u51fa \u00a5${day.expenseCents.centsToYuanText()}",
                color = AccountMutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        day.items.forEach { item ->
            TransactionRow(item)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun TransactionRow(item: LedgerItemUi) {
    val isExpense = item.type == LedgerRepository.TYPE_EXPENSE
    val amountColor = if (isExpense) AccountExpenseColor else AccountIncomeColor
    val sign = if (isExpense) "-" else "+"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.merchant, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(item.categoryName)
                    if (item.note.isNotBlank()) append(" \u00b7 ").append(item.note)
                },
                color = AccountMutedColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "$sign\u00a5${item.amountCents.centsToYuanText()}",
            color = amountColor,
            fontWeight = FontWeight.Medium,
        )
    }
}
