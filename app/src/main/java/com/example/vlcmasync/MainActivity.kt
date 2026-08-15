package com.example.vlcmasync

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
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
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

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

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main
        )

    private lateinit var status: TextView

    private var selected: MalAnime? = null

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
            25f

        status =
            TextView(this)

        status.text =
            if (getToken() != null) {
                "MAL connected ✓"
            } else {
                "MAL not connected"
            }

        status.textSize =
            16f

        val login =
            Button(this)

        login.text =
            "Connect MyAnimeList"

        login.setOnClickListener {
            loginToMal()
        }

        /*
         * Keep the old manual search for now.
         * It is useful for checking that MAL itself still works.
         */
        val query =
            EditText(this)

        query.hint =
            "Anime title"

        val find =
            Button(this)

        find.text =
            "Find anime on MAL"

        find.setOnClickListener {
            searchAnime(
                query.text.toString()
            )
        }

        /*
         * Keep the old manual episode updater too.
         * We'll remove it once the automatic VLC system works.
         */
        val mark =
            Button(this)

        mark.text =
            "Mark selected episode watched"

        mark.setOnClickListener {
            markEpisode()
        }

        /*
         * TEMPORARY filename input.
         *
         * Eventually VLC will provide this automatically.
         */
        val filename =
            EditText(this)

        filename.hint =
            "Anime Title S1E01.mkv"

        val sync =
            Button(this)

        sync.text =
            "Sync filename to MAL"

        sync.setOnClickListener {

            syncFilename(
                filename.text.toString()
            )
        }

        box.addView(title)
        box.addView(status)
        box.addView(login)

        box.addView(query)
        box.addView(find)

        box.addView(mark)

        box.addView(filename)
        box.addView(sync)

        setContentView(box)
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

    /*
     * =========================================================
     * LOGIN
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
            data.getQueryParameter(
                "error"
            )

        if (!error.isNullOrBlank()) {

            val description =
                data.getQueryParameter(
                    "error_description"
                ) ?: error

            status.text =
                "MAL authorization failed: $description"

            return
        }

        val code =
            data.getQueryParameter(
                "code"
            )

        if (code.isNullOrBlank()) {

            status.text =
                "MAL login failed: no authorization code."

            return
        }

        val verifier =
            prefs.getString(
                PREF_VERIFIER,
                null
            )

        if (verifier.isNullOrBlank()) {

            status.text =
                "MAL login failed: verifier missing."

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
                        "Login failed: ${e.message}"
                }
            }
        }
    }

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
     * EXISTING MANUAL SEARCH
     * =========================================================
     */

    private fun searchAnime(
        query: String
    ) {

        val token =
            requireToken()
                ?: return

        if (query.isBlank()) {

            status.text =
                "Enter an anime title."

            return
        }

        status.text =
            "Searching MyAnimeList..."

        scope.launch(Dispatchers.IO) {

            try {

                val results =
                    api.search(
                        token,
                        query
                    )

                withContext(
                    Dispatchers.Main
                ) {

                    if (results.isEmpty()) {

                        status.text =
                            "No matches."

                    } else {

                        val names =
                            results.map { anime ->

                                "${anime.id} — ${anime.title}"
                            }

                        AlertDialog.Builder(
                            this@MainActivity
                        )
                            .setTitle(
                                "Choose MAL match"
                            )
                            .setItems(
                                names.toTypedArray()
                            ) { _, which ->

                                selected =
                                    results[which]

                                status.text =
                                    "Selected: " +
                                    results[which].title
                            }
                            .show()
                    }
                }

            } catch (e: Exception) {

                withContext(
                    Dispatchers.Main
                ) {

                    status.text =
                        "Search failed: ${e.message}"
                }
            }
        }
    }

    /*
     * =========================================================
     * FILENAME PARSER
     * =========================================================
     */

    private data class AnimeFile(
        val title: String,
        val season: Int,
        val episode: Int
    )

    private fun parseAnimeFilename(
        filename: String
    ): AnimeFile? {

        val name =
            filename
                .substringBeforeLast(
                    ".",
                    filename
                )
                .trim()

        /*
         * Required:
         *
         * Anime Title S1E01
         * Anime Title S2E12
         * Anime Title S10E105
         */
        val regex =
            Regex(
                """^(.+?)\s+S(\d+)E(\d+)$""",
                RegexOption.IGNORE_CASE
            )

        val match =
            regex.matchEntire(name)
                ?: return null

        val title =
            match.groupValues[1]
                .trim()

        val season =
            match.groupValues[2]
                .toIntOrNull()
                ?: return null

        val episode =
            match.groupValues[3]
                .toIntOrNull()
                ?: return null

        if (
            title.isBlank() ||
            season < 1 ||
            episode < 1
        ) {
            return null
        }

        return AnimeFile(
            title = title,
            season = season,
            episode = episode
        )
    }

    /*
     * =========================================================
     * TITLE NORMALIZATION
     * =========================================================
     *
     * Used only for matching.
     *
     * "Re Zero"
     * "Re:ZERO"
     *
     * become much closer to each other.
     */

    private fun normalizeTitle(
        title: String
    ): String {

        return title
            .lowercase(Locale.US)
            .replace(
                Regex("[^a-z0-9]+"),
                ""
            )
    }

    /*
     * =========================================================
     * FIND A SAFE MAL MATCH
     * =========================================================
     *
     * We do NOT blindly choose result #1.
     */

    private fun findBestMatch(
        inputTitle: String,
        results: List<MalAnime>
    ): MalAnime? {

        val wanted =
            normalizeTitle(
                inputTitle
            )

        if (wanted.isBlank()) {
            return null
        }

        /*
         * Exact main-title match.
         */
        results.firstOrNull { anime ->

            normalizeTitle(
                anime.title
            ) == wanted

        }?.let {
            return it
        }

        /*
         * Exact alternative-title match.
         */
        results.firstOrNull { anime ->

            anime.alternativeTitles.any { alt ->

                normalizeTitle(
                    alt
                ) == wanted
            }

        }?.let {
            return it
        }

        /*
         * Deliberately DON'T automatically accept a vague
         * partial match.
         *
         * This prevents random files from changing MAL.
         */
        return null
    }

    /*
     * =========================================================
     * AUTOMATIC FILENAME → MAL
     * =========================================================
     */

    private fun syncFilename(
        filename: String
    ) {

        val token =
            requireToken()
                ?: return

        val file =
            parseAnimeFilename(
                filename
            )

        if (file == null) {

            status.text =
                "Ignored: use Anime Title S1E01 format."

            return
        }

        status.text =
            "Searching MAL for ${file.title}..."

        scope.launch(Dispatchers.IO) {

            try {

                val results =
                    api.search(
                        token,
                        file.title
                    )

                val anime =
                    findBestMatch(
                        file.title,
                        results
                    )

                if (anime == null) {

                    withContext(
                        Dispatchers.Main
                    ) {

                        status.text =
                            "No reliable MAL match for " +
                            "\"${file.title}\". Ignored."
                    }

                    return@launch
                }

                /*
                 * We have a reliable title match.
                 *
                 * Now check the user's list.
                 */
                val current =
                    api.getMyListStatus(
                        token,
                        anime.id
                    )

                val today =
                    SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                    ).format(
                        Date()
                    )

                /*
                 * =================================================
                 * NOT CURRENTLY ON MAL LIST
                 * =================================================
                 */

                if (current == null) {

                    api.updateListStatus(
                        token = token,
                        animeId = anime.id,
                        status = "watching",
                        watchedEpisodes =
                            file.episode,
                        startDate =
                            today
                    )

                    withContext(
                        Dispatchers.Main
                    ) {

                        status.text =
                            "✓ ${anime.title}\n" +
                            "Watching\n" +
                            "S${file.season}E" +
                            String.format(
                                Locale.US,
                                "%02d",
                                file.episode
                            ) +
                            "\nStarted: $today"
                    }

                    return@launch
                }

                /*
                 * =================================================
                 * ALREADY ON MAL LIST
                 * =================================================
                 */

                val newProgress =
                    maxOf(
                        current.watchedEpisodes,
                        file.episode
                    )

                /*
                 * Don't overwrite the user's start date.
                 */
                api.updateListStatus(
                    token = token,
                    animeId = anime.id,
                    watchedEpisodes =
                        newProgress
                )

                withContext(
                    Dispatchers.Main
                ) {

                    status.text =
                        "✓ ${anime.title}\n" +
                        "Status: ${current.status}\n" +
                        "Episodes watched: " +
                        newProgress
                }

            } catch (e: Exception) {

                withContext(
                    Dispatchers.Main
                ) {

                    status.text =
                        "Automatic sync failed: " +
                        e.message
                }
            }
        }
    }

    /*
     * =========================================================
     * EXISTING MANUAL EPISODE UPDATE
     * =========================================================
     */

    private fun markEpisode() {

        val token =
            requireToken()
                ?: return

        val anime =
            selected

        if (anime == null) {

            status.text =
                "Select an anime first."

            return
        }

        val input =
            EditText(this)

        input.hint =
            "Episode number"

        input.inputType =
            InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle(
                "Mark watched"
            )
            .setView(input)
            .setNegativeButton(
                "Cancel",
                null
            )
            .setPositiveButton(
                "Sync"
            ) { _, _ ->

                val episode =
                    input.text
                        .toString()
                        .toIntOrNull()

                if (
                    episode == null ||
                    episode < 0
                ) {

                    status.text =
                        "Invalid episode."

                    return@setPositiveButton
                }

                scope.launch(
                    Dispatchers.IO
                ) {

                    try {

                        api.update(
                            token,
                            anime.id,
                            episode
                        )

                        withContext(
                            Dispatchers.Main
                        ) {

                            status.text =
                                "✓ ${anime.title}: " +
                                "episode $episode synced"
                        }

                    } catch (e: Exception) {

                        withContext(
                            Dispatchers.Main
                        ) {

                            status.text =
                                "Update failed: " +
                                e.message
                        }
                    }
                }
            }
            .show()
    }

    override fun onDestroy() {

        scope.cancel()

        super.onDestroy()
    }
}
