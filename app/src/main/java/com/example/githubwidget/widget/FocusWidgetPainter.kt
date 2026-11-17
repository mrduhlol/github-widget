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

    fun accessibilityText(design: WidgetDesign, data: UserData): String {
        val a = activity(data)
        val username = data.contributions.username
        return when (design.layout) {
            WidgetLayout.STREAK -> "$username: ${a.streak} day streak, ${a.today} contributions today. Best streak ${Stats.from(data.contributions).longestStreak} days."
            WidgetLayout.WEEKLY_GOAL -> "$username: ${a.week} of ${design.weeklyGoal} contributions this week."
            WidgetLayout.PROFILE -> buildList {
                if (design.profileShowName) data.profile?.displayName?.let { add(it) }
                add("@$username")
                if (design.profileShowFollowers) add("${data.profile?.followers ?: "Unavailable"} followers")
                if (design.profileShowFollowing) add("${data.profile?.following ?: "Unavailable"} following")
                addAll(profileLinks(design, data).map { it.label })
            }.joinToString(". ")
            else -> "GitHub contributions for $username: ${data.contributions.totalLastYear} in the last year"
        } + ". " + WidgetRenderer.updatedAgo(data.fetchedAt)
    }

    fun draw(canvas: Canvas, area: RectF, scale: Float, design: WidgetDesign, colors: ColorSet,
             context: Context, font: NumberFont, data: UserData, avatar: Bitmap?) {
        val state = canvas.save()
        canvas.clipRect(area)
        Card(canvas, area, scale, design, colors, context, font, data, avatar).draw()
        canvas.restoreToCount(state)
    }

    private class Card(val c: Canvas, val r: RectF, val s: Float, val d: WidgetDesign,
                       val colors: ColorSet, val context: Context, val font: NumberFont,
                       val data: UserData, val avatar: Bitmap?) {
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
            val portrait = r.height() > r.width() * 1.1f
            val name = data.profile?.displayName ?: data.contributions.username
            val login = "@${data.contributions.username}"
            val roomy = r.height() >= dp(100f)
            var y: Float
            if (portrait && roomy) {
                val avSize = min(dp(76f), r.width() * .60f)
                y = r.top + dp(4f)
                if (d.profileShowAvatar) { avatar(r.centerX() - avSize/2, y, avSize); y += avSize + dp(19f) }
                else y += dp(18f)
                if (d.profileShowName) { line(name, r.left, y, r.width(), 16f, center = true); y += dp(21f) }
                line(login, r.left, y, r.width(), 12f, colors.textSecondary, center = true)
                y += dp(28f)
            } else {
                val avSize = if (roomy) dp(52f) else min(dp(34f), r.height())
                var x = r.left
                if (d.profileShowAvatar && roomy && r.width() >= dp(160f)) { avatar(x, r.top, avSize); x += avSize + dp(12f) }
                val available = r.right - x - dp(24f)
                if (d.profileShowName && roomy) {
                    line(name, x, r.top + dp(21f), available, 17f)
                    line(login, x, r.top + dp(42f), available, 12f, colors.textSecondary)
                } else line(login, x, r.top + dp(21f), available, 13f)
                y = r.top + if (roomy) dp(68f) else dp(55f)
            }
            val counts = buildList {
                if (d.profileShowFollowers) add(data.profile?.followers to "followers")
                if (d.profileShowFollowing) add(data.profile?.following to "following")
            }
            if (counts.isNotEmpty() && r.bottom - y >= dp(32f)) {
                val column = r.width() / counts.size
                counts.forEachIndexed { index, (value, label) ->
                    val x = r.left + column * index
                    line(value?.let { nf.format(it) } ?: "—", x, y, column - dp(4f), 22f, colors.accent, numeric = true, center = portrait)
                    line(label, x, y + dp(16f), column - dp(4f), 10f, colors.textSecondary, center = portrait)
                }
                y += dp(38f)
            }
            val links = profileLinks(d, data)
            val slots = ((r.bottom - y) / dp(17f)).toInt().coerceAtLeast(0)
            links.take(slots).forEachIndexed { i, link ->
                val label = if (i == slots - 1 && links.size > slots) "+${links.size - i} links · open app" else link.label
                line(label, r.left, y, r.width(), 10.5f, colors.textSecondary, center = portrait); y += dp(17f)
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
                c.drawOval(rect, p)
            } else {
                p.color = colors.levels[2]; c.drawOval(rect, p)
                line(data.contributions.username.take(1).uppercase(), x, y + size * .65f, size, size / s * .42f, center = true)
            }
        }
    }
}
