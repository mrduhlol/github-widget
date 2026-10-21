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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
            shape = MaterialTheme.shapes.extraLarge,
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
                        ". Download the complete new app APK. Install it over this version to keep your profile and settings.",
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
                }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Download full app", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAutoDialog = false
                    AppUpdate.markPrompted(context, info.latestVersion)
                }, modifier = Modifier.heightIn(min = 48.dp)) {
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            containerColor = AppTheme.colors.canvas,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = AppTheme.colors.card, tonalElevation = 0.dp) {
                        Tab.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = destination == tab,
                                onClick = { onTab(destination) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = AppTheme.colors.textPrimary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    unselectedIconColor = AppTheme.colors.textSecondary,
                                    unselectedTextColor = AppTheme.colors.textSecondary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.statusBarsPadding(),
                        containerColor = AppTheme.colors.canvas,
                    ) {
                        Tab.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = destination == tab,
                                onClick = { onTab(destination) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    AnimatedContent(
                        tab,
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tab",
                    ) { destination ->
                        when (destination) {
                            Tab.WIDGET -> DesignScreen(onOpenStats = { onTab(Tab.STATS) })
                            Tab.STATS -> StatsScreen()
                            Tab.SETTINGS -> SettingsScreen(onChangeAccount = onChangeAccount)
                        }
                    }
                }
            }
        }
    }
}
