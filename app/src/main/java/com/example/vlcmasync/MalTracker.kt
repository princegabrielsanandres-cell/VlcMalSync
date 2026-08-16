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

    /*
     * =========================================================
     * FIND THE MAL ENTRY FOR THE REQUESTED SEASON
     * =========================================================
     *
     * S1:
     *   Use the original MAL search result.
     *
     * S2+:
     *   Follow MAL's "sequel" relationships.
     *
     * Example:
     *
     * Re:Zero S1
     *      ↓ sequel
     * Re:Zero S2
     *      ↓ sequel
     * Re:Zero S3
     *
     * We follow the chain until the requested season.
     */

    private fun resolveSeason(
        token: String,
        anime: MalAnime,
        requestedSeason: Int
    ): MalAnime {

        if (requestedSeason <= 1) {
            return anime
        }

        var current =
            anime

        var currentSeason =
            1

        /*
         * Prevent an accidental infinite relationship loop.
         */
        val visited =
            mutableSetOf<Int>()

        while (
            currentSeason < requestedSeason
        ) {

            if (
                !visited.add(current.id)
            ) {
                break
            }

            val details =
                api.getAnimeDetails(
                    token,
                    current.id
                )

            /*
             * Find the direct sequel.
             */
            val sequel =
                details.relatedAnime
                    .firstOrNull {
                        it.relation.equals(
                            "sequel",
                            ignoreCase = true
                        )
                    }

            if (sequel == null) {
                /*
                 * MAL doesn't have another sequel
                 * relationship, so we can't safely guess.
                 */
                break
            }

            /*
             * Fetch the actual MAL information for
             * the sequel.
             *
             * Search by its exact MAL title so we get
             * the full MalAnime object required by the
             * existing tracker.
             */
            val candidates =
                api.search(
                    token,
                    sequel.title
                )

            val wanted =
                normalizeTitle(
                    sequel.title
                )

            val next =
                candidates.firstOrNull {
                    normalizeTitle(
                        it.title
                    ) == wanted
                }

            if (next == null) {
                break
            }

            current =
                next

            currentSeason++
        }

        /*
         * If we successfully reached the requested season,
         * return it.
         *
         * Otherwise return the original anime rather than
         * silently choosing some unrelated MAL entry.
         */
        return if (
            currentSeason == requestedSeason
        ) {
            current
        } else {
            anime
        }
    }

    private fun normalizeTitle(
        title: String
    ): String {

        return title
            .lowercase()
            .replace(
                Regex("[^a-z0-9]+"),
                ""
            )
    }

    /*
     * =========================================================
     * TRACK EPISODE
     * =========================================================
     */

    suspend fun trackEpisode(
        token: String,
        anime: MalAnime,
        season: Int,
        episode: Int
    ): TrackResult {

        /*
         * IMPORTANT:
         *
         * Resolve the season BEFORE checking MAL progress.
         *
         * This means S1 and S2 get separate MAL IDs and
         * therefore separate episode progress.
         */

        val targetAnime =
            resolveSeason(
                token = token,
                anime = anime,
                requestedSeason = season
            )

        val current =
            api.getMyListStatus(
                token,
                targetAnime.id
            )

        val date =
            today()

        /*
         * =====================================================
         * ANIME IS NOT ON THE MAL LIST YET
         * =====================================================
         */

        if (current == null) {

            val totalEpisodes =
                targetAnime.episodes

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
                animeId = targetAnime.id,
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
                animeTitle =
                    targetAnime.title,

                season =
                    season,

                episode =
                    episode,

                status =
                    status,

                watchedEpisodes =
                    episode,

                started =
                    true,

                completed =
                    isCompleted
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

        val totalEpisodes =
            targetAnime.episodes

        val shouldBeCompleted =
            totalEpisodes != null &&
            totalEpisodes > 0 &&
            newProgress >= totalEpisodes

        /*
         * Never move an already-completed anime back
         * to Watching.
         */

        val targetStatus =
            if (
                current.status == "completed"
            ) {
                "completed"

            } else if (
                shouldBeCompleted
            ) {
                "completed"

            } else {
                "watching"
            }

        /*
         * =====================================================
         * START DATE
         * =====================================================
         */

        val needsStartDate =
            current.startDate.isNullOrBlank()

        /*
         * =====================================================
         * FINISH DATE
         * =====================================================
         */

        val needsFinishDate =
            targetStatus == "completed" &&
            current.finishDate.isNullOrBlank()

        /*
         * =====================================================
         * CHECK WHETHER ANYTHING CHANGED
         * =====================================================
         */

        val progressChanged =
            newProgress !=
            current.watchedEpisodes

        val statusChanged =
            targetStatus !=
            current.status

        val dateChanged =
            needsStartDate ||
            needsFinishDate

        if (
            !progressChanged &&
            !statusChanged &&
            !dateChanged
        ) {

            return TrackResult(
                animeTitle =
                    targetAnime.title,

                season =
                    season,

                episode =
                    episode,

                status =
                    current.status,

                watchedEpisodes =
                    current.watchedEpisodes,

                started =
                    false,

                completed =
                    current.status ==
                    "completed"
            )
        }

        /*
         * =====================================================
         * UPDATE MAL
         * =====================================================
         */

        api.updateListStatus(

            token =
                token,

            animeId =
                targetAnime.id,

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

            animeTitle =
                targetAnime.title,

            season =
                season,

            episode =
                episode,

            status =
                targetStatus,

            watchedEpisodes =
                newProgress,

            started =
                needsStartDate ||
                statusChanged,

            completed =
                targetStatus ==
                "completed"
        )
    }
}
