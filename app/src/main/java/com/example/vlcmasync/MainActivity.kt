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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class MainActivity : Activity() {

    companion object {
        private const val CLIENT_ID =
            "9f0d8ade5b8a7922da3dafada0bc2153"

        private const val REDIRECT =
            "malvlcsync://oauth"

        private const val PREF_VERIFIER =
            "pkce_verifier"

        private const val PREF_TOKEN =
            "token"
    }

    private val api = MalApi(OkHttpClient())

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var status: TextView

    private var selected: MalAnime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(32, 32, 32, 32)

        val title = TextView(this)
        title.text = "VLC → MAL Sync"
        title.textSize = 25f

        status = TextView(this)
        status.text = if (getToken() != null) {
            "MAL connected ✓"
        } else {
            "MAL not connected"
        }
        status.textSize = 16f

        val login = Button(this)
        login.text = "Connect MyAnimeList"
        login.setOnClickListener {
            loginToMal()
        }

        val query = EditText(this)
        query.hint = "Anime title"

        val find = Button(this)
        find.text = "Find anime on MAL"
        find.setOnClickListener {
            searchAnime(query.text.toString())
        }

        val mark = Button(this)
        mark.text = "Mark selected episode watched"
        mark.setOnClickListener {
            markEpisode()
        }

        box.addView(title)
        box.addView(status)
        box.addView(login)
        box.addView(query)
        box.addView(find)
        box.addView(mark)

        setContentView(box)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent != null) {
            setIntent(intent)
            handleIntent(intent)
        }
    }

    private fun loginToMal() {

        val randomBytes = ByteArray(32)

        SecureRandom().nextBytes(randomBytes)

        val verifier = Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes)

        getPreferences(0)
            .edit()
            .putString(PREF_VERIFIER, verifier)
            .apply()

        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))

        val challenge = Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)

        val uri = Uri.Builder()
            .scheme("https")
            .authority("myanimelist.net")
            .path("/v1/oauth2/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri)

        startActivity(intent)
    }

    private fun handleIntent(intent: Intent?) {

        val data = intent?.data ?: return

        if (data.scheme != "malvlcsync") {
            return
        }

        if (data.host != "oauth") {
            return
        }

        val error = data.getQueryParameter("error")

        if (!error.isNullOrBlank()) {

            val description =
                data.getQueryParameter("error_description")
                    ?: error

            status.text =
                "MAL login cancelled: $description"

            return
        }

        val code =
            data.getQueryParameter("code")

        if (code.isNullOrBlank()) {

            status.text =
                "MAL login failed: no authorization code."

            return
        }

        val verifier =
            getPreferences(0)
                .getString(PREF_VERIFIER, null)

        if (verifier.isNullOrBlank()) {

            status.text =
                "MAL login failed: PKCE verifier missing."

            return
        }

        status.text =
            "Connecting to MyAnimeList..."

        scope.launch(Dispatchers.IO) {

            try {

                val token =
                    api.token(
                        CLIENT_ID,
                        code,
                        verifier,
                        REDIRECT
                    )

                getPreferences(0)
                    .edit()
                    .putString(PREF_TOKEN, token)
                    .remove(PREF_VERIFIER)
                    .apply()

                withContext(Dispatchers.Main) {
                    status.text =
                        "MAL connected ✓"
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    status.text =
                        "Login failed: ${e.message}"
                }
            }
        }
    }

    private fun getToken(): String? {

        return getPreferences(0)
            .getString(PREF_TOKEN, null)
    }

    private fun requireToken(): String? {

        val token = getToken()

        if (token == null) {
            status.text =
                "Connect MAL first."
        }

        return token
    }

    private fun searchAnime(query: String) {

        val token = requireToken()
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
                    api.search(token, query)

                withContext(Dispatchers.Main) {

                    if (results.isEmpty()) {

                        status.text =
                            "No matches."

                    } else {

                        val names =
                            results.map { anime ->
                                "${anime.id} — ${anime.title}"
                            }

                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Choose MAL match")
                            .setItems(
                                names.toTypedArray()
                            ) { _, which ->

                                selected =
                                    results[which]

                                status.text =
                                    "Selected: ${results[which].title}"
                            }
                            .show()
                    }
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    status.text =
                        "Search failed: ${e.message}"
                }
            }
        }
    }

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

        val input = EditText(this)

        input.hint =
            "Episode number"

        input.inputType =
            InputType.TYPE_CLASS_NUMBER

        AlertDialog.Builder(this)
            .setTitle("Mark watched")
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

                if (episode == null || episode < 0) {

                    status.text =
                        "Invalid episode."

                    return@setPositiveButton
                }

                scope.launch(Dispatchers.IO) {

                    try {

                        api.update(
                            token,
                            anime.id,
                            episode
                        )

                        withContext(Dispatchers.Main) {
                            status.text =
                                "✓ ${anime.title}: episode $episode synced"
                        }

                    } catch (e: Exception) {

                        withContext(Dispatchers.Main) {
                            status.text =
                                "Update failed: ${e.message}"
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
