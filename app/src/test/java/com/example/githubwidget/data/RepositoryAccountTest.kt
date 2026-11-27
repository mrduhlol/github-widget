package com.example.githubwidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import com.example.githubwidget.widget.RefreshWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryAccountTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("github_widget_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("review_inbox", Context.MODE_PRIVATE).edit().clear().commit()
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        Repository.resetForTest()
    }

    @Test
    fun initIgnoresStaleAvatarOwnedByAnotherAccount() {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        File(context.filesDir, "avatar.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        context.getSharedPreferences("github_widget_prefs", Context.MODE_PRIVATE).edit()
            .putString("username", "bob")
            .putString("avatar_username", "alice")
            .commit()

        Repository.init(context)
        assertEquals("bob", Repository.username.value)
        assertNull(Repository.avatar.value)
    }

    @Test
    fun refreshDoesNotCommitAvatarWhenAccountChangesMidFlight() = runBlocking {
        Repository.init(context)
        val aliceAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val bobAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        Repository.signIn(Lookup(UserData(Contributions("alice", 1, emptyList()), null, 1L), aliceAvatar))

        val outcome = Repository.refreshWithOutcome(
            updateWidgets = false,
            fetchContributions = { Contributions("alice", 5, emptyList()) },
            fetchProfile = {
                Repository.signIn(Lookup(UserData(Contributions("bob", 2, emptyList()), null, 2L), bobAvatar))
                Profile("alice", "Alice", "https://example.com/alice.png")
            },
            fetchAvatar = { aliceAvatar },
        )

        assertEquals(Repository.RefreshOutcome.SKIPPED, outcome)
        assertEquals("bob", Repository.username.value)
        assertEquals(bobAvatar, Repository.avatar.value)
    }

    @Test
    fun refreshDoesNotCommitWhenUserSignsOutAndBackIntoSameAccountMidFlight() = runBlocking {
        Repository.init(context)
        val oldAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val newAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        Repository.signIn(Lookup(UserData(Contributions("alice", 1, emptyList()), null, 1L), oldAvatar))

        val outcome = Repository.refreshWithOutcome(
            updateWidgets = false,
            fetchContributions = { Contributions("alice", 99, emptyList()) },
            fetchProfile = {
                Repository.signOut()
                Repository.signIn(Lookup(UserData(Contributions("alice", 2, emptyList()), null, 2L), newAvatar))
                Profile("alice", "Stale Alice", "https://example.com/old.png")
            },
            fetchAvatar = { oldAvatar },
        )

        assertEquals(Repository.RefreshOutcome.SKIPPED, outcome)
        assertEquals("alice", Repository.username.value)
        assertEquals(2, Repository.data.value?.contributions?.totalLastYear)
        assertEquals(newAvatar, Repository.avatar.value)
    }

    @Test
    fun missingAvatarOwnerTriggersRecoveryEvenWhenProfileIsFresh() = runBlocking {
        Repository.init(context)
        val freshProfile = Profile("octocat", "The Octocat", "https://example.com/octocat.png", fetchedAt = System.currentTimeMillis())
        val recoveredAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        Repository.signIn(Lookup(UserData(Contributions("octocat", 10, emptyList()), freshProfile, System.currentTimeMillis()), null))

        assertEquals(true, Repository.isStale())
        val outcome = Repository.refreshWithOutcome(
            updateWidgets = false,
            fetchContributions = { Contributions("octocat", 10, emptyList()) },
            fetchProfile = { throw AssertionError("Fresh profile should not be re-fetched") },
            fetchAvatar = { recoveredAvatar },
        )

        assertEquals(Repository.RefreshOutcome.UPDATED, outcome)
        assertEquals(recoveredAvatar, Repository.avatar.value)
    }

    @Test
    fun reviewInboxRejectsStaleResultAfterSessionChange() = runBlocking {
        Repository.init(context)
        Repository.signIn(Lookup(UserData(Contributions("alice", 1, emptyList()), null, 1L), null))

        val ok = ReviewInbox.refresh(context, "alice") {
            Repository.signOut()
            Repository.signIn(Lookup(UserData(Contributions("alice", 2, emptyList()), null, 2L), null))
            ReviewResults(1, emptyList(), System.currentTimeMillis())
        }

        assertEquals(true, ok)
        assertNull(ReviewInbox.cached(context, "alice").results)
    }

    @Test
    fun accountSnapshotCapturesConsistentUserDataAndAvatar() {
        Repository.init(context)
        val avatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        val userData = UserData(Contributions("octocat", 42, emptyList()), null, 123L)
        Repository.signIn(Lookup(userData, avatar))

        val snap = Repository.snapshot()
        assertEquals("octocat", snap.username)
        assertEquals(userData, snap.data)
        assertEquals(avatar, snap.avatar)
        assertEquals(true, snap.hasUser)
    }

    @Test
    fun delayedSignInDiskWriteCannotOverwriteNewerRefreshAndPreservesAvatar() = runBlocking {
        Repository.init(context)
        val gate = CompletableDeferred<Unit>()
        val hookEntered = CompletableDeferred<Unit>()
        Repository.onSignInPersistHook = {
            hookEntered.complete(Unit)
            gate.await()
        }

        val signInAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        val freshProfile = Profile("octocat", "Octocat", "https://example.com/o.png", fetchedAt = System.currentTimeMillis())
        val initialData = UserData(Contributions("octocat", 1, emptyList()), freshProfile, 1L)
        Repository.signIn(Lookup(initialData, signInAvatar))
        hookEntered.await()
        // The real sign-in disk save is paused before touching disk.
        assertEquals(0, context.filesDir.listFiles { _, name -> name.startsWith("snapshot_") }?.size ?: 0)

        val outcome = Repository.refreshWithOutcome(
            updateWidgets = false,
            fetchContributions = { Contributions("octocat", 77, emptyList()) },
            fetchProfile = { throw AssertionError("Fresh profile should not be re-fetched") },
            fetchAvatar = { throw AssertionError("Existing avatar should not be re-fetched") },
        )
        assertEquals(Repository.RefreshOutcome.UPDATED, outcome)

        gate.complete(Unit)
        Repository.lastSignInPersistJob?.join()

        Repository.resetForTest()
        Repository.init(context)
        assertEquals(77, Repository.data.value?.contributions?.totalLastYear)
        assertEquals(Color.YELLOW, Repository.avatar.value?.getPixel(0, 0))
    }

    @Test
    fun cancelledRefreshResetsLoadingStateToIdle() {
        Repository.init(context)
        Repository.signIn(Lookup(UserData(Contributions("octocat", 1, emptyList()), null, 1L), null))

        assertThrows(CancellationException::class.java) {
            runBlocking {
                Repository.refreshWithOutcome(
                    updateWidgets = false,
                    fetchContributions = { throw CancellationException("cancelled") },
                )
            }
        }
        assertEquals(RefreshState.Idle, Repository.refreshState.value)
    }

    @Test
    fun cancelledProfileFetchPropagatesCancellationAndResetsLoading() {
        Repository.init(context)
        Repository.signIn(Lookup(UserData(Contributions("octocat", 1, emptyList()), null, 1L), null))

        assertThrows(CancellationException::class.java) {
            runBlocking {
                Repository.refreshWithOutcome(
                    updateWidgets = false,
                    fetchContributions = { Contributions("octocat", 2, emptyList()) },
                    fetchProfile = { throw CancellationException("profile cancelled") },
                )
            }
        }
        assertEquals(RefreshState.Idle, Repository.refreshState.value)
    }

    @Test
    fun writeCacheFailureReturnsTransientFailure() = runBlocking {
        Repository.init(context)
        Repository.signIn(Lookup(UserData(Contributions("octocat", 1, emptyList()), null, 1L), null))
        Repository.lastSignInPersistJob?.join()

        var blocked: File? = null
        Repository.onBeforeSnapshotWriteHook = { cacheFile, _ ->
            blocked = cacheFile
            cacheFile.mkdirs()
        }
        try {
            val outcome = Repository.refreshWithOutcome(
                updateWidgets = false,
                fetchContributions = { Contributions("octocat", 55, emptyList()) },
                fetchProfile = { Profile("octocat", "Octocat", "https://example.com/o.png") },
                fetchAvatar = { null },
            )
            assertEquals(Repository.RefreshOutcome.TRANSIENT_FAILURE, outcome)
            assertEquals(true, Repository.refreshState.value is RefreshState.Failed)
        } finally {
            Repository.onBeforeSnapshotWriteHook = null
            blocked?.deleteRecursively()
        }
    }

    @Test
    fun failedRefreshDoesNotInvalidatePendingSignInSave() = runBlocking {
        Repository.init(context)
        val gate = CompletableDeferred<Unit>()
        val hookEntered = CompletableDeferred<Unit>()
        Repository.onSignInPersistHook = {
            hookEntered.complete(Unit)
            gate.await()
        }

        val signInAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val initialData = UserData(Contributions("octocat", 12, emptyList()), null, 1L)
        Repository.signIn(Lookup(initialData, signInAvatar))
        hookEntered.await()

        var blocked: File? = null
        Repository.onBeforeSnapshotWriteHook = { cacheFile, _ ->
            blocked = cacheFile
            cacheFile.mkdirs()
        }
        try {
            val outcome = Repository.refreshWithOutcome(
                updateWidgets = false,
                fetchContributions = { Contributions("octocat", 55, emptyList()) },
                fetchProfile = { Profile("octocat", "Octocat", "https://example.com/o.png") },
                fetchAvatar = { null },
            )
            assertEquals(Repository.RefreshOutcome.TRANSIENT_FAILURE, outcome)
        } finally {
            Repository.onBeforeSnapshotWriteHook = null
            blocked?.deleteRecursively()
        }

        gate.complete(Unit)
        Repository.lastSignInPersistJob?.join()

        Repository.resetForTest()
        Repository.init(context)
        assertEquals(12, Repository.data.value?.contributions?.totalLastYear)
        assertEquals(Color.GREEN, Repository.avatar.value?.getPixel(0, 0))
    }

    @Test
    fun avatarWriteFailureReturnsTransientFailureAndPreservesPendingSignInSave() = runBlocking {
        Repository.init(context)
        val gate = CompletableDeferred<Unit>()
        val hookEntered = CompletableDeferred<Unit>()
        Repository.onSignInPersistHook = {
            hookEntered.complete(Unit)
            gate.await()
        }

        val signInAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        val initialData = UserData(Contributions("octocat", 14, emptyList()), null, 1L)
        Repository.signIn(Lookup(initialData, signInAvatar))
        hookEntered.await()

        var blocked: File? = null
        Repository.onBeforeSnapshotWriteHook = { _, avatarFile ->
            blocked = avatarFile
            avatarFile.mkdirs()
        }
        try {
            val outcome = Repository.refreshWithOutcome(
                updateWidgets = false,
                fetchContributions = { Contributions("octocat", 88, emptyList()) },
                fetchProfile = { Profile("octocat", "Octocat", "https://example.com/o.png") },
                fetchAvatar = { signInAvatar },
            )
            assertEquals(Repository.RefreshOutcome.TRANSIENT_FAILURE, outcome)
        } finally {
            Repository.onBeforeSnapshotWriteHook = null
            blocked?.deleteRecursively()
        }

        gate.complete(Unit)
        Repository.lastSignInPersistJob?.join()

        Repository.resetForTest()
        Repository.init(context)
        assertEquals(14, Repository.data.value?.contributions?.totalLastYear)
        assertEquals(Color.CYAN, Repository.avatar.value?.getPixel(0, 0))
    }

    @Test
    fun avatarWriteFailureLeavesPreviousDiskGraphAndAvatarIntactAfterRestart() = runBlocking {
        Repository.init(context)
        val oldAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val initialData = UserData(Contributions("octocat", 19, emptyList()), null, 1L)
        Repository.signIn(Lookup(initialData, oldAvatar))
        Repository.lastSignInPersistJob?.join()

        var blocked: File? = null
        // Inject failure AFTER the new graph file has already been written to exercise partial-write cleanup.
        Repository.onAfterCacheWriteHook = { avatarFile ->
            blocked = avatarFile
            avatarFile.mkdirs()
        }
        try {
            val newAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val outcome = Repository.refreshWithOutcome(
                updateWidgets = false,
                fetchContributions = { Contributions("octocat", 99, emptyList()) },
                fetchProfile = { Profile("octocat", "Octocat", "https://example.com/o.png") },
                fetchAvatar = { newAvatar },
            )
            assertEquals(Repository.RefreshOutcome.TRANSIENT_FAILURE, outcome)
        } finally {
            Repository.onAfterCacheWriteHook = null
            blocked?.deleteRecursively()
        }

        // Reload with no pending sign-in save; BOTH previous on-disk graph and avatar must be untouched.
        Repository.resetForTest()
        Repository.init(context)
        assertEquals(19, Repository.data.value?.contributions?.totalLastYear)
        assertEquals(Color.BLUE, Repository.avatar.value?.getPixel(0, 0))
    }

    @Test
    fun refreshDoesNotCommitSnapshotIfSignedOutBeforePublication() = runBlocking {
        Repository.init(context)
        Repository.signIn(Lookup(UserData(Contributions("alice", 10, emptyList()), null, 1L), null))
        Repository.lastSignInPersistJob?.join()

        // Sign out right after the new graph file is written, before the atomic pointer commit.
        Repository.onAfterCacheWriteHook = {
            Repository.signOut()
        }
        try {
            val outcome = Repository.refreshWithOutcome(
                updateWidgets = false,
                fetchContributions = { Contributions("alice", 77, emptyList()) },
                fetchProfile = { Profile("alice", "Alice", "https://example.com/a.png") },
                fetchAvatar = { null },
            )
            assertEquals(Repository.RefreshOutcome.SKIPPED, outcome)
        } finally {
            Repository.onAfterCacheWriteHook = null
        }

        Repository.resetForTest()
        Repository.init(context)
        assertEquals("", Repository.username.value)
        assertNull(Repository.data.value)
    }

    @Test
    fun signInPersistenceFailureReportsFailureAndQueuesFollowUpRetry() = runBlocking {
        Repository.init(context)
        var blocked: File? = null
        val retryCalls = mutableListOf<Boolean>()
        Repository.onRetryRequestedHook = { replace -> retryCalls.add(replace) }
        Repository.onBeforeSnapshotWriteHook = { cacheFile, _ ->
            blocked = cacheFile
            cacheFile.mkdirs()
        }
        try {
            val signInAvatar = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
            val initialData = UserData(Contributions("octocat", 5, emptyList()), null, 1L)
            Repository.signIn(Lookup(initialData, signInAvatar))
            Repository.lastSignInPersistJob?.join()
            assertEquals(true, Repository.refreshState.value is RefreshState.Failed)
            assertEquals(listOf(true), retryCalls)
            assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, RefreshWorker.runOncePolicy(replace = true))
            assertFalse(File(context.filesDir, "avatar.png").exists())
        } finally {
            Repository.onBeforeSnapshotWriteHook = null
            blocked?.deleteRecursively()
        }
    }

    @Test
    fun refreshClassifiesTransientAndPermanentFailures() = runBlocking {
        Repository.init(context)
        Repository.signIn(Lookup(UserData(Contributions("octocat", 1, emptyList()), null, 1L), null))

        val transient = Repository.refreshWithOutcome(
            updateWidgets = false,
            fetchContributions = { throw FetchError.Offline },
        )
        assertEquals(Repository.RefreshOutcome.TRANSIENT_FAILURE, transient)

        val permanent = Repository.refreshWithOutcome(
            updateWidgets = false,
            fetchContributions = { throw FetchError.NotFound("octocat") },
        )
        assertEquals(Repository.RefreshOutcome.PERMANENT_FAILURE, permanent)
    }

    @Test
    fun signOutClearsReviewInboxAndResetsRefreshState() {
        Repository.init(context)
        context.getSharedPreferences("review_inbox", Context.MODE_PRIVATE).edit()
            .putString("username", "octocat")
            .putString("results", """{"items":[],"total_count":0,"fetchedAt":1}""")
            .commit()
        Repository.signIn(Lookup(UserData(Contributions("octocat", 1, emptyList()), null, 1L), null))

        Repository.signOut()

        assertEquals("", Repository.username.value)
        assertEquals(RefreshState.Idle, Repository.refreshState.value)
        assertNull(ReviewInbox.cached(context, "octocat").results)
    }
}
