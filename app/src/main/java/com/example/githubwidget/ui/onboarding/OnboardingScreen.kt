package com.example.githubwidget.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.githubwidget.data.Lookup
import com.example.githubwidget.data.Repository
import com.example.githubwidget.data.SampleData
import com.example.githubwidget.ui.components.AddWidgetHelpSheet
import com.example.githubwidget.ui.components.AppIcons
import com.example.githubwidget.ui.components.Avatar
import com.example.githubwidget.ui.components.PrimaryButton
import com.example.githubwidget.ui.components.SecondaryButton
import com.example.githubwidget.ui.components.WallpaperBackdrop
import com.example.githubwidget.ui.components.WidgetPinning
import com.example.githubwidget.ui.components.WidgetPreview
import com.example.githubwidget.ui.theme.AppTheme
import com.example.githubwidget.ui.theme.GhTheme
import com.example.githubwidget.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.text.NumberFormat

private enum class Step { WELCOME, USERNAME, CONFIRM }

/**
 * First-run flow: welcome → "what's your username?" → "is this you?" + add widget.
 * When [changingAccount] is true the welcome step is skipped and the user can cancel.
 */
@Composable
fun OnboardingScreen(changingAccount: Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    val steps = remember(changingAccount) {
        if (changingAccount) listOf(Step.USERNAME, Step.CONFIRM) else Step.entries.toList()
    }
    var step by rememberSaveable { mutableStateOf(steps.first()) }
    var name by rememberSaveable { mutableStateOf("") }
    var lookup by remember { mutableStateOf<Lookup?>(null) }

    // The lookup holds a bitmap and isn't saved; if it's gone (process death), ask again.
    LaunchedEffect(step, lookup) {
        if (step == Step.CONFIRM && lookup == null) step = Step.USERNAME
    }

    val index = steps.indexOf(step).coerceAtLeast(0)
    val canGoBack = index > 0 || changingAccount
    val goBack: () -> Unit = {
        if (index > 0) step = steps[index - 1] else onCancel()
    }
    BackHandler(enabled = canGoBack, onBack = goBack)

    // Insets are applied once around the flow, including the keyboard.
    GhTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(AppTheme.colors.canvas)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            TopBar(
                count = steps.size,
                index = index,
                showBack = canGoBack,
                backIsClose = index == 0,
                onBack = goBack,
            )
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { dir * it / 4 } + fadeIn(tween(260, 60)))
                        .togetherWith(slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -dir * it / 4 } + fadeOut(tween(180)))
                },
                label = "onboardingStep",
            ) { s ->
                when (s) {
                    Step.WELCOME -> WelcomeStep(onNext = { step = Step.USERNAME })
                    Step.USERNAME -> UsernameStep(
                        name = name,
                        onNameChange = { name = it },
                        onFound = {
                            lookup = it
                            step = Step.CONFIRM
                        },
                    )
                    Step.CONFIRM -> lookup?.let {
                        ConfirmStep(
                            lookup = it,
                            changingAccount = changingAccount,
                            onNotMe = { step = Step.USERNAME },
                            onDone = onDone,
                        )
                    } ?: Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ------------------------------------------------------------------ chrome

@Composable
private fun TopBar(count: Int, index: Int, showBack: Boolean, backIsClose: Boolean, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(48.dp)) {
                Icon(
                    if (backIsClose) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = if (backIsClose) "Cancel" else "Back",
                    tint = AppTheme.colors.textPrimary,
                )
            }
        }
        if (count > 1) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    "Step ${index + 1} of $count",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.colors.textSecondary,
                )
                StepDots(count, index)
            }
        }
    }
}

@Composable
private fun StepDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == index
            val w by animateDpAsState(if (active) 22.dp else 7.dp, tween(280), label = "dotWidth")
            val c by animateColorAsState(
                when {
                    active -> MaterialTheme.colorScheme.primary
                    i < index -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    else -> AppTheme.colors.hairline
                },
                label = "dotColor",
            )
            Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(c))
        }
    }
}

/** Keep actions visible on phones; let the whole page scroll when space is tight. */
@Composable
private fun StepPage(
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    centered: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val scrollTogether = maxHeight < 420.dp || fontScale > 1.3f
        if (scrollTogether) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                content()
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = actions)
            }
        } else {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                    verticalArrangement = if (centered) Arrangement.Center else Arrangement.Top,
                    content = content,
                )
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = actions,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ step 1: welcome

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val design by Repository.design.collectAsState()
    StepPage(
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(AppIcons.Octocat, null, Modifier.size(28.dp), tint = AppTheme.colors.textPrimary)
                Text("GH-widgets", style = MaterialTheme.typography.titleMedium, color = AppTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(30.dp))
            Text(
                "Your GitHub.\nAt a glance.",
                style = MaterialTheme.typography.displaySmall,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Your contribution graph, right on your home screen. Set it up with just your username.",
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(28.dp))
            WallpaperBackdrop {
                WidgetPreview(
                    design = design,
                    data = SampleData.userData,
                    avatar = null,
                    widthDp = 340f,
                    heightDp = 170f,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        .semantics { contentDescription = "Example contribution widget with sample data" },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Example widget · Your activity comes next",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Choose your style. Add your widget. Make it yours.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        },
        actions = {
            PrimaryButton("Set up my widget", onNext)
            Spacer(Modifier.height(12.dp))
            Text(
                "No sign-in. No token. Just your username.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        },
    )
}

// ------------------------------------------------------------------ step 2: username

@Composable
private fun UsernameStep(name: String, onNameChange: (String) -> Unit, onFound: (Lookup) -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Wait for the slide-in to settle so the keyboard doesn't fight the animation.
        kotlinx.coroutines.delay(250)
        runCatching { focus.requestFocus() }
    }

    val submit: () -> Unit = submit@{
        if (loading) return@submit
        if (name.isBlank()) {
            error = "Type your GitHub username to continue."
            return@submit
        }
        error = null
        loading = true
        scope.launch {
            val result = Repository.lookup(name)
            loading = false
            result
                .onSuccess {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onFound(it)
                }
                .onFailure { error = it.message ?: "Something went wrong. Please try again." }
        }
    }

    StepPage(
        content = {
            Spacer(Modifier.height(16.dp))
            Text(
                "Find your GitHub profile",
                style = MaterialTheme.typography.headlineMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Enter a username or paste your GitHub profile link. We only need your public profile.",
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            ProfileLinkExample()
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    onNameChange(it.replace("\n", ""))
                    error = null
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                enabled = !loading,
                isError = error != null,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = AppTheme.colors.textPrimary),
                label = { Text("GitHub username") },
                placeholder = {
                    Text("username", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.textSecondary)
                },
                prefix = {
                    Text("@", style = MaterialTheme.typography.titleLarge, color = AppTheme.colors.textSecondary)
                },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppTheme.colors.card,
                    unfocusedContainerColor = AppTheme.colors.card,
                    disabledContainerColor = AppTheme.colors.card,
                    errorContainerColor = AppTheme.colors.card,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = AppTheme.colors.hairline,
                    disabledBorderColor = AppTheme.colors.hairline,
                    errorBorderColor = AppTheme.colors.danger,
                    disabledTextColor = AppTheme.colors.textSecondary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            AnimatedVisibility(error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Row(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Info, null,
                        Modifier.padding(top = 1.dp).size(18.dp),
                        tint = AppTheme.colors.danger,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.danger,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "No password or access token required.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
        },
        actions = {
            PrimaryButton(
                if (loading) "Finding profile…" else "Find my profile",
                submit,
                modifier = Modifier.semantics { contentDescription = if (loading) "Finding GitHub profile" else "Find my profile" },
                loading = loading,
                enabled = name.isNotBlank(),
            )
        },
    )
}

/** A profile-link example that remains readable at large font sizes. */
@Composable
private fun ProfileLinkExample() {
    Text(
        "For example: github.com/username",
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
    )
}

// ------------------------------------------------------------------ step 3: confirm

@Composable
private fun ConfirmStep(lookup: Lookup, changingAccount: Boolean, onNotMe: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val design by Repository.design.collectAsState()
    val hasWidget = remember { WidgetUpdater.hasWidgets(context) }
    var showHelp by remember { mutableStateOf(false) }

    val login = lookup.data.contributions.username
    val displayName = lookup.data.profile?.displayName ?: login
    val total = lookup.data.contributions.totalLastYear
    val totalText = NumberFormat.getIntegerInstance().format(total)

    fun finish() {
        Repository.signIn(lookup)
        onDone()
    }

    StepPage(
        content = {
            Spacer(Modifier.height(16.dp))
            Text(
                if (changingAccount || hasWidget) "Confirm your profile" else "Make it your home screen",
                style = MaterialTheme.typography.headlineMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (changingAccount || hasWidget) "Check that this is the profile you want to follow." else "Check your profile, then add your widget.",
                style = MaterialTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = AppTheme.colors.card,
                border = BorderStroke(1.dp, AppTheme.colors.hairline),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(lookup.avatar, displayName, 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = AppTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (displayName != login) {
                            Text(
                                "@$login",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (total == 1) "1 contribution in the last year" else "$totalText contributions in the last year",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            WallpaperBackdrop {
                WidgetPreview(
                    design = design,
                    data = lookup.data,
                    avatar = lookup.avatar,
                    widthDp = 340f,
                    heightDp = 170f,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    changingAccount -> "Your widget will switch to this account."
                    hasWidget -> "Your widget will show this graph right away."
                    else -> "This is how your widget will look. You can change its style later."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        },
        actions = {
            when {
                changingAccount -> {
                    PrimaryButton("Use this account", ::finish)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Not me", onNotMe, Modifier.fillMaxWidth())
                }
                hasWidget -> {
                    PrimaryButton("Looks good", ::finish)
                    Spacer(Modifier.height(4.dp))
                    QuietButton("Not me, go back", onNotMe)
                }
                else -> {
                    PrimaryButton("Add to home screen", onClick = {
                        if (WidgetPinning.isSupported(context)) {
                            // Save first so the new widget shows this graph immediately.
                            Repository.signIn(lookup)
                            WidgetPinning.request(context)
                            onDone()
                        } else {
                            // Saving would close onboarding, so show the steps first.
                            showHelp = true
                        }
                    })
                    Spacer(Modifier.height(4.dp))
                    QuietButton("I'll add it later", ::finish, Modifier.fillMaxWidth())
                    QuietButton("Choose another profile", onNotMe, Modifier.fillMaxWidth())
                }
            }
        },
    )

    if (showHelp) {
        AddWidgetHelpSheet(onDismiss = {
            showHelp = false
            finish()
        })
    }
}

@Composable
private fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}
