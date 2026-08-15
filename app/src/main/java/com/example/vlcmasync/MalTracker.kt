package com.example.vlcmasync

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MalTracker(
    private val api: MalApi
) {

    data class TrackResult(
        val animeTitle: String,
        val season: Int,
        val episode: Int,
        val status: String,
        val watchedEpisodes: Int,
        val started: Boolean,
        val completed: Boolean
    )

    private fun today(): String {
        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())
    }

    suspend fun trackEpisode(
        token: String,
        anime: MalAnime,
        season: Int,
        episode: Int
    ): TrackResult {

        val current =
            api.getMyListStatus(
                token,
                anime.id
            )

        val date =
            today()

        /*
         * Anime isn't on MAL yet.
         *
         * Start watching it.
         */
        if (current == null) {

            api.updateListStatus(
                token = token,
                animeId = anime.id,
                status = "watching",
                watchedEpisodes = episode,
                startDate = date
            )

            return TrackResult(
                animeTitle = anime.title,
                season = season,
                episode = episode,
                status = "watching",
                watchedEpisodes = episode,
                started = true,
                completed = false
            )
        }

        /*
         * Never move MAL progress backwards.
         */
        val newProgress =
            maxOf(
                current.watchedEpisodes,
                episode
            )

        /*
         * If MAL already has this episode or a later
         * episode, there is nothing to reduce.
         */
        if (
            newProgress ==
            current.watchedEpisodes
        ) {

            return TrackResult(
                animeTitle = anime.title,
                season = season,
                episode = episode,
                status = current.status,
                watchedEpisodes =
                    current.watchedEpisodes,
                started = false,
                completed =
                    current.status == "completed"
            )
        }

        /*
         * Update progress while preserving the
         * existing MAL status and start date.
         */
        api.updateListStatus(
            token = token,
            animeId = anime.id,
            watchedEpisodes = newProgress
        )

        return TrackResult(
            animeTitle = anime.title,
            season = season,
            episode = episode,
            status = current.status,
            watchedEpisodes = newProgress,
            started = false,
            completed =
                current.status == "completed"
        )
    }
}
