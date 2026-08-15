package com.example.vlcmasync

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class MalAnime(
    val id: Int,
    val title: String
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
                    response.body
                        ?.string()
                        .orEmpty()

                if (!response.isSuccessful) {
                    error(text)
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
            URLEncoder.encode(
                query,
                "UTF-8"
            )

        val url =
            "$api/anime" +
            "?q=$encoded" +
            "&limit=10" +
            "&fields=id,title"

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
                    response.body
                        ?.string()
                        .orEmpty()

                if (!response.isSuccessful) {
                    error(text)
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

                        MalAnime(
                            id = node.getInt("id"),
                            title = node.getString("title")
                        )
                    }
            }
    }

    /*
     * =========================================================
     * GET USER'S MAL LIST STATUS
     * =========================================================
     *
     * Returns null if the anime is not currently on the
     * user's MAL list.
     */

    fun getMyListStatus(
        token: String,
        animeId: Int
    ): MalListStatus? {

        val request =
            Request.Builder()
                .url(
                    "$api/anime/$animeId/my_list_status"
                )
                .header(
                    "Authorization",
                    "Bearer $token"
                )
                .build()

        c.newCall(request)
            .execute()
            .use { response ->

                val text =
                    response.body
                        ?.string()
                        .orEmpty()

                /*
                 * MAL returns an error when the anime is not
                 * currently in the user's list.
                 */
                if (response.code == 404) {
                    return null
                }

                if (!response.isSuccessful) {
                    error(text)
                }

                val json =
                    JSONObject(text)

                return MalListStatus(
                    status =
                        json.optString(
                            "status",
                            ""
                        ),

                    watchedEpisodes =
                        json.optInt(
                            "num_episodes_watched",
                            0
                        ),

                    startDate =
                        json.optString(
                            "start_date",
                            ""
                        ).ifBlank {
                            null
                        },

                    finishDate =
                        json.optString(
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
     * UPDATE USER'S MAL LIST
     * =========================================================
     *
     * Every parameter except animeId/token is optional.
     *
     * Example:
     *
     * updateListStatus(
     *     token,
     *     12345,
     *     status = "watching",
     *     watchedEpisodes = 1,
     *     startDate = "2026-08-15"
     * )
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
                    response.body
                        ?.string()
                        .orEmpty()

                if (!response.isSuccessful) {
                    error(text)
                }
            }
    }

    /*
     * =========================================================
     * EXISTING SIMPLE UPDATE
     * =========================================================
     *
     * Kept so your existing MainActivity code continues
     * working.
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
