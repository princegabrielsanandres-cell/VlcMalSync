package com.example.vlcmasync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class MainActivity : Activity() {

    companion object {
        private const val CLIENT_ID = "9f0d8ade5b8a7922da3dafada0bc2153"
        private const val REDIRECT = "malvlcsync://oauth"

        private const val PREFS = "vlc_mal_sync"
        private const val PREF_VERIFIER = "pkce_verifier"
        private const val PREF_TOKEN = "token"
    }

    private val api = MalApi(OkHttpClient())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var status: TextView
    private var selected: MalAnime? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "VLC → MAL Sync"
            textSize = 25f
        }

        status = TextView(this).apply {
            text = if (token() != null) {
                "MAL connected ✓"
            } else {
                "MAL not connected"
            }
            textSize = 16f
        }

        val login = Button(this).apply {
            text = "Connect MyAnimeList"
            setOnClickListener { login() }
        }

        val q = EditText(this).apply {
            hint = "Anime title"
        }

        val find = Button(this).apply {
            text = "Find anime on MAL"
            setOnClickListener {
                find(q.text.toString())
            }
        }

        val mark = Button(this).apply {
            text = "Mark selected episode watched"
            setOnClickListener {
                mark()
            }
        }

        box.addView(title)
        box.addView(status)
        box.addView(login)
        box.addView(q)
        box.addView(find)
        box.addView(mark)

        setContentView(box)

        // Handle an OAuth callback if Android launched the activity
        // directly with the callback URI.
        handle(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        // Because MainActivity uses singleTask, OAuth callbacks can
        // arrive here instead of creating another Activity instance.
        setIntent(intent)
        handle(intent)
    }

    private fun login() {
        val x = ByteArray(32).also {
            SecureRandom().nextBytes(it)
        }

        val verifier = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(x)

        // Save the verifier before opening MAL. This is important because
        // Android may recreate the Activity while the browser is open.
        getPreferences(0)
            .edit()
            .putString(PREF_VERIFIER, verifier)
            .apply()

        val challengeBytes = MessageDigest
            .getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))

        val challenge = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(challengeBytes)

        val u = Uri.Builder()
            .scheme("https")
            .authority("myanimelist.net")
            .path("/v1/oauth2/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        startActivity(Intent(Intent.ACTION_VIEW, u))
    }

    private fun handle(i: Intent?) {
        val d = i?.data ?: return

        if (d.scheme != "malvlcsync" || d.host != "oauth") {
            return
        }

        val error = d.getQueryParameter("error")

        if (error != null) {
            val description =
                d.getQueryParameter("error_description") ?: error

            status.text = "MAL login cancelled: $description"
            return
        }

        val code = d.getQueryParameter("code")

        if (code.isNullOrBlank()) {
            status.text = "MAL login failed: no authorization code."
            return
        }

        val verifier = getPreferences(0)
            .getString(PREF_VERIFIER, null)

        if (verifier.isNullOrBlank()) {
            status.text = "MAL login failed: PKCE verifier missing."
            return
        }

        status.text = "Connecting to MyAnimeList..."

        scope.launch(Dispatchers.IO) {
            try {
                val t = api.token(
                    CLIENT_ID,
                    code,
                    verifier,
                    REDIRECT
                )

                getPreferences(0)
                    .edit()
                    .putString(PREF_TOKEN, t)
                    .remove(PREF_VERIFIER)
                    .apply()

                ui("MAL connected ✓")

            } catch (e: Exception) {
                ui("Login failed: ${e.message}")
            }
        }
    }

    private fun token(): String? {
        return getPreferences(0)
            .getString(PREF_TOKEN, null)
    }

    private fun tok(): String? {
        val t = token()

        if (t == null) {
            status.text = "Connect MAL first."
        }

        return t
    }

    private fun find(q: String) {
        val t = tok() ?: return

        if (q.isBlank()) {
            status.text = "Enter an anime title."
            return
        }

        status.text = "Searching MyAnimeList..."

        scope.launch(Dispatchers.IO) {
            try {
                val r = api.search(t, q)

                withContext(Dispatchers.Main) {
                    if (r.isEmpty()) {
                        status.text = "No matches."
                    } else {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Choose MAL match")
                            .setItems(
                                r.map {
                                    "${it.id} — ${it.title}"
                                }.toTypedArray()
                            ) { _, w ->
                                selected = r[w]
                                status.text =
                                    "Selected: ${r[w].title}"
                            }
                            .show()
                    }
                }

            } catch (e: Exception) {
                ui("Search failed: ${e.message}")
            }
        }
    }

    private fun mark() {
        val t = tok() ?: return

        val a = selected ?: run {
            status.text = "Select an anime first."
            return
        }

        val e = EditText(this).apply {
            hint = "Episode number"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        AlertDialog.Builder(this)
            .setTitle("Mark watched")
            .setView(e)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sync") { _, _ ->

                val n = e.text.toString().toIntOrNull()

                if (n == null || n < 0) {
                    status.text = "Invalid episode."
                    return@setPositiveButton
                }

                scope.launch(Dispatchers.IO) {
                    try {
                        api.update(t, a.id, n)

                        ui(
                            "✓ ${a.title}: episode $n synced"
                        )

                    } catch (x: Exception) {
                        ui(
                            "Update failed: ${x.message}"
                        )
                    }
                }
            }
            .show()
    }

    private fun ui(s: String) {
        runOnUiThread {
            status.text = s
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
