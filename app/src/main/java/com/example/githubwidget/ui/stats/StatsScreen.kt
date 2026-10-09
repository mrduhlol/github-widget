package com.example.githubwidget.ui.stats

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.githubwidget.data.RefreshState
import com.example.githubwidget.data.Repository
import com.example.githubwidget.data.Stats
import com.example.githubwidget.data.UserData
import com.example.githubwidget.design.Palettes
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.ui.components.AppIcons
import com.example.githubwidget.ui.components.Avatar
import com.example.githubwidget.ui.components.PrimaryButton
import com.example.githubwidget.ui.components.ScreenHeader
import com.example.githubwidget.ui.components.SectionCard
import com.example.githubwidget.ui.components.SectionLabel
import com.example.githubwidget.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val numbers: NumberFormat get() = NumberFormat.getIntegerInstance()

private fun count(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "${numbers.format(n)} $many"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val data by Repository.data.collectAsState()
    val avatar by Repository.avatar.collectAsState()
    val design by Repository.design.collectAsState()
    val refresh by Repository.refreshState.collectAsState()
    val loading = refresh is RefreshState.Loading

    // Only show the pull indicator when the user actually pulled.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading) pulled = false }

    val current = data
    if (current == null) {
        EmptyState(loading = loading, failure = (refresh as? RefreshState.Failed)?.message)
        return
    }

    PullToRefreshBox(
        isRefreshing = pulled && loading,
        onRefresh = {
            pulled = true
            Repository.refreshAsync()
        },
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        StatsContent(current, avatar, design, refresh)
    }
}

@Composable
private fun StatsContent(
    data: UserData,
    avatar: android.graphics.Bitmap?,
    design: WidgetDesign,
    refresh: RefreshState,
) {
    val stats = remember(data.contributions) { Stats.from(data.contributions) }
    val context = LocalContext.current
    val dark = AppTheme.colors.isDark
    val empty = AppTheme.colors.raised
    val levels = remember(design, dark, empty) {
        val set = Palettes.colors(context, design.copy(opacity = 100), dark)
        // The app's card color differs from the widget's, so use our own "empty" square.
        listOf(empty) + set.levels.drop(1).map { Color(it) }
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("header") {
            ScreenHeader(
                "Your activity",
                subtitle = if (refresh is RefreshState.Loading) "Updating…" else updatedText(data.fetchedAt, now),
                action = { RefreshAction(refresh is RefreshState.Loading) },
            )
        }
        if (refresh is RefreshState.Failed) {
            item("error") { ErrorBanner(refresh.message) }
        }
        item("hero") { HeroCard(data, avatar, stats) }
        item("year") {
            SectionCard(title = "Your year") {
                YearHeatmap(
                    days = data.contributions.days,
                    levels = levels,
                    radiusFraction = design.cellShape.radiusFraction,
                    weekStartsMonday = design.weekStartsMonday,
                )
            }
        }
        item("tiles") { StatTiles(stats) }
        item("month") { InsightCard(stats) }
        item("weekdays") { WeekdayCard(stats, design.weekStartsMonday) }
        item("months") { MonthsCard(stats) }
        item("share") { ShareCard(data, avatar, design) }
    }
}

// ------------------------------------------------------------------ header

private fun updatedText(fetchedAt: Long, now: Long): String {
    if (fetchedAt <= 0L) return "Not updated yet"
    val minutes = ((now - fetchedAt) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Updated just now"
        minutes < 60 -> "Updated $minutes min ago"
        minutes < 24 * 60 -> (minutes / 60).toInt().let { if (it == 1) "Updated 1 hour ago" else "Updated $it hours ago" }
        minutes < 48 * 60 -> "Updated yesterday"
        else -> {
            val date = Instant.ofEpochMilli(fetchedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            "Updated on " + date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        }
    }
}

@Composable
private fun RefreshAction(loading: Boolean) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp,
            )
        } else {
            IconButton(onClick = { Repository.refreshAsync() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = AppTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val warning = AppTheme.colors.warning
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = warning.copy(alpha = if (AppTheme.colors.isDark) 0.12f else 0.08f),
        border = BorderStroke(1.dp, warning.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = warning, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Couldn't update", style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { Repository.clearError() }) {
                    Text("Dismiss", color = AppTheme.colors.textSecondary)
                }
                TextButton(onClick = { Repository.refreshAsync() }) {
                    Text("Try again", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ hero

@Composable
private fun HeroCard(data: UserData, avatar: android.graphics.Bitmap?, stats: Stats) {
    val login = data.profile?.login ?: data.contributions.username
    val name = data.profile?.displayName ?: login
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.hairline),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(primary.copy(alpha = if (AppTheme.colors.isDark) 0.14f else 0.08f), Color.Transparent),
                    ),
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(avatar, name, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (name.equals(login, ignoreCase = true)) "github.com/$login" else "@$login",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                numbers.format(stats.total),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = MaterialTheme.typography.displaySmall.fontSize * 1.35f,
                    lineHeight = MaterialTheme.typography.displaySmall.lineHeight * 1.3f,
                ),
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                if (stats.total == 1) "contribution in the last year" else "contributions in the last year",
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
            )
            if (stats.thisMonth > 0) {
                Spacer(Modifier.height(16.dp))
                Pill("${count(stats.thisMonth, "contribution", "contributions")} this month")
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color = MaterialTheme.colorScheme.primary, icon: ImageVector? = null) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (color == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onPrimaryContainer else color,
        )
    }
}

// ------------------------------------------------------------------ tiles

private data class Tile(
    val label: String,
    val value: String,
    val suffix: String?,
    val icon: ImageVector,
    val tint: Color,
)

@Composable
private fun StatTiles(stats: Stats) {
    val warning = AppTheme.colors.warning
    val neutral = AppTheme.colors.textSecondary
    val primary = MaterialTheme.colorScheme.primary
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()) }
    val tiles = listOf(
        Tile(
            "Current streak", numbers.format(stats.currentStreak), if (stats.currentStreak == 1) "day" else "days",
            AppIcons.Flame, if (stats.currentStreak > 0) warning else neutral,
        ),
        Tile(
            "Longest streak", numbers.format(stats.longestStreak), if (stats.longestStreak == 1) "day" else "days",
            Icons.Rounded.Star, neutral,
        ),
        Tile("Today", numbers.format(stats.today), null, Icons.Rounded.CheckCircle, if (stats.today > 0) primary else neutral),
        Tile("This week", numbers.format(stats.thisWeek), null, Icons.Rounded.DateRange, neutral),
        Tile(
            stats.bestDay?.let { "Best day · ${it.date.format(dateFormat)}" } ?: "Best day",
            numbers.format(stats.bestDay?.count ?: 0), null, Icons.Rounded.ThumbUp, neutral,
        ),
        Tile(
            "Active days", numbers.format(stats.activeDays), "of ${numbers.format(stats.trackedDays)}",
            AppIcons.Graph, neutral,
        ),
    )
    Column {
        SectionLabel("At a glance", Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { StatTile(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun StatTile(tile: Tile, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(20.dp),
        color = AppTheme.colors.card,
        border = BorderStroke(1.dp, AppTheme.colors.hairline),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tile.tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tile.icon, contentDescription = null, tint = tile.tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                buildAnnotatedString {
                    append(tile.value)
                    if (tile.suffix != null) {
                        withStyle(
                            SpanStyle(
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                fontWeight = FontWeight.Medium,
                                color = AppTheme.colors.textSecondary,
                            ),
                        ) { append(" ${tile.suffix}") }
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tile.label,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                maxLines = 2,
            )
        }
    }
}

// ------------------------------------------------------------------ insights

private fun weekdayPlural(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.FULL, Locale.getDefault()).let { if (Locale.getDefault().language == "en") "${it}s" else it }

@Composable
private fun InsightCard(stats: Stats) {
    val last = stats.last30
    val prev = stats.previous30
    val change: Int? = if (prev != null && prev > 0) (((last - prev) * 100.0) / prev).roundToInt() else null
    val sentence = when {
        last == 0 && (prev == null || prev == 0) -> "No contributions in the last 30 days. Your next one will show up here."
        last == 0 -> "No contributions in the last 30 days, down from ${numbers.format(prev!!)} the 30 days before."
        prev == null -> "You made ${count(last, "contribution", "contributions")} in the last 30 days."
        prev == 0 -> "You made ${count(last, "contribution", "contributions")}, up from none the 30 days before."
        change != null && abs(change) < 5 -> "You made ${count(last, "contribution", "contributions")} — about the same as the 30 days before."
        change != null && change > 0 -> "You made ${count(last, "contribution", "contributions")} — $change% more than the 30 days before."
        else -> "You made ${count(last, "contribution", "contributions")} — ${abs(change ?: 0)}% fewer than the 30 days before."
    }

    SectionCard(title = "Last 30 days") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                numbers.format(last),
                style = MaterialTheme.typography.headlineMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            when {
                change != null && abs(change) >= 5 && change > 0 ->
                    Pill("$change%", AppTheme.colors.success, Icons.Rounded.KeyboardArrowUp)
                change != null && abs(change) >= 5 ->
                    Pill("${abs(change)}%", AppTheme.colors.textSecondary, Icons.Rounded.KeyboardArrowDown)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(sentence, style = MaterialTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary)

        val busiest = stats.busiestWeekday
        if (busiest != null || stats.activeDays > 0) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.hairline))
            Spacer(Modifier.height(16.dp))
            if (busiest != null) {
                InsightLine(AppIcons.Stats, "You're most active on ${weekdayPlural(busiest)}.")
            }
            if (stats.activeDays > 0) {
                if (busiest != null) Spacer(Modifier.height(10.dp))
                val avg = stats.averagePerActiveDay
                val avgText = if (avg < 10) String.format(Locale.getDefault(), "%.1f", avg) else numbers.format(avg.roundToInt())
                InsightLine(AppIcons.Graph, "On days you contribute, you make $avgText on average.")
            }
        }
    }
}

@Composable
private fun InsightLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
    }
}

// ------------------------------------------------------------------ charts

@Composable
private fun WeekdayCard(stats: Stats, weekStartsMonday: Boolean) {
    // weekdayTotals is Monday first; rotate for Sunday-first weeks.
    val order = if (weekStartsMonday) (0..6).toList() else listOf(6) + (0..5)
    val values = order.map { stats.weekdayTotals[it] }
    val labels = order.map {
        DayOfWeek.of(it + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.')
    }
    val busiest = stats.busiestWeekday
    val highlight = busiest?.let { order.indexOf(it.value - 1) }
        ?: values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0 }
    SectionCard(title = "Days of the week", subtitle = "Contributions for each day of the week over the last year.") {
        BarChart(
            values = values,
            labels = labels,
            highlight = highlight,
            description = buildString {
                append("Contributions by day of the week. ")
                order.forEachIndexed { i, d ->
                    append("${DayOfWeek.of(d + 1).getDisplayName(TextStyle.FULL, Locale.getDefault())}: ${numbers.format(values[i])}. ")
                }
            },
        )
    }
}

@Composable
private fun MonthsCard(stats: Stats) {
    val values = stats.months.map { it.second }
    val labels = stats.months.map { (ym, _) ->
        ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.').take(3)
    }
    val best = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0 }
    SectionCard(title = "Months") {
        if (best != null) {
            val (ym, total) = stats.months[best]
            Text(
                "Your best month was ${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                "${count(total, "contribution", "contributions")} that month",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        } else {
            Text(
                "No contributions in the last 12 months yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(16.dp))
        BarChart(
            values = values,
            labels = labels,
            highlight = best,
            spacing = 5.dp,
            barAreaHeight = 110.dp,
            description = buildString {
                append("Contributions per month. ")
                stats.months.forEach { (ym, v) ->
                    append("${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}: ${numbers.format(v)}. ")
                }
            },
        )
    }
}

// ------------------------------------------------------------------ share

@Composable
private fun ShareCard(data: UserData, avatar: android.graphics.Bitmap?, design: WidgetDesign) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    SectionCard(title = "Share") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Graph, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Share your year", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
                Text(
                    "Make a picture of your graph and stats to send to friends or post online.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Share image",
            icon = Icons.Rounded.Share,
            loading = busy,
            onClick = {
                busy = true
                scope.launch {
                    val ok = ShareImage.share(context, data, avatar, design)
                    busy = false
                    if (!ok) {
                        Toast.makeText(context, "Couldn't create the image. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

// ------------------------------------------------------------------ empty

@Composable
private fun EmptyState(loading: Boolean, failure: String?) {
    Box(
        Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 360.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    "Loading your activity…",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                )
            } else {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Graph, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "No activity to show yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    failure ?: "We haven't loaded your GitHub activity yet. Make sure you're online, then try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Try again", onClick = { Repository.refreshAsync() }, icon = Icons.Rounded.Refresh)
            }
        }
    }
}
