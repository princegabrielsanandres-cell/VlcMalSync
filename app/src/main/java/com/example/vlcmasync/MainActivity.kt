package com.example.vlcmasync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.util.Base64

class MainActivity : Activity() {

    companion object {

        private const val CLIENT_ID =
            "9f0d8ade5b8a7922da3dafada0bc2153"

        private const val REDIRECT =
            "malvlcsync://oauth"

        private const val PREFS =
            "vlc_mal_sync"

        private const val PREF_VERIFIER =
            "pkce_verifier"

        private const val PREF_TOKEN =
            "token"
    }

    private val api =
        MalApi(OkHttpClient())

    private val tracker by lazy {
        MalTracker(api)
    }

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main
        )

    private lateinit var status: TextView

    private val prefs by lazy {
        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        )
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        buildUi()

        handleIntent(intent)
    }

    /*
     * =========================================================
     * UI
     * =========================================================
     */

    private fun buildUi() {

        val box =
            LinearLayout(this)

        box.orientation =
            LinearLayout.VERTICAL

        box.setPadding(
            32,
            32,
            32,
            32
        )

        val title =
            TextView(this)

        title.text =
            "VLC → MAL Sync"

        title.textSize =
            26f

        val connection =
            TextView(this)

        connection.text =
            if (getToken() != null) {
                "MAL connected ✓"
            } else {
                "MAL not connected"
            }

        connection.textSize =
            16f

        val login =
            Button(this)

        login.text =
            "Connect MyAnimeList"

        login.setOnClickListener {
            loginToMal()
        }

        /*
         * Temporary filename input.
         *
         * Later this will be supplied automatically by VLC.
         */
        val filename =
            EditText(this)

        filename.hint =
            "Anime Title S1E01.mkv"

        filename.setSingleLine(true)

        val sync =
            Button(this)

        sync.text =
            "Test Automatic MAL Sync"

        sync.setOnClickListener {

            syncFilename(
                filename.text
                    .toString()
            )
        }

        status =
            TextView(this)

        status.text =
            "Waiting..."

        status.textSize =
            16f

        box.addView(title)
        box.addView(connection)
        box.addView(login)

        box.addView(filename)
        box.addView(sync)

        box.addView(status)

        setContentView(box)
    }

    /*
     * =========================================================
     * OAUTH
     * =========================================================
     */

    private fun loginToMal() {

        val bytes =
            ByteArray(32)

        SecureRandom()
            .nextBytes(bytes)

        val verifier =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes)

        prefs.edit()
            .putString(
                PREF_VERIFIER,
                verifier
            )
            .remove(PREF_TOKEN)
            .apply()

        val uri =
            Uri.Builder()
                .scheme("https")
                .authority("myanimelist.net")
                .path(
                    "/v1/oauth2/authorize"
                )
                .appendQueryParameter(
                    "response_type",
                    "code"
                )
                .appendQueryParameter(
                    "client_id",
                    CLIENT_ID
                )
                .appendQueryParameter(
                    "redirect_uri",
                    REDIRECT
                )
                .appendQueryParameter(
                    "code_challenge",
                    verifier
                )
                .appendQueryParameter(
                    "code_challenge_method",
                    "plain"
                )
                .build()

        status.text =
            "Opening MyAnimeList..."

        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                uri
            )
        )
    }

    override fun onNewIntent(
        intent: Intent?
    ) {
        super.onNewIntent(intent)

        if (intent != null) {
            setIntent(intent)
            handleIntent(intent)
        }
    }

    private fun handleIntent(
        intent: Intent?
    ) {

        val data =
            intent?.data
                ?: return

        if (
            data.scheme != "malvlcsync" ||
            data.host != "oauth"
        ) {
            return
        }

        val error =
            data.getQueryParameter("error")

        if (!error.isNullOrBlank()) {

            val description =
                data.getQueryParameter(
                    "error_description"
                ) ?: error

            status.text =
                "MAL authorization failed:\n$description"

            return
        }

        val code =
            data.getQueryParameter("code")

        if (code.isNullOrBlank()) {

            status.text =
                "MAL login failed:\n" +
                "No authorization code."

            return
        }

        val verifier =
            prefs.getString(
                PREF_VERIFIER,
                null
            )

        if (verifier.isNullOrBlank()) {

            status.text =
                "MAL login failed:\n" +
                "Verifier missing."

            return
        }

        status.text =
            "Completing MAL login..."

        prefs.edit()
            .remove(PREF_VERIFIER)
            .apply()

        scope.launch(Dispatchers.IO) {

            try {

                val token =
                    api.token(
                        CLIENT_ID,
                        code,
                        verifier,
                        REDIRECT
                    )

                prefs.edit()
                    .putString(
                        PREF_TOKEN,
                        token
                    )
                    .apply()

                withContext(
                    Dispatchers.Main
                ) {

                    status.text =
                        "MAL connected ✓"
                }

            } catch (e: Exception) {

                withContext(
                    Dispatchers.Main
                ) {

                    status.text =
                        "Login failed:\n" +
                        e.message
                }
            }
        }
    }

    /*
     * =========================================================
     * TOKEN
     * =========================================================
     */

    private fun getToken(): String? {

        return prefs.getString(
            PREF_TOKEN,
            null
        )
    }

    private fun requireToken(): String? {

        val token =
            getToken()

        if (token == null) {

            status.text =
                "Connect MAL first."
        }

        return token
    }

    /*
     * =========================================================
     * AUTOMATIC SYNC TEST
     * =========================================================
     */

    private fun syncFilename(
        filename: String
    ) {

        val token =
            requireToken()
                ?: return

        if (filename.isBlank()) {

            status.text =
                "Enter a filename."

            return
        }

        /*
         * Use the existing AnimeParser.kt.
         */
        val parsed =
            AnimeParser.parse(filename)

        if (parsed == null) {

            status.text =
                "Ignored.\n\n" +
                "Filename format must be:\n" +
                "Anime Title S1E01.mkv"

            return
        }

        status.text =
            "Detected:\n" +
            "${parsed.title}\n" +
            "Season: " +
            (parsed.season ?: "?") +
            "\nEpisode: " +
            parsed.episode +
            "\n\nSearching MAL..."

        scope.launch(Dispatchers.IO) {

            try {

                /*
                 * Search MAL using the title extracted
                 * from the filename.
                 */
                val results =
                    api.search(
                        token,
                        parsed.title
                    )

                if (results.isEmpty()) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        status.text =
                            "Ignored.\n\n" +
                            "No MAL results for:\n" +
                            parsed.title
                    }

                    return@launch
                }

                /*
                 * Find an exact title or alternative-title
                 * match instead of blindly selecting result #1.
                 */
                val wanted =
                    normalizeTitle(
                        parsed.title
                    )

                val anime =
                    results.firstOrNull { result ->

                        normalizeTitle(
                            result.title
                        ) == wanted ||
                        result.alternativeTitles.any { alt ->

                            normalizeTitle(
                                alt
                            ) == wanted
                        }
                    }

                if (anime == null) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        status.text =
                            "Ignored.\n\n" +
                            "No reliable MAL match for:\n" +
                            parsed.title
                    }

                    return@launch
                }

                withContext(
                    Dispatchers.Main
                ) {

                    status.text =
                        "Found MAL anime:\n" +
                        anime.title +
                        "\n\nSyncing..."
                }

                /*
                 * If no season was specified, use 1 as the
                 * temporary testing value.
                 *
                 * Your required format normally includes S1E01,
                 * so this mainly supports the parser's other formats.
                 */
                val season =
                    parsed.season ?: 1

                val result =
                    tracker.trackEpisode(
                        token = token,
                        anime = anime,
                        season = season,
                        episode = parsed.episode
                    )

                withContext(
                    Dispatchers.Main
                ) {

                    status.text =
                        buildResultText(result)
                }

            } catch (e: Exception) {

                withContext(
                    Dispatchers.Main
                ) {

                    status.text =
                        "Automatic sync failed:\n\n" +
                        e.message
                }
            }
        }
    }

    /*
     * =========================================================
     * TITLE NORMALIZATION
     * =========================================================
     */

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
     * RESULT DISPLAY
     * =========================================================
     */

    private fun buildResultText(
        result: MalTracker.TrackResult
    ): String {

        return buildString {

            append("✓ MAL SYNC SUCCESSFUL\n\n")

            append(result.animeTitle)
            append("\n")

            append("Season: ")
            append(result.season)
            append("\n")

            append("Episode: ")
            append(result.episode)
            append("\n")

            append("Status: ")
            append(result.status)
            append("\n")

            append("Episodes watched: ")
            append(result.watchedEpisodes)
            append("\n")

            if (result.started) {

                append("\n")
                append("Started watching ✓")
            }

            if (result.completed) {

                append("\n")
                append("Completed ✓")
            }
        }
    }

    override fun onDestroy() {

        scope.cancel()

        super.onDestroy()
    }
}
