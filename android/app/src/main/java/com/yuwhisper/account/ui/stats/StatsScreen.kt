package com.yuwhisper.account.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuwhisper.account.ui.MonthStatsUi
import com.yuwhisper.account.ui.centsToYuanText
import com.yuwhisper.account.ui.theme.*
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(stats: MonthStatsUi) {
    val monthLabel = stats.yearMonth.format(DateTimeFormatter.ofPattern("yyyy年M月"))
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
        ) {
            item {
                AccountPageHeading("统计", monthLabel)
                Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("本月支出", style = MaterialTheme.typography.bodyMedium, color = AccountMutedColor)
                    Text("¥${stats.totalExpenseCents.centsToYuanText()}",
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, lineHeight = 54.sp),
                        color = MaterialTheme.colorScheme.onBackground)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("支出分类", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${stats.byCategory.size} 类", style = MaterialTheme.typography.bodySmall, color = AccountMutedColor)
                }
            }
            if (stats.byCategory.isEmpty()) {
                item { AccountEmptyState(Icons.Outlined.BarChart, "本月还没有支出", "确认入账后，分类金额和占比会显示在这里。") }
            } else {
                items(stats.byCategory, key = { row -> row.name }) { row ->
                    val share = if (stats.totalExpenseCents > 0) (row.amountCents.toDouble() / stats.totalExpenseCents).toFloat().coerceIn(0f, 1f) else 0f
                    val progress by animateFloatAsState(share, AccountMotionMedium, label = "分类占比")
                    Column(Modifier.animateItem()) {
                        Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (LocalDensity.current.fontScale >= 1.3f) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(row.name, style = MaterialTheme.typography.bodyLarge)
                                    Text("¥${row.amountCents.centsToYuanText()}", fontFamily = AccountSerif,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal))
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(12.dp))
                                    Text("¥${row.amountCents.centsToYuanText()}", fontFamily = AccountSerif,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(Modifier.weight(1f).height(3.dp).background(MaterialTheme.colorScheme.primaryContainer).clearAndSetSemantics {}) {
                                    Box(Modifier.fillMaxWidth(progress).height(3.dp).background(MaterialTheme.colorScheme.primary))
                                }
                                Text("${(share * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = AccountMutedColor)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
