package com.example.githubwidget.widget

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import com.example.githubwidget.data.*
import com.example.githubwidget.design.*
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Shared by the launcher and the live editor; no network access while drawing. */
internal object FocusWidgetPainter {
    data class Activity(val streak: Int, val today: Int, val week: Int)

    fun activity(data: UserData, today: LocalDate = LocalDate.now()): Activity {
        val counts = data.contributions.days.associate { it.date to it.count }
        var day = if ((counts[today] ?: 0) > 0) today else today.minusDays(1)
        var streak = 0
        while ((counts[day] ?: 0) > 0) { streak++; day = day.minusDays(1) }
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return Activity(streak, counts[today] ?: 0,
            counts.filterKeys { !it.isBefore(monday) && !it.isAfter(today) }.values.sum())
    }

    fun profileLinks(design: WidgetDesign, data: UserData): List<ProfileLink> = buildList {
        if (design.profileShowGitHub) add(ProfileLink("github.com/${data.contributions.username}", "https://github.com/${data.contributions.username}"))
        if (design.profileShowWebsite) GitHubApi.webUrl(data.profile?.website)?.let {
            add(ProfileLink(it.removePrefix("https://").removePrefix("http://").trimEnd('/'), it))
        }
        if (design.profileShowSocials) data.profile?.socialLinks.orEmpty().forEach { link ->
            GitHubApi.webUrl(link.url)?.let { url ->
                add(ProfileLink(url.removePrefix("https://").removePrefix("http://").trimEnd('/'), url))
            }
        }
    }.distinctBy { it.url }.filterNot { it.url in design.profileHiddenLinks }

    const val PROFILE_LINK_SIZE = 48f
    const val PROFILE_FOOTER_OFFSET = 10f

    data class DockItem(val label: String, val url: String?)

    /** Coordinates use the renderer's content rectangle; each link gets at least 48dp. */
    fun profileDock(contentWidth: Float, contentHeight: Float, design: WidgetDesign, data: UserData): List<DockItem> {
        if (contentHeight < 120f || contentWidth < 125f) return emptyList()
        val links = profileLinks(design, data)
        val capacity = (contentWidth / PROFILE_LINK_SIZE).toInt().coerceIn(1, 6)
        return if (links.size <= capacity) links.map { DockItem(it.label, it.url) }
        else links.take(capacity - 1).map { DockItem(it.label, it.url) } + DockItem("All connections", null)
    }

    private fun compactCount(value: Int): String = when {
        value >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1fM", value / 1_000_000f)
        value >= 10_000 -> String.format(java.util.Locale.getDefault(), "%.1fk", value / 1_000f)
        else -> NumberFormat.getIntegerInstance().format(value)
    }

    private data class ProfileMetric(val value: Int?, val label: String, val spokenLabel: String)

    private fun profileMetrics(design: WidgetDesign, data: UserData): List<ProfileMetric> = buildList {
        fun addMetric(count: ProfileCount, enabled: Boolean, value: Int?, spokenLabel: String) {
            if (design.profileStreakReplaces == count) {
                val streak = activity(data).streak
                add(ProfileMetric(streak, "Streak", if (streak == 1) "day contribution streak" else "days contribution streak"))
            } else if (enabled) {
                add(ProfileMetric(value, count.label, spokenLabel))
            }
        }
        addMetric(ProfileCount.FOLLOWERS, design.profileShowFollowers, data.profile?.followers, "followers")
        addMetric(ProfileCount.FOLLOWING, design.profileShowFollowing, data.profile?.following, "following")
        addMetric(ProfileCount.REPOS, design.profileShowRepos, data.profile?.publicRepos, "public repositories")
    }

    fun accessibilityText(design: WidgetDesign, data: UserData): String {
        val username = data.contributions.username
        return when {
            design.layout == WidgetLayout.STREAK -> StreakWidgetPainter.snapshot(data, WidgetRenderer.Placeholder.NONE).description()
            design.layout.isWeeklyGoal -> "$username: ${activity(data).week} of ${design.weeklyGoal} contributions this week."
            design.layout == WidgetLayout.PROFILE -> buildList {
                if (design.profileShowName) data.profile?.displayName?.let { add(it) }
                if (design.profileShowHandle) add("@$username")
                profileMetrics(design, data).forEach { add("${it.value ?: "Unavailable"} ${it.spokenLabel}") }
                addAll(profileLinks(design, data).map { it.label })
            }.joinToString(". ")
            else -> "GitHub contributions for $username: ${data.contributions.totalLastYear} in the last year"
        } + ". " + WidgetRenderer.updatedAgo(data.fetchedAt)
    }

    fun draw(canvas: Canvas, area: RectF, scale: Float, design: WidgetDesign, colors: ColorSet,
             context: Context, font: NumberFont, data: UserData, avatar: Bitmap?,
             contentWidthDp: Float, contentHeightDp: Float, weeklyGoalPreviewDate: LocalDate? = null) {
        val state = canvas.save()
        if (design.layout == WidgetLayout.PROFILE) {
            canvas.clipRect(area.left, scale, canvas.width - scale, area.bottom + PROFILE_FOOTER_OFFSET * scale)
        } else {
            canvas.clipRect(area)
        }
        Card(canvas, area, scale, design, colors, context, font, data, avatar, contentWidthDp, contentHeightDp)
            .draw(weeklyGoalPreviewDate)
        canvas.restoreToCount(state)
    }

    private class Card(val c: Canvas, val r: RectF, val s: Float, val d: WidgetDesign,
                       val colors: ColorSet, val context: Context, val font: NumberFont,
                       val data: UserData, val avatar: Bitmap?, val contentWidthDp: Float,
                       val contentHeightDp: Float) {
        fun dp(v: Float) = v * s
        val nf = NumberFormat.getIntegerInstance()
        fun paint(size: Float, color: Int = colors.textPrimary, numeric: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(size)
            this.color = color
            typeface = NumberFont.typeface(context, if (numeric) font else if (font == NumberFont.DOT_MATRIX) NumberFont.SYSTEM else font, if (numeric) 700 else 500)
            if (d.opacity < 45) setShadowLayer(dp(3f), 0f, dp(1f), if (colors.isDark) 0x99000000.toInt() else 0x66FFFFFF)
        }
        fun line(value: String, x: Float, y: Float, width: Float, size: Float = 12f,
                 color: Int = colors.textPrimary, numeric: Boolean = false, center: Boolean = false) {
            if (width <= 0 || y > r.bottom) return
            val p = paint(size, color, numeric)
            if (numeric) while (p.measureText(value) > width && p.textSize > dp(14f)) p.textSize -= dp(1f)
            val shown = WidgetRenderer.ellipsize(value, p, width)
            val actualX = if (center) x + (width - p.measureText(shown)) / 2 else x
            c.drawText(shown, actualX, y, p)
        }
        fun draw(weeklyGoalPreviewDate: LocalDate?) = when (d.layout) {
            WidgetLayout.PROFILE -> profile()
            else -> goal(weeklyGoalPreviewDate)
        }
        private fun goal(weeklyGoalPreviewDate: LocalDate?) {
            val today = weeklyGoalPreviewDate ?: LocalDate.now()
            val dayIndex = today.dayOfWeek.value - 1
            val monday = today.minusDays(dayIndex.toLong())
            val byDate = data.contributions.days.associate { it.date to it.count }
            val days = (0..dayIndex).map { byDate[monday.plusDays(it.toLong())] ?: 0 }
            val total = days.sum()
            val target = d.weeklyGoal.coerceAtLeast(1)
            val progress = (total.toFloat() / target).coerceIn(0f, 1f)
            val percent = "${(total.toLong() * 100 / target).coerceAtMost(100)}%"
            val count = nf.format(total)
            val targetLabel = nf.format(target)
            val wide = r.width() >= dp(210f) && r.width() >= r.height() * 1.35f
            val header = r.top + dp(12f)
            ProfileIcons.draw(c, "https://github.com", r.left + dp(6f), header - dp(4f), dp(12f), colors.accent)
            line("Weekly goal", r.left + dp(17f), header, r.width() - dp(35f))
            if (r.width() >= dp(210f)) {
                val format = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
                val sunday = monday.plusDays(6)
                val end = if (monday.month == sunday.month) nf.format(sunday.dayOfMonth) else format.format(sunday)
                val dateLabel = if (weeklyGoalPreviewDate != null) "Example week" else "${format.format(monday)}–$end"
                goalEnd(dateLabel, r.right - dp(28f), header, colors.textSecondary)
            }
            if (r.height() < dp(62f)) {
                line("$count / $targetLabel", r.left, r.bottom - dp(2f), r.width(), 22f, colors.accent, numeric = true, center = true)
                return
            }
            val body = RectF(r.left, r.top + dp(20f), r.right, r.bottom - dp(17f))
            val visual = RectF(body)
            if (d.layout != WidgetLayout.WEEKLY_GOAL_METER && (wide || d.layout != WidgetLayout.WEEKLY_GOAL)) {
                val reading = RectF(body)
                if (wide) {
                    reading.right = body.left + body.width() * .47f
                    visual.left = reading.right + dp(8f)
                } else {
                    reading.bottom = body.top + body.height() * .39f
                    visual.top = reading.bottom + dp(2f)
                }
                goalReading(reading, count, targetLabel)
            }
            when (d.layout) {
                WidgetLayout.WEEKLY_GOAL_METER -> goalMeter(body, count, targetLabel, progress, percent, wide)
                WidgetLayout.WEEKLY_GOAL_WEEK -> goalWeek(visual, days)
                WidgetLayout.WEEKLY_GOAL_ASCENT -> goalAscent(visual, progress)
                else -> goalArc(visual, progress, if (wide) percent else count,
                    if (wide) "of goal" else "of $targetLabel")
            }
            val remaining = 6 - dayIndex
            val status = if (total >= target) "Goal met" else "${nf.format(target - total)} to go"
            line(status, r.left, r.bottom - dp(2f), r.width() * .56f, 10f, colors.accent)
            if (r.width() >= dp(106f)) {
                goalEnd(when (remaining) { 0 -> "Ends today"; 1 -> "1 day left"; else -> "$remaining days left" },
                    r.right, r.bottom - dp(2f), colors.textSecondary)
            }
        }

        private fun goalEnd(value: String, right: Float, baseline: Float, color: Int) {
            val p = paint(10f, color)
            c.drawText(value, right - p.measureText(value), baseline, p)
        }

        private fun goalReading(area: RectF, count: String, target: String) {
            val size = (area.height() / s * .47f).coerceIn(14f, 38f)
            val label = area.height() >= dp(43f)
            val baseline = area.centerY() + dp(if (label) 2f else size * .34f)
            val valuePaint = paint(size, colors.accent, numeric = true)
            val targetPaint = paint(size * .53f, colors.textSecondary)
            val suffix = "/ $target"
            val gap = dp(5f)
            val suffixWidth = targetPaint.measureText(suffix)
            val available = area.width() - suffixWidth - gap
            if (available > 0f) {
                valuePaint.textSize *= minOf(1f, available / valuePaint.measureText(count))
                val countWidth = valuePaint.measureText(count)
                val left = area.centerX() - (countWidth + gap + suffixWidth) / 2
                c.drawText(count, left, baseline, valuePaint)
                c.drawText(suffix, left + countWidth + gap, baseline, targetPaint)
            }
            if (label) line("Contributions", area.left, baseline + dp(14f), area.width(), 10f, colors.textSecondary, center = true)
        }

        private fun goalArc(area: RectF, progress: Float, amount: String, label: String) {
            val diameter = min(area.width(), area.height()) - dp(14f)
            if (diameter <= dp(8f)) return
            val ring = RectF(area.centerX() - diameter / 2, area.centerY() - diameter / 2,
                area.centerX() + diameter / 2, area.centerY() + diameter / 2)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                color = colors.levels[0]
            }
            if (diameter >= dp(44f)) {
                p.strokeWidth = dp(1f)
                for (tick in 0..12) {
                    val angle = Math.toRadians(135.0 + 22.5 * tick)
                    val x = cos(angle).toFloat()
                    val y = sin(angle).toFloat()
                    val inner = diameter / 2 + dp(4f)
                    val outer = inner + dp(3f)
                    c.drawLine(area.centerX() + x * inner, area.centerY() + y * inner,
                        area.centerX() + x * outer, area.centerY() + y * outer, p)
                }
            }
            p.strokeWidth = dp(4f)
            c.drawArc(ring, 135f, 270f, false, p)
            if (progress > 0f) {
                p.color = colors.accent
                c.drawArc(ring, 135f, 270f * progress, false, p)
            }
            val size = (diameter / s * .29f).coerceIn(10f, 28f)
            line(amount, ring.left + dp(4f), ring.centerY() + dp(4f), ring.width() - dp(8f), size, colors.accent, true, true)
            if (diameter >= dp(44f)) line(label, ring.left, ring.centerY() + dp(17f), ring.width(), 10f, colors.textSecondary, center = true)
        }

        private fun goalMeter(area: RectF, count: String, target: String, progress: Float, percent: String, wide: Boolean) {
            val caption = area.height() >= dp(50f)
            val bottom = area.bottom - dp(if (caption) 13f else 2f)
            val barHeight = min(dp(15f), area.height() * .22f)
            val barTop = bottom - barHeight
            goalReading(RectF(area.left, area.top, area.right, barTop - dp(3f)), count, target)
            val blocks = if (wide) 16 else 10
            val gap = dp(3f)
            val width = (area.width() - gap * (blocks - 1)) / blocks
            if (width <= 0f) return
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(blocks) { index ->
                val left = area.left + index * (width + gap)
                val right = left + width
                p.color = colors.levels[0]
                c.drawRoundRect(left, barTop, right, bottom, dp(2f), dp(2f), p)
                val fill = (progress * blocks - index).coerceIn(0f, 1f)
                if (fill > 0f) {
                    val clip = c.save()
                    c.clipRect(left, barTop, left + width * fill, bottom)
                    p.color = colors.accent
                    c.drawRoundRect(left, barTop, right, bottom, dp(2f), dp(2f), p)
                    c.restoreToCount(clip)
                }
            }
            if (caption) {
                line("0", area.left, area.bottom, area.width() / 4, 10f, colors.textSecondary)
                line("$percent of goal", area.left, area.bottom, area.width(), 10f, colors.textSecondary, center = true)
                goalEnd(target, area.right, area.bottom, colors.textSecondary)
            }
        }

        private fun goalWeek(area: RectF, days: List<Int>) {
            val column = area.width() / 7
            val captionSize = if (column >= dp(22f)) 10f else 8f
            val barTop = area.top + dp(13f)
            val barBottom = area.bottom - dp(13f)
            val barHeight = barBottom - barTop
            if (barHeight < dp(5f)) return
            val peak = (days.maxOrNull() ?: 0).coerceAtLeast(1)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            repeat(7) { index ->
                val x = area.left + index * column
                val half = min(column * .28f, dp(9f))
                val center = x + column / 2
                val count = days.getOrNull(index)
                val textColor = if (index == days.lastIndex) colors.accent else colors.textSecondary
                line(count?.let(nf::format) ?: "–", x, barTop - dp(3f), column, captionSize, textColor, center = true)
                p.color = colors.levels[0]
                c.drawRoundRect(center - half, barTop, center + half, barBottom, dp(2f), dp(2f), p)
                if (count != null && count > 0) {
                    p.color = colors.accent
                    c.drawRoundRect(center - half, barBottom - barHeight * count / peak, center + half,
                        barBottom, dp(2f), dp(2f), p)
                }
                val day = DayOfWeek.of(index + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                val label = if (column >= dp(22f)) day else day.take(2)
                line(label, x, area.bottom - dp(1f), column, captionSize, textColor, center = true)
            }
        }

        private fun goalAscent(area: RectF, progress: Float) {
            val left = area.left + dp(3f)
            val right = area.right - dp(14f)
            val bottom = area.bottom - dp(4f)
            val top = area.top + dp(12f)
            if (right <= left || bottom <= top) return
            val stepWidth = (right - left) / 4
            val stepHeight = (bottom - top) / 4
            val route = Path().apply {
                moveTo(left, bottom)
                repeat(4) { step ->
                    val x = left + stepWidth * step
                    val y = bottom - stepHeight * step
                    lineTo(x + stepWidth * .4f, y)
                    lineTo(x + stepWidth, y - stepHeight)
                }
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.levels[0]; strokeWidth = dp(1f) }
            for (y in listOf(bottom, (bottom + top) / 2, top)) c.drawLine(left, y, right, y, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(2.5f)
            p.strokeJoin = Paint.Join.ROUND
            c.drawPath(route, p)
            val x = left + (right - left) * progress
            val position = progress * 4
            val step = position.toInt()
            val slope = ((position - step - .4f) / .6f).coerceAtLeast(0f)
            val y = bottom - stepHeight * (step + slope)
            p.color = colors.accent
            val clip = c.save()
            c.clipRect(left - dp(2f), area.top, x + dp(1f), area.bottom)
            val fill = Path(route).apply { lineTo(right, bottom); close() }
            p.style = Paint.Style.FILL
            p.alpha = 52
            c.drawPath(fill, p)
            p.alpha = 255
            p.style = Paint.Style.STROKE
            c.drawPath(route, p)
            c.restoreToCount(clip)
            p.style = Paint.Style.FILL
            c.drawCircle(x, y, dp(3.5f), p)
            p.strokeWidth = dp(1.4f)
            c.drawLine(right, top, right, area.top, p)
            c.drawRect(right, area.top, right + dp(9f), area.top + dp(5f), p)
        }
        fun profile() {
            val name = data.profile?.displayName ?: data.contributions.username
            val login = "@${data.contributions.username}"
            val dock = profileDock(contentWidthDp, contentHeightDp, d, data)
            val footerTop = if (dock.isEmpty()) r.bottom else r.bottom - dp(PROFILE_LINK_SIZE - PROFILE_FOOTER_OFFSET)
            val wide = contentWidthDp >= 220f && contentHeightDp >= 125f
            val tall = r.height() > r.width() * 1.1f && r.height() >= dp(210f)
            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (colors.isDark) 0xFF30363C.toInt() else colors.border
                style = Paint.Style.STROKE
                strokeWidth = dp(.8f)
            }
            val metrics = profileMetrics(d, data)

            fun profileText(text: String, x: Float, y: Float, width: Float, size: Float,
                            typeface: Typeface, color: Int = colors.textSecondary, minSize: Float = size) {
                if (width <= 0f || y > r.bottom) return
                val p = paint(size, color).apply { this.typeface = typeface }
                val measured = p.measureText(text)
                if (measured > width && minSize < size) {
                    p.textSize = (p.textSize * width / measured).coerceAtLeast(dp(minSize))
                }
                c.drawText(WidgetRenderer.ellipsize(text, p, width), x, y, p)
            }

            fun identity(text: String, x: Float, y: Float, width: Float, size: Float, bold: Boolean = false) {
                val style = if (bold) Typeface.BOLD else Typeface.NORMAL
                val face = when (d.profileTypeface) {
                    ProfileTypeface.MONO -> Typeface.create(Typeface.MONOSPACE, style)
                    ProfileTypeface.SANS -> NumberFont.typeface(context, NumberFont.INTER, if (bold) 700 else 400)
                    ProfileTypeface.CONDENSED -> Typeface.create("sans-serif-condensed", style)
                }
                profileText(text, x, y, width, size, face, if (bold) colors.textPrimary else colors.textSecondary,
                    minSize = if (bold) size * .8f else size)
            }

            val countFace = if (font == NumberFont.SYSTEM) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                else NumberFont.typeface(context, font, 700)

            fun stats(x: Float, top: Float, width: Float, size: Float) {
                if (metrics.isEmpty() || top + dp(34f) > footerTop) return
                val column = width / metrics.size
                c.drawLine(x, top, x + width, top, rule)
                metrics.forEachIndexed { i, (value, label) ->
                    val left = x + column * i
                    profileText(value?.let(::compactCount) ?: "—", left, top + dp(20f),
                        column - dp(4f), size, countFace, colors.textPrimary)
                    profileText(label, left, top + dp(32f), column - dp(4f),
                        if (column >= dp(55f)) 10f else 9f, Typeface.DEFAULT)
                }
            }

            val bodyHeight = footerTop - r.top
            val identityLines = listOf(d.profileShowName, d.profileShowHandle).count { it }
            val hasMetrics = metrics.isNotEmpty()

            fun identityBlock(x: Float, top: Float, width: Float, size: Float, compact: Boolean = false) {
                if (d.profileShowName) identity(name, x, top + dp(size), width, size, bold = true)
                if (d.profileShowHandle) {
                    val handleSize = if (compact) 10f else 12f
                    val baseline = if (d.profileShowName) size + handleSize + 4f else handleSize + 1f
                    identity(login, x, top + dp(baseline), width, handleSize)
                }
            }

            if (wide || tall) {
                val left = r.right - r.width() * if (wide) .68f else .56f
                val right = c.width - dp(1f)
                val top = dp(1f)
                val bottom = if (wide) c.height - dp(9f) else min(footerTop, top + (right - left) * 1.65f)
                val texture = RectF(left, top, right, bottom)
                ProfileTexture.drawContours(c, texture, colors.textSecondary, colors.isDark, s)
            }

            when {
                tall -> {
                    val rowHeight = (bodyHeight / 9f).coerceIn(dp(24f), dp(38f))
                    val metricsHeight = rowHeight * metrics.size
                    val identityHeight = dp(when {
                        identityLines == 2 -> 46f
                        identityLines == 1 -> 29f
                        else -> 0f
                    })
                    val photoGap = if (identityLines > 0 || hasMetrics) dp(10f) else 0f
                    val photo = if (d.profileShowAvatar) {
                        minOf(r.width(), bodyHeight - metricsHeight - identityHeight - photoGap).coerceAtLeast(0f)
                    } else 0f
                    val blockHeight = photo + (if (photo > 0f) photoGap else 0f) + identityHeight + metricsHeight
                    var y = r.top + (bodyHeight - blockHeight).coerceAtLeast(0f) / 2f
                    if (photo > 0f) {
                        avatar(r.left, y, photo)
                        y += photo + photoGap
                    }
                    identityBlock(r.left, y, r.width(), 21f)
                    y += identityHeight
                    metrics.forEachIndexed { i, (value, label) ->
                        val top = y + rowHeight * i
                        val baseline = top + rowHeight / 2f + dp(5f)
                        val split = r.width() * .55f
                        val count = value?.let(::compactCount) ?: "—"
                        val countPaint = paint(18f, colors.textPrimary).apply { typeface = countFace }
                        val countWidth = r.width() - split
                        val shown = WidgetRenderer.ellipsize(count, countPaint, countWidth)
                        c.drawLine(r.left, top, r.right, top, rule)
                        profileText(label, r.left, baseline, split - dp(6f), 11f, Typeface.DEFAULT)
                        c.drawText(shown, r.right - countPaint.measureText(shown), baseline, countPaint)
                    }
                }
                wide -> {
                    val separateStats = hasMetrics && bodyHeight >= dp(148f)
                    val heroHeight = bodyHeight - if (separateStats) dp(46f) else 0f
                    val photoLimit = if (identityLines == 0 && (separateStats || !hasMetrics)) r.width() else r.width() * .42f
                    val photo = minOf(heroHeight - dp(6f), photoLimit, dp(138f))
                    val hasSideContent = identityLines > 0 || (hasMetrics && !separateStats)
                    val photoLeft = if (hasSideContent) r.left else r.centerX() - photo / 2f
                    val x = if (d.profileShowAvatar) r.left + photo + dp(16f) else r.left
                    val nameSize = if (bodyHeight >= dp(148f)) 26f else 22f
                    val identityHeight = dp(when {
                        identityLines == 2 -> nameSize + 20f
                        identityLines == 1 -> nameSize + 4f
                        else -> 0f
                    })
                    val stackHeight = identityHeight + if (hasMetrics && !separateStats) dp(34f) else 0f
                    val y = r.top + (heroHeight - stackHeight).coerceAtLeast(0f) / 2f
                    if (d.profileShowAvatar) avatar(photoLeft, r.top + (heroHeight - photo) / 2f, photo)
                    identityBlock(x, y, r.right - x, nameSize)
                    if (separateStats) stats(r.left, footerTop - dp(38f), r.width(), 21f)
                    else stats(x, y + identityHeight, r.right - x, 18f)
                }
                else -> {
                    val showStats = hasMetrics && bodyHeight >= dp(66f)
                    val heroHeight = bodyHeight - if (showStats) dp(40f) else 0f
                    val compact = heroHeight < dp(40f)
                    val showPhoto = d.profileShowAvatar && r.width() >= dp(120f)
                    val photo = minOf(dp(54f), r.width() * .36f, heroHeight)
                    val x = if (showPhoto) r.left + photo + dp(9f) else r.left
                    val textHeight = dp(when {
                        identityLines < 2 -> 18f
                        compact -> 28f
                        else -> 34f
                    })
                    val y = r.top + (heroHeight - textHeight).coerceAtLeast(0f) / 2f
                    if (showPhoto) {
                        val left = if (identityLines > 0) r.left else r.centerX() - photo / 2f
                        avatar(left, r.top + (heroHeight - photo) / 2f, photo)
                    }
                    identityBlock(x, y, r.right - x, if (compact) 14f else 16f, compact)
                    if (showStats) stats(r.left, footerTop - dp(34f), r.width(), 19f)
                }
            }

            if (dock.isNotEmpty()) {
                c.drawLine(r.left, footerTop + dp(8f), r.right, footerTop + dp(8f), rule)
                val slot = r.width() / dock.size
                val centerY = footerTop + dp(28f)
                dock.forEachIndexed { i, item ->
                    val centerX = r.left + slot * (i + .5f)
                    ProfileIcons.draw(c, item.url, centerX, centerY, dp(17f), colors.textSecondary)
                }
            }
        }

        fun avatar(x: Float, y: Float, size: Float) {
            val rect = RectF(x, y, x + size, y + size)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            if (avatar != null) {
                val shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val scale = maxOf(size / avatar.width, size / avatar.height)
                val matrix = Matrix().apply { setScale(scale, scale); postTranslate(x + (size - avatar.width * scale)/2, y + (size - avatar.height * scale)/2) }
                shader.setLocalMatrix(matrix); p.shader = shader
                c.drawRoundRect(rect, dp(3f), dp(3f), p)
            } else {
                p.color = colors.levels[2]; c.drawRoundRect(rect, dp(3f), dp(3f), p)
                line(data.contributions.username.take(1).uppercase(), x, y + size * .65f, size, size / s * .42f, center = true)
            }
            rect.inset(dp(.4f), dp(.4f))
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(.8f)
            p.color = colors.border
            c.drawRoundRect(rect, dp(2.6f), dp(2.6f), p)
        }
    }
}
