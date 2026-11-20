package com.example.githubwidget.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.githubwidget.data.Repository
import com.example.githubwidget.data.Day
import com.example.githubwidget.data.SampleData
import java.time.LocalDate
import java.time.ZoneOffset
import com.example.githubwidget.data.GitHubApi
import com.example.githubwidget.data.ReviewInbox
import com.example.githubwidget.data.ReviewInboxState
import com.example.githubwidget.design.AuroraColor
import com.example.githubwidget.design.Background
import com.example.githubwidget.design.CellShape
import com.example.githubwidget.design.Corners
import com.example.githubwidget.design.Palette
import com.example.githubwidget.design.NumberFont
import com.example.githubwidget.design.Palettes
import com.example.githubwidget.design.ProfileCount
import com.example.githubwidget.design.ProfileTypeface
import com.example.githubwidget.design.Range
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.design.WidgetLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.githubwidget.ui.components.AddWidgetHelpSheet
import com.example.githubwidget.ui.components.Field
import com.example.githubwidget.ui.components.Hairline
import com.example.githubwidget.ui.components.PreviewSize
import com.example.githubwidget.ui.components.PrimaryButton
import com.example.githubwidget.ui.components.SectionCard
import com.example.githubwidget.ui.components.SegmentedChoice
import com.example.githubwidget.ui.components.ToggleRow
import com.example.githubwidget.ui.components.WallpaperBackdrop
import com.example.githubwidget.ui.components.WidgetPinning
import com.example.githubwidget.ui.components.WidgetPreview
import com.example.githubwidget.ui.components.selectionBorder
import com.example.githubwidget.ui.theme.AppTheme
import com.example.githubwidget.ui.theme.numberFontFamily
import com.example.githubwidget.widget.WidgetUpdater

/**
 * The "Widget" tab: a live preview followed by simple visual
 * choices below it. Every change is saved and applied to the home-screen
 * widget immediately — there's no Save button to forget.
 */
@Composable
fun DesignScreen(layout: WidgetLayout, initialSize: PreviewSize, onBack: () -> Unit, onOpenStats: () -> Unit) {
    val context = LocalContext.current
    val sharedDesign by Repository.design.collectAsState()
    val design = sharedDesign.copy(layout = layout)
    val realData by Repository.data.collectAsState()
    val avatar by Repository.avatar.collectAsState()
    val data = realData ?: SampleData.userData
    val username by Repository.username.collectAsState()
    val activeInbox by ReviewInbox.state.collectAsState()
    val inbox = activeInbox?.takeIf { it.username == username }
        ?: remember(username) { ReviewInbox.cached(context, username) }
    LaunchedEffect(design.layout, username) {
        if (design.layout == WidgetLayout.PR_INBOX) {
            refreshReviewInbox(context, username)
        }
    }
    var profilePanel by rememberSaveable { mutableStateOf("Content") }
    var previewSize by rememberSaveable(layout, initialSize) { mutableStateOf(initialSize) }
    var hasWidget by remember { mutableStateOf(WidgetUpdater.hasWidgets(context)) }
    var showHelp by remember { mutableStateOf(false) }
    var pickingColor by remember { mutableStateOf(false) }

    // Re-check when coming back from the home screen (the user may have just added one).
    LifecycleResumeEffect(Unit) {
        hasWidget = WidgetUpdater.hasWidgets(context)
        onPauseOrDispose { }
    }

    fun update(t: (WidgetDesign) -> WidgetDesign) = Repository.updateDesign { t(it.copy(layout = layout)) }
    fun addWidget() {
        update { it }
        if (!WidgetPinning.request(context, layout, previewSize == PreviewSize.PORTRAIT)) showHelp = true
    }

    LazyColumn(
        Modifier.fillMaxSize().background(AppTheme.colors.canvas).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth(),
            ) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("‹  All widgets")
                }
                Text(layout.label, style = MaterialTheme.typography.headlineMedium,
                    color = AppTheme.colors.textPrimary)
                Text(layout.description, style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary)
                Spacer(Modifier.height(14.dp))
                WallpaperBackdrop {
                    WidgetPreview(
                        design = design,
                        data = data,
                        avatar = if (realData != null) avatar else null,
                        widthDp = previewSize.widthDp,
                        heightDp = previewSize.heightDp,
                        inbox = if (design.layout == WidgetLayout.PR_INBOX) inbox else null,
                        modifier = Modifier.fillMaxWidth().height(if (previewSize == PreviewSize.PORTRAIT) 320.dp else if (previewSize == PreviewSize.LARGE) 250.dp else 180.dp),
                    )
                }
                if (realData == null && design.layout != WidgetLayout.PR_INBOX) {
                    Text("Example data · Refresh Stats to load your activity", style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                }
                Spacer(Modifier.height(10.dp))
                SegmentedChoice(
                    options = PreviewSize.entries,
                    selected = previewSize,
                    label = { it.label },
                    onSelect = { previewSize = it },
                )
            }
        }
        // ---------------------------------------------------------- controls
        item {
            AddWidgetCard(hasWidget, onAdd = ::addWidget, onHelp = { showHelp = true })
        }
        if (design.layout == WidgetLayout.PROFILE) {
            item { SegmentedChoice(listOf("Content", "Appearance"), profilePanel, { it }, { profilePanel = it }) }
            if (profilePanel == "Content") item { ProfileCardOptions(design, ::update) }
        }
        if (design.layout != WidgetLayout.PROFILE || profilePanel == "Appearance") {
            item {
                if (layout == WidgetLayout.STREAK) {
                    SectionCard(title = "Aurora color", subtitle = "Used when you contribute today.") {
                        val day = LocalDate.now(ZoneOffset.UTC)
                        val example = remember(day) {
                            val sample = SampleData.userData
                            sample.copy(contributions = sample.contributions.copy(days =
                                (0L..5L).map { Day(day.minusDays(it), if (it < 5) 1 else 0, 1) }))
                        }
                        WidgetPreview(design, example, null, 340f, 170f,
                            Modifier.fillMaxWidth().height(135.dp), dark = true)
                        Text("Active state · Example", style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        SegmentedChoice(AuroraColor.entries, design.auroraColor, { it.label },
                            { color -> update { it.copy(auroraColor = color) } })
                    }
                } else {
                    SectionCard(title = "Colors") {
                        PaletteRow(design, onSelect = { id -> update { it.copy(paletteId = id) } }, onCustom = { pickingColor = true })
                    }
                }
            }
            item {
                SectionCard(title = "Font") {
                    if (design.layout == WidgetLayout.PROFILE) {
                        Field("Name and username") {
                            SegmentedChoice(ProfileTypeface.entries, design.profileTypeface, { it.label },
                                { face -> update { it.copy(profileTypeface = face) } })
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Field("Typeface", hint = "Used for numbers in widgets, Stats and share images. Dot matrix keeps small labels in a regular font.") {
                        NumberFontChoice(onSelect = { Repository.setNumberFont(it) })
                    }
                }
            }
            item {
                SectionCard(title = "Background") {
                    if (layout != WidgetLayout.STREAK) Field("Card color", hint = if (design.background == Background.AUTO) "Light or dark, following your phone's setting" else null) {
                        SegmentedChoice(Background.entries, design.background, { it.label }, { b -> update { it.copy(background = b) } })
                    }
                    if (layout != WidgetLayout.STREAK) Spacer(Modifier.height(20.dp))
                    OpacitySlider(design.opacity) { v -> update { it.copy(opacity = v) } }
                    if (layout != WidgetLayout.PROFILE && layout != WidgetLayout.STREAK) {
                        Spacer(Modifier.height(16.dp))
                        Field("Corners") {
                            SegmentedChoice(Corners.entries, design.corners, { it.label }, { c -> update { it.copy(corners = c) } })
                        }
                    }
                }
            }
        }
        if (design.layout in listOf(WidgetLayout.CLASSIC, WidgetLayout.GRAPH, WidgetLayout.NUMBERS)) item {
            SectionCard(title = "Graph") {
                Field(
                    "How much to show",
                    hint = if (design.range == Range.AUTO) "Shows as many weeks as fit. Make the widget wider to see more." else null,
                ) {
                    SegmentedChoice(Range.entries, design.range, { it.label }, { r -> update { it.copy(range = r) } })
                }
                Spacer(Modifier.height(20.dp))
                Field("Square shape") {
                    SegmentedChoice(CellShape.entries, design.cellShape, { it.label }, { s -> update { it.copy(cellShape = s) } })
                }
                Spacer(Modifier.height(20.dp))
                Field("Weeks start on") {
                    SegmentedChoice(
                        listOf(false, true), design.weekStartsMonday,
                        { if (it) "Monday" else "Sunday" },
                        { m -> update { it.copy(weekStartsMonday = m) } },
                    )
                }
            }
        }
        if (design.layout.isWeeklyGoal) {
            item {
                SectionCard(title = "Weekly goal", subtitle = "Contribution count · Monday to Sunday") {
                    Field("Target", hint = "${design.weeklyGoal} contributions per week") {
                        Slider(
                            value = design.weeklyGoal.toFloat().coerceIn(1f, 200f),
                            onValueChange = { value -> update { it.copy(weeklyGoal = value.toInt().coerceIn(1, 200)) } },
                            valueRange = 1f..200f,
                        )
                    }
                }
            }
        }
        item {
            when {
                design.layout == WidgetLayout.PROFILE || design.layout == WidgetLayout.STREAK -> Unit
                design.layout == WidgetLayout.PR_INBOX -> ReviewInboxSection(inbox)
                design.layout.isWeeklyGoal -> Text(
                    "Colors and fonts are shared. Each widget keeps its own type. All widgets use your connected GitHub profile.",
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary,
                )
                else -> ShowOnWidget(design, ::update)
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (realData != null) {
                    TextButton(onClick = onOpenStats, modifier = Modifier.heightIn(min = 48.dp)) { Text("See your full activity") }
                }
                if (design != WidgetDesign(layout = layout, contributionLayout = design.contributionLayout)) {
                    TextButton(onClick = { update { WidgetDesign(layout = layout, contributionLayout = it.contributionLayout) } }) {
                        Text("Reset to the original look", color = AppTheme.colors.textSecondary)
                    }
                }
            }
        }
    }

    if (showHelp) AddWidgetHelpSheet(onDismiss = { showHelp = false })
    if (pickingColor) {
        ColorPickerDialog(
            initial = design.customColor,
            onDismiss = { pickingColor = false },
            onPick = { c ->
                pickingColor = false
                update { it.copy(paletteId = Palettes.CUSTOM_ID, customColor = c) }
            },
        )
    }
}

// ------------------------------------------------------------------ add widget

@Composable
private fun AddWidgetCard(hasWidget: Boolean, onAdd: () -> Unit, onHelp: () -> Unit) {
    if (!hasWidget) {
        Column {
            PrimaryButton("Add to home screen", onAdd, icon = Icons.Rounded.Add)
            TextButton(onClick = onHelp, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("How do I add a widget?", color = AppTheme.colors.textSecondary)
            }
        }
    } else {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = AppTheme.colors.success.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, AppTheme.colors.success.copy(alpha = 0.25f)),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = AppTheme.colors.success, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Your widget is on your home screen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAdd) { Text("Add another") }
            }
        }
    }
}

// ------------------------------------------------------------------ colors

@Composable
private fun PaletteRow(design: WidgetDesign, onSelect: (String) -> Unit, onCustom: () -> Unit) {
    val context = LocalContext.current
    val dark = AppTheme.colors.isDark
    val options = buildList {
        if (Palettes.supportsWallpaper) add(Palettes.WALLPAPER_ID)
        addAll(Palettes.presets.map { it.id })
        add(Palettes.CUSTOM_ID)
    }
    val selectedName = when (design.paletteId) {
        Palettes.WALLPAPER_ID -> "Matches your wallpaper"
        Palettes.CUSTOM_ID -> "Your own color"
        else -> Palettes.presets.firstOrNull { it.id == design.paletteId }?.name ?: ""
    }
    Text(selectedName, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
    Spacer(Modifier.height(12.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
        items(options) { id ->
            val swatchDesign = design.copy(paletteId = id)
            val colors = remember(id, dark, design.customColor, design.background, design.opacity) {
                Palettes.colors(context, swatchDesign, dark)
            }
            val palette: Palette = Palettes.palette(context, swatchDesign, dark)
            val label = when (id) {
                Palettes.WALLPAPER_ID -> "Wallpaper"
                Palettes.CUSTOM_ID -> "Custom"
                else -> palette.name
            }
            Swatch(
                label = label,
                levels = colors.levels.drop(1).map { Color(it) },
                selected = design.paletteId == id,
                showEdit = id == Palettes.CUSTOM_ID,
                onClick = { if (id == Palettes.CUSTOM_ID) onCustom() else onSelect(id) },
            )
        }
    }
}

@Composable
private fun Swatch(label: String, levels: List<Color>, selected: Boolean, showEdit: Boolean, onClick: () -> Unit) {
    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else AppTheme.colors.hairline, label = "ring")
    Column(
        Modifier
            .width(64.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick, onClickLabel = "Use $label colors")
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .border(selectionBorder(selected), ring, MaterialTheme.shapes.medium)
                .padding(6.dp)
                .clip(MaterialTheme.shapes.small)
                .background(AppTheme.colors.raised),
            contentAlignment = Alignment.Center,
        ) {
            // A 2×2 mini graph of the palette's four intensities.
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Cell(levels[0]); Cell(levels[1]) }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Cell(levels[2]); Cell(levels[3]) }
            }
            if (selected || showEdit) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else AppTheme.colors.card),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (selected) Icons.Rounded.Check else Icons.Rounded.Edit, null,
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else AppTheme.colors.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Cell(c: Color) {
    Box(Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(c))
}

@Composable
internal fun WidgetCatalog(onOpen: (WidgetLayout, PreviewSize) -> Unit) {
    val context = LocalContext.current
    val design by Repository.design.collectAsState()
    val realData by Repository.data.collectAsState()
    val avatar by Repository.avatar.collectAsState()
    val username by Repository.username.collectAsState()
    val inbox by ReviewInbox.state.collectAsState()
    val cachedInbox = remember(username) { ReviewInbox.cached(context, username) }
    val widgets = WidgetLayout.entries.map { it to PreviewSize.WIDE } + (WidgetLayout.PROFILE to PreviewSize.PORTRAIT)
    val minTileWidth = 144.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minTileWidth),
        modifier = Modifier.fillMaxSize().background(AppTheme.colors.canvas).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Widgets", style = MaterialTheme.typography.headlineMedium, color = AppTheme.colors.textPrimary)
                Text("Choose a widget to preview and customize.", style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary)
            }
        }
        gridItems(widgets, key = { (layout, size) -> "${layout.name}-${size.name}" }) { (layout, size) ->
            val portrait = size == PreviewSize.PORTRAIT
            val shape = MaterialTheme.shapes.medium
            Column(
                Modifier.fillMaxWidth().clip(shape).clickable(role = Role.Button) { onOpen(layout, size) },
            ) {
                WidgetPreview(
                    design.copy(layout = layout), realData ?: SampleData.userData, avatar, size.widthDp, size.heightDp,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).clip(shape)
                        .background(AppTheme.colors.raised).padding(8.dp),
                    inbox = if (layout == WidgetLayout.PR_INBOX) inbox?.takeIf { it.username == username } ?: cachedInbox else null,
                    contentDescription = null,
                )
                Text(
                    if (portrait) "Portrait profile" else layout.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(start = 2.dp, top = 8.dp, end = 2.dp),
                )
                Text(
                    if (portrait) "Your GitHub ID in a vertical layout" else layout.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NumberFontChoice(onSelect: (NumberFont) -> Unit) {
    val selected by Repository.numberFont.collectAsState()
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberFont.entries.chunked(columns).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { font ->
                    FontCard(font, selected = font == selected, onClick = { onSelect(font) }, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1 && columns == 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FontCard(font: NumberFont, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val family = remember(font) { numberFontFamily(context, font) }
    val shape = MaterialTheme.shapes.medium
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else AppTheme.colors.hairline, label = "font")
    Column(
        modifier
            .clip(shape)
            .border(selectionBorder(selected), border, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(font.label, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            "1,182",
            fontFamily = family,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary,
        )
    }
}

// ------------------------------------------------------------------ background

@Composable
private fun OpacitySlider(opacity: Int, onChange: (Int) -> Unit) {
    var local by remember(opacity) { mutableStateOf(opacity.toFloat()) }
    val label = when {
        local < 5 -> "Fully see-through"
        local >= 95 -> "Solid"
        else -> "${local.toInt()}%"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Visibility", style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppTheme.colors.textSecondary)
    }
    Text(
        "Lower it to let your wallpaper shine through.",
        style = MaterialTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
    )
    Slider(
        value = local,
        onValueChange = {
            local = it
            onChange(it.toInt())
        },
        valueRange = 0f..100f,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = AppTheme.colors.raised,
        ),
    )
}

private suspend fun refreshReviewInbox(context: android.content.Context, username: String) {
    ReviewInbox.refresh(context, username)
    withContext(Dispatchers.Default) {
        WidgetUpdater.update(context, WidgetUpdater.reviewInboxIds(context))
    }
}

@Composable
private fun ReviewInboxSection(inbox: ReviewInboxState) {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var refreshing by remember(inbox.username) { mutableStateOf(false) }
    SectionCard(title = "Review requests", subtitle = "Open pull requests in public repositories waiting for your review.") {
        val results = inbox.results
        when {
            inbox.username.isBlank() -> Text("Connect a GitHub profile to check for review requests.")
            inbox.failed && results == null -> Text("Couldn’t check for review requests.")
            inbox.failed -> Text("Couldn’t refresh. Showing the last check.")
            results == null -> Text("Checking GitHub…")
            results.total == 0 -> Text("All caught up.")
            results.requests.isEmpty() -> Text("Open GitHub to see your review requests.")
        }
        results?.requests.orEmpty().forEach { request ->
            TextButton(onClick = { uriHandler.openUri(request.url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(request.title)
                    Text("${request.repository} #${request.number}", style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary)
                }
            }
        }
        if (inbox.username.isNotBlank()) {
            TextButton(onClick = {
                scope.launch {
                    refreshing = true
                    try {
                        refreshReviewInbox(context, inbox.username)
                    } finally {
                        refreshing = false
                    }
                }
            }, enabled = !refreshing) { Text(if (refreshing) "Refreshing…" else "Refresh") }
            if (results != null && results.total > results.requests.size) {
                TextButton(onClick = { uriHandler.openUri(GitHubApi.reviewSearchUrl(inbox.username)) }) {
                    Text("See all on GitHub")
                }
            }
        }
    }
}

@Composable
private fun ProfileCardOptions(design: WidgetDesign, update: ((WidgetDesign) -> WidgetDesign) -> Unit) {
    val realData by Repository.data.collectAsState()
    val data = realData ?: SampleData.userData
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionCard(title = "Identity", subtitle = "Choose what appears. Your settings apply to all profile cards.") {
            ProfileChoices(listOf(
                Triple("Photo", design.profileShowAvatar, { update { it.copy(profileShowAvatar = !it.profileShowAvatar) } }),
                Triple("Name", design.profileShowName, { update { it.copy(profileShowName = !it.profileShowName) } }),
                Triple("Username", design.profileShowHandle, { update { it.copy(profileShowHandle = !it.profileShowHandle) } }),
            ))
        }
        SectionCard(title = "Counts", subtitle = "Public GitHub data. Unavailable counts show a dash.") {
            ProfileChoices(listOfNotNull(
                Triple("Followers", design.profileShowFollowers, { update { it.copy(profileShowFollowers = !it.profileShowFollowers) } })
                    .takeUnless { design.profileStreakReplaces == ProfileCount.FOLLOWERS },
                Triple("Following", design.profileShowFollowing, { update { it.copy(profileShowFollowing = !it.profileShowFollowing) } })
                    .takeUnless { design.profileStreakReplaces == ProfileCount.FOLLOWING },
                Triple("Repositories", design.profileShowRepos, { update { it.copy(profileShowRepos = !it.profileShowRepos) } })
                    .takeUnless { design.profileStreakReplaces == ProfileCount.REPOS },
            ))
            Spacer(Modifier.height(12.dp))
            Field("Streak replaces", hint = "Show consecutive days with contributions in one count slot.") {
                SegmentedChoice(listOf(null) + ProfileCount.entries, design.profileStreakReplaces,
                    { it?.label ?: "Off" }, { count -> update { it.copy(profileStreakReplaces = count) } })
            }
        }
        SectionCard(title = "Connections", subtitle = "Tap a selected icon on your home screen to open it. Extra links appear under the more button.") {
            val all = com.example.githubwidget.widget.FocusWidgetPainter.profileLinks(design.copy(
                profileShowGitHub = true, profileShowWebsite = true, profileShowSocials = true, profileHiddenLinks = emptySet()), data)
            val selected = com.example.githubwidget.widget.FocusWidgetPainter.profileLinks(design, data).map { it.url }.toSet()
            ProfileChoices(all.map { link ->
                Triple(com.example.githubwidget.widget.ProfileIcons.label(link.url), link.url in selected, {
                    val next = if (link.url in selected) selected - link.url else selected + link.url
                    update { it.copy(profileShowGitHub = true, profileShowWebsite = true, profileShowSocials = true,
                        profileHiddenLinks = all.map { it.url }.toSet() - next) }
                })
            })
            Spacer(Modifier.height(10.dp))
            Text(if (realData == null) "Connect your GitHub account to choose your own links." else "Website and social accounts come from your public GitHub profile.",
                style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            realData?.let { user ->
                val links = com.example.githubwidget.widget.FocusWidgetPainter.profileLinks(design, user)
                var showDestinations by remember { mutableStateOf(false) }
                if (links.isNotEmpty()) {
                    TextButton(onClick = { showDestinations = !showDestinations }) { Text(if (showDestinations) "Hide link addresses" else "View link addresses") }
                }
                if (showDestinations) {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    links.forEach { link ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(link.url) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(link.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileChoices(choices: List<Triple<String, Boolean, () -> Unit>>) {
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        choices.chunked(columns).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { (label, selected, onClick) ->
                    FilterChip(selected = selected, onClick = onClick, label = { Text(label) },
                        leadingIcon = if (selected) {{ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }} else null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                }
                if (group.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ content toggles

@Composable
private fun ShowOnWidget(design: WidgetDesign, update: ((WidgetDesign) -> WidgetDesign) -> Unit) {
    val classic = design.layout == WidgetLayout.CLASSIC
    val numbers = design.layout == WidgetLayout.NUMBERS
    val graphOnly = design.layout == WidgetLayout.GRAPH
    SectionCard(
        title = "Show on widget",
        subtitle = when {
            graphOnly -> "The “Graph only” style shows just the squares. Pick another style for text."
            numbers -> "The “Numbers” style shows your stats next to a small graph."
            else -> null
        },
        contentPadding = 18.dp,
    ) {
        AnimatedVisibility(classic) {
            Column {
                ToggleRow("Your name", design.showName, { v -> update { it.copy(showName = v) } })
                ToggleRow("Profile photo", design.showAvatar, { v -> update { it.copy(showAvatar = v) } })
                Hairline(Modifier.padding(vertical = 4.dp))
            }
        }
        ToggleRow(
            "Total contributions", design.showTotal, { v -> update { it.copy(showTotal = v) } },
            subtitle = "For the last 12 months", enabled = !graphOnly,
        )
        ToggleRow(
            "Streak", design.showStreak, { v -> update { it.copy(showStreak = v) } },
            subtitle = "Days in a row with a contribution", enabled = !graphOnly,
        )
        ToggleRow("Today's count", design.showToday, { v -> update { it.copy(showToday = v) } }, enabled = !graphOnly)
        ToggleRow(
            "Month names", design.showMonths, { v -> update { it.copy(showMonths = v) } },
            subtitle = "Shown above the graph when there's room", enabled = !numbers,
        )
    }
}
