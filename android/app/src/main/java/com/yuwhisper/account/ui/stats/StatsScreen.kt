package com.yuwhisper.account.ui.stats

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuwhisper.account.ui.MonthStatsUi
import com.yuwhisper.account.ui.centsToYuanText
import com.yuwhisper.account.ui.theme.AccountMutedColor
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(stats: MonthStatsUi) {
    val monthLabel = stats.yearMonth.format(DateTimeFormatter.ofPattern("yyyy\u5e74M\u6708"))
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\u7edf\u8ba1") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.shapes.medium,
                        )
                        .padding(16.dp),
                ) {
                    Text(monthLabel, color = AccountMutedColor)
                    Spacer(Modifier.height(6.dp))
                    Text("\u672c\u6708\u652f\u51fa", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "\u00a5${stats.totalExpenseCents.centsToYuanText()}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Text("\u6309\u5206\u7c7b\u6c47\u603b", style = MaterialTheme.typography.titleMedium)
            }
            if (stats.byCategory.isEmpty()) {
                item {
                    Text("\u672c\u6708\u6682\u65e0\u652f\u51fa", color = AccountMutedColor)
                }
            } else {
                items(stats.byCategory, key = { it.name }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(row.name)
                        Text(
                            "\u00a5${row.amountCents.centsToYuanText()}",
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
