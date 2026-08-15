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

        val date = today()

        /*
         * =====================================================
         * ANIME IS NOT ON THE MAL LIST YET
         * =====================================================
         */

        if (current == null) {

            val totalEpisodes =
                anime.episodes

            val isCompleted =
                totalEpisodes != null &&
                totalEpisodes > 0 &&
                episode >= totalEpisodes

            val status =
                if (isCompleted) {
                    "completed"
                } else {
                    "watching"
                }

            api.updateListStatus(
                token = token,
                animeId = anime.id,
                status = status,
                watchedEpisodes = episode,
                startDate = date,
                finishDate =
                    if (isCompleted) {
                        date
                    } else {
                        null
                    }
            )

            return TrackResult(
                animeTitle = anime.title,
                season = season,
                episode = episode,
                status = status,
                watchedEpisodes = episode,
                started = true,
                completed = isCompleted
            )
        }

        /*
         * =====================================================
         * ANIME ALREADY EXISTS ON MAL
         * =====================================================
         */

        val newProgress =
            maxOf(
                current.watchedEpisodes,
                episode
            )

        /*
         * We need to determine whether the MAL entry should
         * be Watching or Completed.
         */

        val totalEpisodes =
            anime.episodes

        val shouldBeCompleted =
            totalEpisodes != null &&
            totalEpisodes > 0 &&
            newProgress >= totalEpisodes

        /*
         * If MAL says Completed already, don't move it back
         * to Watching just because another filename was tested.
         *
         * Season-specific handling will be added separately.
         */

        val targetStatus =
            if (current.status == "completed") {
                "completed"
            } else if (shouldBeCompleted) {
                "completed"
            } else {
                "watching"
            }

        /*
         * =====================================================
         * START DATE
         * =====================================================
         *
         * If the anime has no start date, add today's date.
         *
         * If it already has one, preserve it.
         */

        val needsStartDate =
            current.startDate.isNullOrBlank()

        /*
         * =====================================================
         * FINISH DATE
         * =====================================================
         *
         * Only add a finish date when the anime becomes
         * completed.
         *
         * Never overwrite an existing finish date.
         */

        val needsFinishDate =
            targetStatus == "completed" &&
            current.finishDate.isNullOrBlank()

        /*
         * =====================================================
         * DO WE ACTUALLY NEED TO UPDATE MAL?
         * =====================================================
         */

        val progressChanged =
            newProgress != current.watchedEpisodes

        val statusChanged =
            targetStatus != current.status

        val dateChanged =
            needsStartDate ||
            needsFinishDate

        /*
         * If absolutely nothing changed, don't send a
         * useless request to MAL.
         */

        if (
            !progressChanged &&
            !statusChanged &&
            !dateChanged
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
         * =====================================================
         * UPDATE MAL
         * =====================================================
         */

        api.updateListStatus(
            token = token,
            animeId = anime.id,

            status =
                if (statusChanged) {
                    targetStatus
                } else {
                    null
                },

            watchedEpisodes =
                if (progressChanged) {
                    newProgress
                } else {
                    null
                },

            startDate =
                if (needsStartDate) {
                    date
                } else {
                    null
                },

            finishDate =
                if (needsFinishDate) {
                    date
                } else {
                    null
                }
        )

        return TrackResult(
            animeTitle = anime.title,
            season = season,
            episode = episode,
            status = targetStatus,
            watchedEpisodes = newProgress,

            started =
                needsStartDate ||
                statusChanged,

            completed =
                targetStatus == "completed"
        )
    }
}
