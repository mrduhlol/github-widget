package com.example.githubwidget.data

import java.time.LocalDate

/** One square of the contribution graph. [level] is GitHub's 0..4 intensity. */
data class Day(val date: LocalDate, val count: Int, val level: Int)

/** The last ~year of public contributions for one user, oldest day first. */
data class Contributions(
    val username: String,
    val totalLastYear: Int,
    val days: List<Day>,
) {
    val today: Day? get() = days.lastOrNull()
}

/** Public GitHub profile info shown next to the graph. */
data class Profile(
    val login: String,
    val name: String?,
    val avatarUrl: String,
) {
    /** Real name when the user has set one, otherwise the login. */
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: login
}

/** Snapshot the UI and the widget render from. */
data class UserData(
    val contributions: Contributions,
    val profile: Profile?,
    val fetchedAt: Long,
)

/** Friendly, user-facing failures. [message] is always safe to show as-is. */
sealed class FetchError(override val message: String) : Exception(message) {
    class NotFound(username: String) :
        FetchError("We couldn't find a GitHub account called “$username”. Check the spelling and try again.")

    object Offline : FetchError("You're offline. Connect to the internet and try again.")

    object RateLimited : FetchError("GitHub is busy right now. Please try again in a few minutes.")

    class Server(code: Int) : FetchError("GitHub data is temporarily unavailable (error $code). Please try again soon.")
}
