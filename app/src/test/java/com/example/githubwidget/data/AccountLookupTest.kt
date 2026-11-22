package com.example.githubwidget.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountLookupTest {
    private val calendar = Contributions("octocat", 0, emptyList())

    @Test fun acceptsHandlesAndPastedGitHubLinks() {
        for (input in listOf(" octocat ", "@octocat", "github.com/octocat/",
            "HTTPS://WWW.GitHub.com/octocat#overview", "https://github.com/octocat?tab=repositories")) {
            assertEquals(input, "octocat", GitHubApi.cleanUsername(input))
        }
    }

    @Test fun rejectsUnrelatedLinksAndMissingOwners() {
        for (input in listOf("https://example.org/octocat", "https://github.com/", "@@octocat")) {
            assertFalse(input, GitHubApi.isValidUsername(GitHubApi.cleanUsername(input)))
        }
    }

    @Test fun contributionFailureReturnsTheOriginalError() = runBlocking {
        val error = FetchError.NotFound("octocat")
        val result = Repository.lookup("octocat", { throw error }, { throw FetchError.RateLimited })
        assertSame(error, result.exceptionOrNull())
    }

    @Test fun optionalProfileFailureStillAllowsSetup() = runBlocking {
        val result = Repository.lookup("@octocat", { calendar }, { throw FetchError.RateLimited })
        val lookup = result.getOrThrow()
        assertEquals(calendar, lookup.data.contributions)
        assertNull(lookup.data.profile)
        assertNull(lookup.avatar)
    }

    @Test fun cancelledLookupPropagatesCancellation() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                Repository.lookup("octocat", { throw CancellationException() }, { throw FetchError.RateLimited })
            }
        }
    }
}
