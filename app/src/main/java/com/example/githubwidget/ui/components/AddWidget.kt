package com.example.githubwidget.ui.components

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.githubwidget.ContributionWidgetProvider
import com.example.githubwidget.ui.theme.AppTheme

object WidgetPinning {
    /** True when the launcher can add the widget for us with one tap. */
    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    /**
     * Asks the launcher to place the widget. The launcher shows its own
     * confirmation. Returns false when unsupported — show [AddWidgetHelpSheet].
     */
    fun request(context: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) return false
        return runCatching {
            mgr.requestPinAppWidget(ComponentName(context, ContributionWidgetProvider::class.java), null, null)
        }.getOrDefault(false)
    }
}

/** Step-by-step instructions for launchers that can't add widgets automatically. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWidgetHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.card,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text("Add the widget yourself", style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "It takes about 10 seconds:",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Step(1, "Go to your home screen", "Then touch and hold an empty spot until a menu appears.")
                Step(2, "Tap “Widgets”", "On some phones it's under “Add” or the + button.")
                Step(3, "Find “GH Widgets”", "Touch and hold the GitHub contributions widget, then drop it where you like.")
                Step(4, "Make it your size", "Touch and hold the widget and drag its edges. It shows more weeks when it's wider.")
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Got it", onDismiss)
        }
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}
