package com.example.githubwidget.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.githubwidget.data.RefreshState
import com.example.githubwidget.data.Repository
import com.example.githubwidget.ui.components.AddWidgetHelpSheet
import com.example.githubwidget.ui.components.AppIcons
import com.example.githubwidget.ui.components.Avatar
import com.example.githubwidget.ui.components.Hairline
import com.example.githubwidget.ui.components.NavRow
import com.example.githubwidget.ui.components.ScreenHeader
import com.example.githubwidget.ui.components.SecondaryButton
import com.example.githubwidget.ui.components.SectionCard
import com.example.githubwidget.ui.components.SegmentedChoice
import com.example.githubwidget.ui.components.WidgetPinning
import com.example.githubwidget.ui.theme.AppTheme
import kotlinx.coroutines.delay

private const val PROJECT_URL = "https://github.com/mrduhlol/github-widget"

@Composable
fun SettingsScreen(onChangeAccount: () -> Unit) {
    val context = LocalContext.current
    val username by Repository.username.collectAsState()
    val data by Repository.data.collectAsState()
    val avatar by Repository.avatar.collectAsState()
    val refreshHours by Repository.refreshHours.collectAsState()
    val refreshState by Repository.refreshState.collectAsState()

    var showHelp by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    val login = data?.profile?.login ?: username
    val displayName = data?.profile?.displayName ?: login
    val version = remember { appVersion(context) }

    // Tick once a minute so "Updated 3 min ago" stays truthful.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { ScreenHeader("Settings", subtitle = "Account, updates and help") }

        // -------------------------------------------------- account
        item {
            SectionCard(title = "Account") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(avatar, displayName, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = AppTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "@$login",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    SecondaryButton("Change", onChangeAccount)
                }
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(4.dp))
                NavRow(
                    title = "View profile on GitHub",
                    icon = Icons.Rounded.AccountCircle,
                    onClick = { openUrl(context, "https://github.com/$login") },
                )
            }
        }

        // -------------------------------------------------- updates
        item {
            SectionCard(title = "Updates") {
                Text("Refresh every", style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                SegmentedChoice(
                    options = Repository.REFRESH_CHOICES,
                    selected = refreshHours,
                    label = { "$it h" },
                    onSelect = { Repository.setRefreshHours(it) },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your widget updates automatically. Opening the app always gets the latest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(4.dp))
                val loading = refreshState is RefreshState.Loading
                val failed = refreshState as? RefreshState.Failed
                NavRow(
                    title = "Refresh now",
                    icon = Icons.Rounded.Refresh,
                    subtitle = when {
                        loading -> "Updating…"
                        failed != null -> failed.message
                        else -> lastUpdated(data?.fetchedAt, now)
                    },
                    onClick = { if (!loading) Repository.refreshAsync() },
                    trailing = if (loading) {
                        {
                            CircularProgressIndicator(
                                Modifier.padding(end = 4.dp).size(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }

        // -------------------------------------------------- widget
        item {
            SectionCard(title = "Widget", contentPadding = 10.dp) {
                NavRow(
                    title = "Add widget to home screen",
                    icon = Icons.Rounded.Add,
                    onClick = { if (!WidgetPinning.request(context)) showHelp = true },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                NavRow(
                    title = "How to add the widget",
                    icon = Icons.Rounded.Info,
                    onClick = { showHelp = true },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                NavRow(
                    title = "Reset widget design",
                    subtitle = "Go back to the original look",
                    icon = Icons.Rounded.Edit,
                    onClick = { confirmReset = true },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // -------------------------------------------------- help
        item {
            SectionCard(title = "Help", contentPadding = 10.dp) {
                FAQS.forEachIndexed { i, (q, a) ->
                    if (i > 0) Hairline(Modifier.padding(horizontal = 8.dp))
                    FaqItem(q, a)
                }
            }
        }

        // -------------------------------------------------- about
        item {
            SectionCard(title = "About") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Graph, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "GH Widgets",
                        style = MaterialTheme.typography.titleMedium,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (version != null) {
                        Text(
                            "Version $version",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Data comes from GitHub's public contribution graph. No sign-in, no password, " +
                        "nothing leaves your phone except your username.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(4.dp))
                NavRow(
                    title = "Project page",
                    subtitle = "Source code and feedback",
                    icon = Icons.Rounded.Favorite,
                    onClick = { openUrl(context, PROJECT_URL) },
                )
            }
        }

        // -------------------------------------------------- sign out
        item {
            SectionCard(contentPadding = 10.dp) {
                NavRow(
                    title = "Sign out",
                    icon = Icons.AutoMirrored.Rounded.ExitToApp,
                    iconTint = AppTheme.colors.danger,
                    titleColor = AppTheme.colors.danger,
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    trailing = {},
                )
            }
        }
    }

    if (showHelp) {
        AddWidgetHelpSheet(onDismiss = { showHelp = false })
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset widget design?",
            body = "Colors, layout and other style choices go back to how they were at the start. Your account stays the same.",
            confirm = "Reset",
            onConfirm = {
                confirmReset = false
                Repository.resetDesign()
            },
            onDismiss = { confirmReset = false },
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Your widget will be empty until you enter a username again. Your widget design is kept.",
            confirm = "Sign out",
            danger = true,
            onConfirm = {
                confirmSignOut = false
                Repository.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

// ------------------------------------------------------------------ help

private val FAQS = listOf(
    "Why don't I see private contributions?" to
        "GitHub only shares them if you allow it. On GitHub, open your profile settings and turn on " +
        "“Include private contributions on my profile”. They'll show up here after the next update.",
    "How often does it update?" to
        "On its own every few hours (you can pick how often above). Opening this app always gets the latest right away.",
    "The widget looks empty or old" to
        "Open this app once to refresh it, and check that your phone is connected to the internet.",
    "Can I make it bigger?" to
        "Yes. Touch and hold the widget on your home screen, then drag its edges. A wider widget shows more weeks.",
)

@Composable
private fun FaqItem(question: String, answer: String) {
    var open by rememberSaveable(question) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (open) 90f else 0f, label = "faqChevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClickLabel = if (open) "Hide answer" else "Show answer") { open = !open }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                question,
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                Modifier.rotate(rotation),
                tint = AppTheme.colors.textTertiary,
            )
        }
        AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(
                answer,
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 8.dp, end = 32.dp, bottom = 14.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.card,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.textPrimary) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirm,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (danger) AppTheme.colors.danger else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textSecondary)
            }
        },
    )
}

// ------------------------------------------------------------------ helpers

private fun lastUpdated(fetchedAt: Long?, now: Long): String {
    if (fetchedAt == null || fetchedAt <= 0L) return "Not updated yet"
    val age = now - fetchedAt
    if (age < DateUtils.MINUTE_IN_MILLIS) return "Updated just now"
    val rel = DateUtils.getRelativeTimeSpanString(fetchedAt, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
    return "Updated $rel"
}

private fun appVersion(context: Context): String? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser installed; nothing sensible to do.
    }
}
