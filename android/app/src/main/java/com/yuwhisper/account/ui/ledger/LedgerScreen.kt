package com.yuwhisper.account.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.Spacer as LayoutSpacer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    days: List<LedgerDayGroup>,
    onAddClick: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "\u6d41\u6c34", fontWeight = FontWeight.SemiBold)
                },
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
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "\u8bb0\u4e00\u7b14")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (days.isEmpty()) {
            AnimatedVisibility(
                visible = entered,
                enter = fadeIn() + slideInVertically { it / 8 },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("\u8fd8\u6ca1\u6709\u6d41\u6c34", style = MaterialTheme.typography.titleMedium)
                    LayoutSpacer(Modifier.height(8.dp))
                    Text(
                        "\u70b9\u53f3\u4e0b\u89d2\u300c\u8bb0\u4e00\u7b14\u300d\uff0c\u6216\u4ed8\u6b3e\u540e\u81ea\u52a8\u5f39\u51fa\u786e\u8ba4\u5361",
                        color = AccountMutedColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(days, key = { _, day -> day.date.toString() }) { _, day ->
                    AnimatedVisibility(
                        visible = entered,
                        enter = fadeIn() + slideInVertically { it / 10 },
                    ) {
                        DaySection(day)
                    }
                }
                item { LayoutSpacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun DaySection(day: LedgerDayGroup) {
    val dateLabel = day.date.format(DateTimeFormatter.ofPattern("M\u6708d\u65e5 EEEE"))
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
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
            LayoutSpacer(Modifier.height(10.dp))
            day.items.forEachIndexed { index, item ->
                if (index > 0) {
                    LayoutSpacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(vertical = 6.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    )
                }
                TransactionRow(item)
            }
        }
    }
}

@Composable
private fun TransactionRow(item: LedgerItemUi) {
    val isExpense = item.type == LedgerRepository.TYPE_EXPENSE
    val amountColor = if (isExpense) AccountExpenseColor else AccountIncomeColor
    val sign = if (isExpense) "-" else "+"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.merchant,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
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
            fontWeight = FontWeight.SemiBold,
        )
    }
}
