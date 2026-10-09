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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.githubwidget.data.Repository
import com.example.githubwidget.data.SampleData
import com.example.githubwidget.design.Background
import com.example.githubwidget.design.CellShape
import com.example.githubwidget.design.Corners
import com.example.githubwidget.design.Palette
import com.example.githubwidget.design.NumberFont
import com.example.githubwidget.design.Palettes
import com.example.githubwidget.design.Range
import com.example.githubwidget.design.WidgetDesign
import com.example.githubwidget.design.WidgetLayout
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
 * The "Widget" tab: a live preview pinned at the top, and simple visual
 * choices below it. Every change is saved and applied to the home-screen
 * widget immediately — there's no Save button to forget.
 */
@Composable
fun DesignScreen(onOpenStats: () -> Unit) {
    val context = LocalContext.current
    val design by Repository.design.collectAsState()
    val realData by Repository.data.collectAsState()
    val avatar by Repository.avatar.collectAsState()
    val data = realData ?: SampleData.userData
    var previewSize by rememberSaveable { mutableStateOf(PreviewSize.WIDE) }
    var hasWidget by remember { mutableStateOf(WidgetUpdater.hasWidgets(context)) }
    var showHelp by remember { mutableStateOf(false) }
    var pickingColor by remember { mutableStateOf(false) }

    // Re-check when coming back from the home screen (the user may have just added one).
    LifecycleResumeEffect(Unit) {
        hasWidget = WidgetUpdater.hasWidgets(context)
        onPauseOrDispose { }
    }

    fun update(t: (WidgetDesign) -> WidgetDesign) = Repository.updateDesign(t)
    fun addWidget() {
        if (!WidgetPinning.request(context)) showHelp = true
    }

    Column(Modifier.fillMaxSize().background(AppTheme.colors.canvas)) {
        // ---------------------------------------------------------- pinned preview
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your widget", style = MaterialTheme.typography.headlineMedium, color = AppTheme.colors.textPrimary)
                    Text(
                        "Changes show up on your home screen right away.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            WallpaperBackdrop {
                WidgetPreview(
                    design = design,
                    data = data,
                    avatar = if (realData != null) avatar else null,
                    widthDp = previewSize.widthDp,
                    heightDp = previewSize.heightDp,
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            SegmentedChoice(
                options = PreviewSize.entries,
                selected = previewSize,
                label = { it.label },
                onSelect = { previewSize = it },
            )
        }

        // ---------------------------------------------------------- controls
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                AddWidgetCard(hasWidget, onAdd = ::addWidget, onHelp = { showHelp = true })
            }
            item {
                SectionCard(title = "Colors") {
                    PaletteRow(design, onSelect = { id -> update { it.copy(paletteId = id) } }, onCustom = { pickingColor = true })
                }
            }
            item {
                SectionCard(title = "Style") {
                    LayoutChoices(design, data, onSelect = { l -> update { it.copy(layout = l) } })
                }
            }
            item {
                SectionCard(title = "Font") {
                    Field("Typeface", hint = "Used for the widget, the numbers in Stats and the share image") {
                        NumberFontChoice(onSelect = { Repository.setNumberFont(it) })
                    }
                }
            }
            item {
                SectionCard(title = "Background") {
                    Field("Card color", hint = if (design.background == Background.AUTO) "Light or dark, following your phone's setting" else null) {
                        SegmentedChoice(Background.entries, design.background, { it.label }, { b -> update { it.copy(background = b) } })
                    }
                    Spacer(Modifier.height(20.dp))
                    OpacitySlider(design.opacity) { v -> update { it.copy(opacity = v) } }
                    Spacer(Modifier.height(16.dp))
                    Field("Corners") {
                        SegmentedChoice(Corners.entries, design.corners, { it.label }, { c -> update { it.copy(corners = c) } })
                    }
                }
            }
            item {
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
            item {
                ShowOnWidget(design, ::update)
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (realData != null) {
                        TextButton(onClick = onOpenStats) { Text("See your full activity") }
                    }
                    if (design != WidgetDesign()) {
                        TextButton(onClick = { Repository.resetDesign() }) {
                            Text("Reset to the original look", color = AppTheme.colors.textSecondary)
                        }
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
            shape = RoundedCornerShape(18.dp),
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick, onClickLabel = "Use $label colors")
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .border(selectionBorder(selected), ring, RoundedCornerShape(16.dp))
                .padding(6.dp)
                .clip(RoundedCornerShape(11.dp))
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
                        tint = if (selected) Color.White else AppTheme.colors.textSecondary,
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
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Cell(c: Color) {
    Box(Modifier.size(15.dp).clip(RoundedCornerShape(4.dp)).background(c))
}

// ------------------------------------------------------------------ style

@Composable
private fun LayoutChoices(design: WidgetDesign, data: com.example.githubwidget.data.UserData, onSelect: (WidgetLayout) -> Unit) {
    val avatar by Repository.avatar.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WidgetLayout.entries.forEach { layout ->
            val selected = design.layout == layout
            val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else AppTheme.colors.hairline, label = "layout")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(selectionBorder(selected), border, RoundedCornerShape(18.dp))
                    .clickable { onSelect(layout) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(132.dp)
                        .height(66.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colors.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    WidgetPreview(
                        design = design.copy(layout = layout, opacity = 100),
                        data = data,
                        avatar = avatar,
                        widthDp = 320f,
                        heightDp = 160f,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(layout.label, style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.textPrimary)
                    Text(layout.description, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                }
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun NumberFontChoice(onSelect: (NumberFont) -> Unit) {
    val selected by Repository.numberFont.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberFont.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { font ->
                    FontCard(font, selected = font == selected, onClick = { onSelect(font) }, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FontCard(font: NumberFont, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val family = remember(font) { numberFontFamily(context, font) }
    val shape = RoundedCornerShape(16.dp)
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
