package com.example.githubwidget.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.githubwidget.data.AppUpdate
import com.example.githubwidget.data.AppUpdateInfo
import com.example.githubwidget.data.Repository
import com.example.githubwidget.ui.components.AppIcons
import com.example.githubwidget.ui.design.DesignScreen
import com.example.githubwidget.ui.onboarding.OnboardingScreen
import com.example.githubwidget.ui.settings.SettingsScreen
import com.example.githubwidget.ui.stats.StatsScreen
import com.example.githubwidget.ui.theme.AppTheme

enum class Tab(val label: String, val icon: ImageVector) {
    WIDGET("Widget", AppIcons.Widget),
    STATS("Stats", AppIcons.Stats),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

/**
 * Top-level navigation:
 *  - no account yet → onboarding (welcome → username → add widget)
 *  - otherwise three tabs: Widget (design), Stats, Settings
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val username by Repository.username.collectAsState()
    var changingAccount by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.WIDGET) }

    // Auto-check GitHub Releases at most once a day; pops the update dialog itself.
    var autoInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showAutoDialog by remember { mutableStateOf(false) }
    val installedVersion = remember { AppUpdate.currentVersion(context) }
    LaunchedEffect(Unit) {
        if (AppUpdate.shouldAutoCheck(context)) {
            AppUpdate.check(context)
                .onSuccess { info ->
                    AppUpdate.markChecked(context)
                    if (info != null && !AppUpdate.wasPrompted(context, info.latestVersion)) {
                        autoInfo = info
                        showAutoDialog = true
                    }
                }
                .onFailure {
                    // Stay silent; the next launch tries again.
                }
        }
    }

    val showOnboarding = username.isBlank() || changingAccount
    Crossfade(showOnboarding, label = "root") { onboarding ->
        if (onboarding) {
            OnboardingScreen(
                changingAccount = changingAccount && username.isNotBlank(),
                onDone = {
                    changingAccount = false
                    tab = Tab.WIDGET
                },
                onCancel = { changingAccount = false },
            )
        } else {
            MainTabs(tab, onTab = { tab = it }, onChangeAccount = { changingAccount = true })
        }
    }

    val info = autoInfo
    if (showAutoDialog && info != null) {
        val current = installedVersion
        AlertDialog(
            onDismissRequest = {
                showAutoDialog = false
                AppUpdate.markPrompted(context, info.latestVersion)
            },
            containerColor = AppTheme.colors.card,
            shape = MaterialTheme.shapes.large,
            title = {
                Text(
                    "Update available",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AppTheme.colors.textPrimary,
                )
            },
            text = {
                Text(
                    "Version ${info.latestVersion} is out" +
                        (if (current != null) " (you have $current)" else "") +
                        ". Download the APK and install it to update.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAutoDialog = false
                    AppUpdate.markPrompted(context, info.latestVersion)
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: ActivityNotFoundException) {
                        // No browser installed; nothing sensible to do.
                    }
                }) {
                    Text("Download", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAutoDialog = false
                    AppUpdate.markPrompted(context, info.latestVersion)
                }) {
                    Text("Later", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun MainTabs(tab: Tab, onTab: (Tab) -> Unit, onChangeAccount: () -> Unit) {
    // Back from Stats/Settings goes to the Widget tab before leaving the app.
    BackHandler(enabled = tab != Tab.WIDGET) { onTab(Tab.WIDGET) }
    Scaffold(
        containerColor = AppTheme.colors.canvas,
        bottomBar = {
            NavigationBar(containerColor = AppTheme.colors.card, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { onTab(t) },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = AppTheme.colors.textPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            unselectedIconColor = AppTheme.colors.textTertiary,
                            unselectedTextColor = AppTheme.colors.textSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                when (t) {
                    Tab.WIDGET -> DesignScreen(onOpenStats = { onTab(Tab.STATS) })
                    Tab.STATS -> StatsScreen()
                    Tab.SETTINGS -> SettingsScreen(onChangeAccount = onChangeAccount)
                }
            }
        }
    }
}
