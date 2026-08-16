package com.example.vlcmasync

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class MalAnime(
    val id: Int,
    val title: String,
    val alternativeTitles: List<String>,
    val type: String?,
    val episodes: Int?,
    val startDate: String?,
    val endDate: String?
)

data class MalRelatedAnime(
    val id: Int,
    val title: String,
    val relation: String?
)

data class MalAnimeDetails(
    val id: Int,
    val title: String,
    val episodes: Int?,
    val startDate: String?,
    val endDate: String?,
    val startSeason: String?,
    val startYear: Int?,
    val relatedAnime: List<MalRelatedAnime>
)

data class MalListStatus(
    val status: String,
    val watchedEpisodes: Int,
    val startDate: String?,
    val finishDate: String?
)

class MalApi(
    private val c: OkHttpClient = OkHttpClient()
) {

    private val api =
        "https://api.myanimelist.net/v2"

    /*
     * =========================================================
     * OAUTH TOKEN
     * =========================================================
     */

    fun token(
        cid: String,
        code: String,
        v: String,
        r: String
    ): String {

        val body =
            FormBody.Builder()
                .add("client_id", cid)
                .add("code", code)
                .add("code_verifier", v)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", r)
                .build()

        val request =
            Request.Builder()
                .url(
                    "https://myanimelist.net/v1/oauth2/token"
                )
                .post(body)
                .build()

        c.newCall(request)
            .execute()
            .use { response ->

                val text =
                    response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    error(
                        "MAL HTTP ${response.code}\n$text"
                    )
                }

                return JSONObject(text)
                    .getString("access_token")
            }
    }

    /*
     * =========================================================
     * SEARCH ANIME
     * =========================================================
     */

    fun search(
        token: String,
        query: String
    ): List<MalAnime> {

        val encoded =
            URLEncoder.encode(query, "UTF-8")

        val url =
            "$api/anime" +
            "?q=$encoded" +
            "&limit=10" +
            "&fields=" +
            "id,title,alternative_titles," +
            "num_episodes,start_date,end_date," +
            "media_type"

        val request =
            Request.Builder()
                .url(url)
                .header(
                    "Authorization",
                    "Bearer $token"
                )
                .build()

        c.newCall(request)
            .execute()
            .use { response ->

                val text =
                    response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    error(
                        "MAL HTTP ${response.code}\n" +
                        "URL: ${request.url}\n" +
                        "METHOD: ${request.method}\n" +
                        "BODY: $text"
                    )
                }

                val data =
                    JSONObject(text)
                        .getJSONArray("data")

                return (0 until data.length())
                    .map { index ->

                        val node =
                            data
                                .getJSONObject(index)
                                .getJSONObject("node")

                        val alternatives =
                            mutableListOf<String>()

                        if (
                            node.has("alternative_titles") &&
                            !node.isNull("alternative_titles")
                        ) {

                            val alt =
                                node.getJSONObject(
                                    "alternative_titles"
                                )

                            if (
                                alt.has("en") &&
                                !alt.isNull("en")
                            ) {
                                alternatives.add(
                                    alt.getString("en")
                                )
                            }

                            if (
                                alt.has("ja") &&
                                !alt.isNull("ja")
                            ) {
                                alternatives.add(
                                    alt.getString("ja")
                                )
                            }

                            if (
                                alt.has("synonyms") &&
                                !alt.isNull("synonyms")
                            ) {

                                val synonyms =
                                    alt.getJSONArray(
                                        "synonyms"
                                    )

                                for (
                                    i in 0 until synonyms.length()
                                ) {
                                    alternatives.add(
                                        synonyms.getString(i)
                                    )
                                }
                            }
                        }

                        MalAnime(
                            id =
                                node.getInt("id"),

                            title =
                                node.getString("title"),

                            alternativeTitles =
                                alternatives,

                            type =
                                node.optString(
                                    "media_type",
                                    ""
                                ).ifBlank {
                                    null
                                },

                            episodes =
                                if (
                                    node.has("num_episodes") &&
                                    !node.isNull("num_episodes")
                                ) {
                                    node.getInt(
                                        "num_episodes"
                                    )
                                } else {
                                    null
                                },

                            startDate =
                                node.optString(
                                    "start_date",
                                    ""
                                ).ifBlank {
                                    null
                                },

                            endDate =
                                node.optString(
                                    "end_date",
                                    ""
                                ).ifBlank {
                                    null
                                }
                        )
                    }
            }
    }

    /*
     * =========================================================
     * GET ANIME DETAILS + RELATED ANIME
     * =========================================================
     *
     * This is the new part.
     *
     * We ask MAL for:
     *
     * - episode count
     * - dates
     * - start season
     * - start year
     * - related anime
     *
     * MalTracker will use this later to resolve S1/S2/S3.
     */

    fun getAnimeDetails(
        token: String,
        animeId: Int
    ): MalAnimeDetails {

        val url =
            "$api/anime/$animeId" +
            "?fields=" +
            "id,title,num_episodes," +
            "start_date,end_date," +
            "start_season,start_year," +
            "related_anime"

        val request =
            Request.Builder()
                .url(url)
                .header(
                    "Authorization",
                    "Bearer $token"
                )
                .build()

        c.newCall(request)
            .execute()
            .use { response ->

                val text =
                    response.body?.string().orEmpty()

                if (!response.isSuccessful) {

                    error(
                        "MAL HTTP ${response.code}\n" +
                        "URL: ${request.url}\n" +
                        "METHOD: ${request.method}\n" +
                        "BODY: $text"
                    )
                }

                val json =
                    JSONObject(text)

                val related =
                    mutableListOf<MalRelatedAnime>()

                /*
                 * MAL may omit related_anime when there
                 * are no relationships.
                 */

                if (
                    json.has("related_anime") &&
                    !json.isNull("related_anime")
                ) {

                    val array =
                        json.getJSONArray(
                            "related_anime"
                        )

                    for (
                        i in 0 until array.length()
                    ) {

                        val item =
                            array.getJSONObject(i)

                        /*
                         * Relationship structure:
                         *
                         * {
                         *   "node": {
                         *      "id": ...,
                         *      "title": ...
                         *   },
                         *   "relation_type": "sequel"
                         * }
                         */

                        val node =
                            item.optJSONObject("node")

                        if (node != null) {

                            val id =
                                node.optInt(
                                    "id",
                                    -1
                                )

                            val title =
                                node.optString(
                                    "title",
                                    ""
                                )

                            if (
                                id > 0 &&
                                title.isNotBlank()
                            ) {

                                related.add(
                                    MalRelatedAnime(
                                        id = id,
                                        title = title,
                                        relation =
                                            item.optString(
                                                "relation_type",
                                                ""
                                            ).ifBlank {
                                                null
                                            }
                                    )
                                )
                            }
                        }
                    }
                }

                val startSeason =
                    if (
                        json.has("start_season") &&
                        !json.isNull("start_season")
                    ) {

                        json.getJSONObject(
                            "start_season"
                        )
                            .optString(
                                "season",
                                ""
                            )
                            .ifBlank {
                                null
                            }

                    } else {
                        null
                    }

                val startYear =
                    if (
                        json.has("start_year") &&
                        !json.isNull("start_year")
                    ) {
                        json.getInt(
                            "start_year"
                        )
                    } else {
                        null
                    }

                return MalAnimeDetails(

                    id =
                        json.getInt("id"),

                    title =
                        json.getString("title"),

                    episodes =
                        if (
                            json.has("num_episodes") &&
                            !json.isNull("num_episodes")
                        ) {
                            json.getInt(
                                "num_episodes"
                            )
                        } else {
                            null
                        },

                    startDate =
                        json.optString(
                            "start_date",
                            ""
                        ).ifBlank {
                            null
                        },

                    endDate =
                        json.optString(
                            "end_date",
                            ""
                        ).ifBlank {
                            null
                        },

                    startSeason =
                        startSeason,

                    startYear =
                        startYear,

                    relatedAnime =
                        related
                )
            }
    }

    /*
     * =========================================================
     * GET USER'S MAL STATUS
     * =========================================================
     */

    fun getMyListStatus(
        token: String,
        animeId: Int
    ): MalListStatus? {

        val url =
            "$api/anime/$animeId" +
            "?fields=my_list_status"

        val request =
            Request.Builder()
                .url(url)
                .header(
                    "Authorization",
                    "Bearer $token"
                )
                .build()

        c.newCall(request)
            .execute()
            .use { response ->

                val text =
                    response.body?.string().orEmpty()

                if (!response.isSuccessful) {

                    error(
                        "MAL HTTP ${response.code}\n" +
                        "URL: ${request.url}\n" +
                        "METHOD: ${request.method}\n" +
                        "BODY: $text"
                    )
                }

                val json =
                    JSONObject(text)

                if (
                    !json.has("my_list_status") ||
                    json.isNull("my_list_status")
                ) {
                    return null
                }

                val status =
                    json.getJSONObject(
                        "my_list_status"
                    )

                return MalListStatus(

                    status =
                        status.optString(
                            "status",
                            ""
                        ),

                    watchedEpisodes =
                        status.optInt(
                            "num_episodes_watched",
                            0
                        ),

                    startDate =
                        status.optString(
                            "start_date",
                            ""
                        ).ifBlank {
                            null
                        },

                    finishDate =
                        status.optString(
                            "finish_date",
                            ""
                        ).ifBlank {
                            null
                        }
                )
            }
    }

    /*
     * =========================================================
     * UPDATE MAL LIST
     * =========================================================
     */

    fun updateListStatus(
        token: String,
        animeId: Int,
        status: String? = null,
        watchedEpisodes: Int? = null,
        startDate: String? = null,
        finishDate: String? = null
    ) {

        val builder =
            FormBody.Builder()

        if (status != null) {

            builder.add(
                "status",
                status
            )
        }

        if (watchedEpisodes != null) {

            builder.add(
                "num_watched_episodes",
                watchedEpisodes.toString()
            )
        }

        if (startDate != null) {

            builder.add(
                "start_date",
                startDate
            )
        }

        if (finishDate != null) {

            builder.add(
                "finish_date",
                finishDate
            )
        }

        val request =
            Request.Builder()
                .url(
                    "$api/anime/$animeId/my_list_status"
                )
                .header(
                    "Authorization",
                    "Bearer $token"
                )
                .put(
                    builder.build()
                )
                .build()

        c.newCall(request)
            .execute()
            .use { response ->

                val text =
                    response.body?.string()
                        .orEmpty()

                if (!response.isSuccessful) {

                    error(
                        "MAL HTTP ${response.code}\n" +
                        "URL: ${request.url}\n" +
                        "METHOD: ${request.method}\n" +
                        "BODY: $text"
                    )
                }
            }
    }

    /*
     * =========================================================
     * SIMPLE UPDATE
     * =========================================================
     */

    fun update(
        token: String,
        animeId: Int,
        watchedEpisodes: Int
    ) {

        updateListStatus(
            token = token,
            animeId = animeId,
            watchedEpisodes = watchedEpisodes
        )
    }
}
