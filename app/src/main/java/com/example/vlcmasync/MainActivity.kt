package com.example.vlcmasync

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
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

        private const val PICK_VIDEO =
            1001
    }

    private val api =
        MalApi(OkHttpClient())

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main
        )

    private lateinit var status: TextView
    private lateinit var connection: TextView

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
     * MAIN UI
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
            "VlcMalSync"

        title.textSize =
            28f

        val subtitle =
            TextView(this)

        subtitle.text =
            "Anime player + MyAnimeList sync"

        subtitle.textSize =
            16f

        connection =
            TextView(this)

        connection.textSize =
            16f

        updateConnectionText()

        val login =
            Button(this)

        login.text =
            if (getToken() != null) {
                "Reconnect MyAnimeList"
            } else {
                "Connect MyAnimeList"
            }

        login.setOnClickListener {
            loginToMal()
        }

        val openVideo =
            Button(this)

        openVideo.text =
            "Open Anime Video"

        openVideo.setOnClickListener {
            openVideoPicker()
        }

        status =
            TextView(this)

        status.text =
            "Choose an anime video to start."

        status.textSize =
            16f

        box.addView(title)
        box.addView(subtitle)
        box.addView(connection)
        box.addView(login)
        box.addView(openVideo)
        box.addView(status)

        setContentView(box)
    }

    /*
     * =========================================================
     * VIDEO PICKER
     * =========================================================
     */

    private fun openVideoPicker() {

        val intent =
            Intent(
                Intent.ACTION_OPEN_DOCUMENT
            )

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        )

        intent.type =
            "video/*"

        startActivityForResult(
            intent,
            PICK_VIDEO
        )
    }

    @Deprecated("Using Activity result for compatibility.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode != PICK_VIDEO ||
            resultCode != RESULT_OK
        ) {
            return
        }

        val uri =
            data?.data
                ?: return

        /*
         * Keep permission to the selected document so that
         * the player can continue accessing it.
         */
        try {

            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

        } catch (_: Exception) {
            /*
             * Some providers don't support persistable
             * permissions. That's okay for playback.
             */
        }

        openPlayer(uri)
    }

    private fun openPlayer(
        uri: Uri
    ) {

        status.text =
            "Opening video..."

        val intent =
            Intent(
                this,
                PlayerActivity::class.java
            )

        intent.putExtra(
            "video_uri",
            uri
        )

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        startActivity(intent)
    }

    /*
     * =========================================================
     * CONNECTION STATUS
     * =========================================================
     */

    private fun updateConnectionText() {

        connection.text =
            if (getToken() != null) {
                "MAL connected ✓"
            } else {
                "MAL not connected"
            }
    }

    /*
     * =========================================================
     * MAL OAUTH
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

            updateConnectionText()

            return
        }

        val code =
            data.getQueryParameter("code")

        if (code.isNullOrBlank()) {

            status.text =
                "MAL login failed:\n" +
                "No authorization code."

            updateConnectionText()

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

            updateConnectionText()

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

                    updateConnectionText()

                    status.text =
                        "MAL connected ✓"
                }

            } catch (e: Exception) {

                withContext(
                    Dispatchers.Main
                ) {

                    updateConnectionText()

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

    override fun onDestroy() {

        scope.cancel()

        super.onDestroy()
    }
}
