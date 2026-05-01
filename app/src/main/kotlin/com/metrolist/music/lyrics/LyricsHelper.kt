/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import com.metrolist.music.constants.LyricsProviderOrderKey
import com.metrolist.music.constants.PreferredLyricsProvider
import com.metrolist.music.constants.PreferredLyricsProviderKey
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

private const val MAX_LYRICS_FETCH_MS = 30000L
private const val PROVIDER_NONE = ""

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    val preferred =
        context.dataStore.data
            .map { preferences ->
                Timber.tag("LyricsHelper")
                    .d("Current Lyrics Order: ${preferences[LyricsProviderOrderKey] ?: ""}")
                resolveLyricsProviders(preferences)
            }.distinctUntilChanged()

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    private fun CoroutineScope.launchProviderJob(
        provider: LyricsProvider,
        index: Int,
        channel: Channel<Pair<Int, LyricsWithProvider?>>,
        mediaMetadata: MediaMetadata,
        cleanedTitle: String,
    ): Job = launch {
        try {
            val providerResult = provider.getLyrics(
                context,
                mediaMetadata.id,
                cleanedTitle,
                mediaMetadata.artists.joinToString { it.name },
                mediaMetadata.duration,
                mediaMetadata.album?.title,
            )
            if (providerResult.isSuccess) {
                Timber.tag("LyricsHelper").i("Got lyrics from ${provider.name}")
                val filtered = LyricsUtils.filterLyricsCreditLines(providerResult.getOrNull()!!)
                channel.send(Pair(index, LyricsWithProvider(filtered, provider.name)))
            } else {
                Timber.tag("LyricsHelper")
                    .w("${provider.name} failed: ${providerResult.exceptionOrNull()?.message}")
                channel.send(Pair(index, null))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("LyricsHelper").w("${provider.name} threw: ${e.message}")
            channel.send(Pair(index, null))
        }
    }



    private suspend fun raceProviders(
        providers: List<LyricsProvider>,
        mediaMetadata: MediaMetadata,
        cleanedTitle: String,
    ): LyricsWithProvider? {
        if (providers.isEmpty()) return null
        return coroutineScope {
            val deferreds: MutableList<Deferred<Pair<LyricsProvider, String>?>> = providers.map { provider ->
                async {
                    try {
                        val result = withTimeoutOrNull(10_000L) {
                            provider.getLyrics(
                                context,
                                mediaMetadata.id,
                                cleanedTitle,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            )
                        }
                        if (result?.isSuccess == true) {
                            val filtered = LyricsUtils.filterLyricsCreditLines(result.getOrNull()!!)
                            provider to filtered
                        } else null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("LyricsHelper").w("${provider.name} threw: ${e.message}")
                        null
                    }
                }
            }.toMutableList()

            val results = mutableListOf<Pair<LyricsProvider, String>>()

            while (deferreds.isNotEmpty()) {
                val (index, result) = select {
                    deferreds.forEachIndexed { i, d ->
                        d.onAwait { i to it }
                    }
                }
                deferreds.removeAt(index)
                if (result != null) {
                    results.add(result)
                    // If we already have a synced result, no need to wait for the rest
                    if (lyricsTextLooksKaraoke(result.second)) {
                        deferreds.forEach { it.cancel() }
                        break
                    }
                }
            }

            // Prefer synced over plain, otherwise take first result
            val best = results.firstOrNull { lyricsTextLooksKaraoke(it.second) }
                ?: results.firstOrNull { lyricsTextLooksSynced(it.second) }
                ?: results.firstOrNull()

            best?.let { LyricsWithProvider(it.second, it.first.name) }
        }
    }

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) return LyricsWithProvider(cached.lyrics, cached.providerName)

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (_: Exception) { true }

        if (!isNetworkAvailable) return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)

        val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
        val preferences = context.dataStore.data.first()
        val enabledProviders = resolveLyricsProviders(preferences).filter { it.isEnabled(context) }

        val youtubeProviders = enabledProviders.filter {
            it is YouTubeLyricsProvider || it is YouTubeSubtitleLyricsProvider
        }
        val primaryProviders = enabledProviders.filter {
            it !is YouTubeLyricsProvider && it !is YouTubeSubtitleLyricsProvider
        }

        return withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            coroutineScope {
                // Try all non-YouTube providers in parallel first
                val winner = raceProviders(primaryProviders, mediaMetadata, cleanedTitle)

                if (winner != null) return@coroutineScope winner

                // Fall back to YouTube providers sequentially
                for (provider in youtubeProviders) {
                    val result = runCatching {
                        withTimeoutOrNull(10_000L) {
                            provider.getLyrics(
                                context,
                                mediaMetadata.id,
                                cleanedTitle,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            )
                        }
                    }.getOrNull()
                    if (result?.isSuccess == true) {
                        return@coroutineScope LyricsWithProvider(
                            LyricsUtils.filterLyricsCreditLines(result.getOrNull()!!),
                            provider.name
                        )
                    }
                }
                LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
            }
        } ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        // Check network connectivity before making network requests
        // Use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // If network check fails, try to proceed anyway
            true
        }

        if (!isNetworkAvailable) return // Still try to proceed in case of false negative

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(songTitle)
            val allProviders = context.dataStore.data
                .map { preferences -> resolveLyricsProviders(preferences) }
                .first()
            val enabledProviders = allProviders.filter { it.isEnabled(context) }

            // Separate LyricsPlus from other providers so it only runs conditionally
            val otherProviders = enabledProviders.filter { it.name != "LyricsPlus" }
            val lyricsPlusProvider = enabledProviders.find { it.name == "LyricsPlus" }

            val callbackMutex = Mutex()

            // Step 1: Fetch from all non-LyricsPlus providers concurrently
            val otherJobs = otherProviders.map { provider ->
                launch {
                    try {
                        provider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(provider.name, filteredLyrics)
                            launch {
                                callbackMutex.withLock {
                                    allResult += result
                                    callback(result)
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Catch network-related exceptions like UnresolvedAddressException
                        reportException(e)
                    }
                }
            }
            otherJobs.joinAll()

            // Step 2: Only fetch from LyricsPlus if other providers combined returned <= 2 lyrics texts
            val otherLyricsCount = allResult.count { it.providerName != "LyricsPlus" }
            if (lyricsPlusProvider != null && otherLyricsCount <= 2) {
                launch {
                    try {
                        lyricsPlusProvider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                            val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                            val result = LyricsResult(lyricsPlusProvider.name, filteredLyrics)
                            launch {
                                callbackMutex.withLock {
                                    allResult += result
                                    callback(result)
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }.join()
            }

            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    private fun resolveLyricsProviders(preferences: androidx.datastore.preferences.core.Preferences): List<LyricsProvider> {
        val providerOrder = preferences[LyricsProviderOrderKey].orEmpty()
        if (providerOrder.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(providerOrder)
        }
        return when (preferences[PreferredLyricsProviderKey].toEnum(PreferredLyricsProvider.LRCLIB)) {
            PreferredLyricsProvider.LRCLIB -> listOf(
                LrcLibLyricsProvider,
                BetterLyricsProvider,
                PaxsenixLyricsProvider,
                KuGouLyricsProvider,
                LyricsPlusProvider,
                YouTubeSubtitleLyricsProvider,
                YouTubeLyricsProvider,
            )
            PreferredLyricsProvider.KUGOU -> listOf(
                KuGouLyricsProvider,
                BetterLyricsProvider,
                PaxsenixLyricsProvider,
                LrcLibLyricsProvider,
                LyricsPlusProvider,
                YouTubeSubtitleLyricsProvider,
                YouTubeLyricsProvider,
            )
            PreferredLyricsProvider.BETTER_LYRICS -> listOf(
                BetterLyricsProvider,
                PaxsenixLyricsProvider,
                LrcLibLyricsProvider,
                KuGouLyricsProvider,
                LyricsPlusProvider,
                YouTubeSubtitleLyricsProvider,
                YouTubeLyricsProvider,
            )
            PreferredLyricsProvider.PAXSENIX -> listOf(
                PaxsenixLyricsProvider,
                BetterLyricsProvider,
                LrcLibLyricsProvider,
                KuGouLyricsProvider,
                LyricsPlusProvider,
                YouTubeSubtitleLyricsProvider,
                YouTubeLyricsProvider,
            )
            PreferredLyricsProvider.LYRICSPLUS -> listOf(
                LyricsPlusProvider,
                BetterLyricsProvider,
                PaxsenixLyricsProvider,
                LrcLibLyricsProvider,
                KuGouLyricsProvider,
                YouTubeSubtitleLyricsProvider,
                YouTubeLyricsProvider,
            )
        }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
