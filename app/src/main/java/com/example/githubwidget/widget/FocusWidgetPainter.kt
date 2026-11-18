package com.example.githubwidget.widget

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import com.example.githubwidget.data.*
import com.example.githubwidget.design.*
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.min

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

    fun accessibilityText(design: WidgetDesign, data: UserData): String {
        val a = activity(data)
        val username = data.contributions.username
        return when (design.layout) {
            WidgetLayout.STREAK -> "$username: ${a.streak} day streak, ${a.today} contributions today. Best streak ${Stats.from(data.contributions).longestStreak} days."
            WidgetLayout.WEEKLY_GOAL -> "$username: ${a.week} of ${design.weeklyGoal} contributions this week."
            WidgetLayout.PROFILE -> buildList {
                if (design.profileShowName) data.profile?.displayName?.let { add(it) }
                if (design.profileShowHandle) add("@$username")
                if (design.profileShowRepos) add("${data.profile?.publicRepos ?: "Unavailable"} public repositories")
                if (design.profileShowFollowers) add("${data.profile?.followers ?: "Unavailable"} followers")
                if (design.profileShowFollowing) add("${data.profile?.following ?: "Unavailable"} following")
                addAll(profileLinks(design, data).map { it.label })
            }.joinToString(". ")
            else -> "GitHub contributions for $username: ${data.contributions.totalLastYear} in the last year"
        } + ". " + WidgetRenderer.updatedAgo(data.fetchedAt)
    }

    fun draw(canvas: Canvas, area: RectF, scale: Float, design: WidgetDesign, colors: ColorSet,
             context: Context, font: NumberFont, data: UserData, avatar: Bitmap?,
             contentWidthDp: Float, contentHeightDp: Float) {
        val state = canvas.save()
        canvas.clipRect(area)
        Card(canvas, area, scale, design, colors, context, font, data, avatar, contentWidthDp, contentHeightDp).draw()
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
        fun draw() = when (d.layout) {
            WidgetLayout.PROFILE -> profile()
            WidgetLayout.STREAK -> streak()
            else -> goal()
        }
        fun streak() {
            val a = activity(data)
            val compact = r.height() < dp(80f)
            val numberSize = if (compact) 28f else min(58f, r.height() / s * .40f)
            val base = r.top + dp(numberSize)
            val number = nf.format(a.streak)
            val numberWidth = min(r.width() * .53f, paint(numberSize, numeric = true).measureText(number) + dp(8f))
            line(number, r.left, base, numberWidth, numberSize, colors.accent, numeric = true)
            line("day streak", r.left + numberWidth, base - dp(4f), r.width() - numberWidth - dp(20f), 12f)
            if (compact) {
                line(if (a.today > 0) "${nf.format(a.today)} today" else "No contributions today", r.left, r.bottom - dp(2f), r.width(), 10f, colors.textSecondary)
            } else {
                var y = base + dp(23f)
                line(if (a.today > 0) "${nf.format(a.today)} contributions today" else "No contributions today", r.left, y, r.width(), 12f, colors.textSecondary)
                y += dp(22f)
                line("Best · ${nf.format(Stats.from(data.contributions).longestStreak)} days", r.left, y, r.width(), 12f, colors.textSecondary)
                if (r.bottom - y > dp(23f)) line(WidgetRenderer.updatedAgo(data.fetchedAt), r.left, r.bottom - dp(2f), r.width(), 9f, colors.textSecondary)
            }
        }
        fun goal() {
            val a = activity(data)
            val target = d.weeklyGoal.coerceAtLeast(1)
            val progress = (a.week.toFloat() / target).coerceIn(0f, 1f)
            val compact = r.height() < dp(100f)
            line("Weekly goal", r.left, r.top + dp(13f), r.width() - dp(22f), 12f)
            if (compact) {
                line("${nf.format(a.week)} / ${nf.format(target)}", r.left, r.top + dp(40f), r.width(), 24f, colors.accent, numeric = true)
                if (r.height() >= dp(58f)) {
                    val rect = RectF(r.left, r.bottom - dp(6f), r.right, r.bottom)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.levels[0] }
                    c.drawRoundRect(rect, dp(3f), dp(3f), p)
                    rect.right = rect.left + rect.width() * progress; p.color = colors.accent
                    c.drawRoundRect(rect, dp(3f), dp(3f), p)
                }
                return
            }
            val diameter = min(r.width() - dp(8f), r.height() - dp(53f)).coerceAtLeast(dp(32f))
            val ring = RectF(r.centerX() - diameter/2, r.top + dp(25f), r.centerX() + diameter/2, r.top + dp(25f) + diameter)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(6f); strokeCap = Paint.Cap.ROUND; color = colors.levels[0] }
            c.drawOval(ring, p); p.color = colors.accent; c.drawArc(ring, -90f, 360f * progress, false, p)
            line(nf.format(a.week), ring.left + dp(8f), ring.centerY() + dp(4f), ring.width() - dp(16f), 32f, colors.accent, true, true)
            line("of ${nf.format(target)}", ring.left + dp(8f), ring.centerY() + dp(21f), ring.width() - dp(16f), 11f, colors.textSecondary, center = true)
            line(if (a.week >= target) "Goal met" else "${nf.format(target - a.week)} to go", r.left, r.bottom - dp(2f), r.width(), 11f, colors.textSecondary, center = true)
        }
        fun profile() {
            val name = data.profile?.displayName ?: data.contributions.username
            val login = "@${data.contributions.username}"
            val dock = profileDock(contentWidthDp, contentHeightDp, d, data)
            val footerTop = if (dock.isEmpty()) r.bottom else r.bottom - dp(48f)
            val wide = contentWidthDp >= 220f && contentHeightDp >= 125f
            val tall = r.height() > r.width() * 1.1f && r.height() >= dp(210f)
            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.border
                style = Paint.Style.STROKE
                strokeWidth = dp(.8f)
            }
            val metrics = buildList {
                if (d.profileShowFollowers) add(data.profile?.followers to "Followers")
                if (d.profileShowFollowing) add(data.profile?.following to "Following")
                if (d.profileShowRepos) add(data.profile?.publicRepos to "Repos")
            }

            fun mono(text: String, x: Float, y: Float, width: Float, size: Float,
                     bold: Boolean = false, color: Int = colors.textSecondary, numeric: Boolean = false) {
                if (width <= 0f || y > r.bottom) return
                val p = paint(size, color).apply {
                    typeface = if (numeric && font != NumberFont.SYSTEM) NumberFont.typeface(context, font, 700)
                    else Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
                }
                c.drawText(WidgetRenderer.ellipsize(text, p, width), x, y, p)
            }

            fun stats(x: Float, top: Float, width: Float) {
                if (metrics.isEmpty() || top + dp(28f) > footerTop) return
                val column = width / metrics.size
                metrics.forEachIndexed { i, (value, label) ->
                    val left = x + column * i
                    mono(value?.let { compactCount(it) } ?: "—", left, top + dp(14f),
                        column - dp(3f), 17f, true, colors.textPrimary, numeric = true)
                    mono(label, left, top + dp(27f), column - dp(2f), if (column >= dp(55f)) 10f else 8f)
                }
            }

            when {
                wide -> {
                    val headerHeight = min(dp(84f), footerTop - r.top - dp(7f))
                    val y = r.top + (footerTop - r.top - headerHeight) / 2
                    var x = r.left
                    if (d.profileShowAvatar) {
                        avatar(x, y, headerHeight)
                        x += headerHeight + dp(16f)
                    }
                    val width = r.right - x - dp(22f)
                    var baseline = y + dp(19f)
                    if (d.profileShowName) {
                        mono(name, x, baseline, width, 21f, true, colors.textPrimary)
                        baseline += dp(15f)
                    }
                    if (d.profileShowHandle) mono(login, x, baseline, width, 11f)
                    stats(x, y + headerHeight - dp(28f), min(r.right - x, dp(206f)))
                }
                tall -> {
                    val photo = min(dp(102f), r.width())
                    var y = r.top
                    if (d.profileShowAvatar) {
                        avatar(r.left, y, photo)
                        y += photo + dp(10f)
                    }
                    if (d.profileShowName) {
                        y += dp(18f)
                        mono(name, r.left, y, r.width(), 19f, true, colors.textPrimary)
                        y += dp(5f)
                    }
                    if (d.profileShowHandle) {
                        y += dp(13f)
                        mono(login, r.left, y, r.width(), 11f)
                    }
                    stats(r.left, y + dp(16f), r.width())
                }
                else -> {
                    val photo = min(dp(38f), r.height())
                    var x = r.left
                    if (d.profileShowAvatar && r.width() >= dp(120f)) {
                        avatar(x, r.top, photo)
                        x += photo + dp(9f)
                    }
                    var y = r.top + dp(13f)
                    if (d.profileShowName) {
                        mono(name, x, y, r.right - x - dp(16f), 14f, true, colors.textPrimary)
                        y += dp(14f)
                    }
                    if (d.profileShowHandle) mono(login, x, y, r.right - x, 10f)
                    if (footerTop - r.top >= dp(66f)) stats(r.left, footerTop - dp(28f), r.width())
                }
            }

            if (dock.isNotEmpty()) {
                c.drawLine(r.left, footerTop + dp(4f), r.right, footerTop + dp(4f), rule)
                val slot = dp(PROFILE_LINK_SIZE)
                val centerY = footerTop + dp(27f)
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
        }
    }
}
