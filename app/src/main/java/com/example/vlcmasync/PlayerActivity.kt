package com.example.vlcmasync

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class PlayerActivity : Activity(),
    SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    private var videoUri: Uri? = null
    private var videoFinished = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        videoUri =
            intent.getParcelableExtra(
                "video_uri"
            )

        if (videoUri == null) {
            finish()
            return
        }

        buildUi()

        initializePlayer()
    }

    private fun buildUi() {

        val root =
            FrameLayout(this)

        surfaceView =
            SurfaceView(this)

        status =
            TextView(this)

        status.text =
            "Loading video..."

        status.textSize =
            16f

        root.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)

        surfaceView
            .holder
            .addCallback(this)
    }

    private fun initializePlayer() {

        val options =
            ArrayList<String>()

        libVLC =
            LibVLC(
                this,
                options
            )

        mediaPlayer =
            MediaPlayer(libVLC)

        mediaPlayer.setEventListener { event ->

            when (event.type) {

                MediaPlayer.Event.Playing -> {

                    runOnUiThread {

                        status.visibility =
                            View.GONE
                    }
                }

                MediaPlayer.Event.EndReached -> {

                    if (!videoFinished) {

                        videoFinished =
                            true

                        runOnUiThread {

                            status.visibility =
                                View.VISIBLE

                            status.text =
                                "Episode finished ✓"
                        }

                        onEpisodeFinished()
                    }
                }

                MediaPlayer.Event.EncounteredError -> {

                    runOnUiThread {

                        status.visibility =
                            View.VISIBLE

                        status.text =
                            "Unable to play video."
                    }
                }
            }
        }
    }

    override fun surfaceCreated(
        holder: SurfaceHolder
    ) {

        mediaPlayer
            .vlcVout
            .setVideoSurface(
                holder.surface,
                holder
            )

        mediaPlayer
            .vlcVout
            .attachViews()

        startVideo()
    }

    private fun startVideo() {

        val uri =
            videoUri
                ?: return

        val media =
            Media(
                libVLC,
                uri
            )

        mediaPlayer.media =
            media

        media.release()

        mediaPlayer.play()
    }

    /*
     * =========================================================
     * EPISODE FINISHED
     * =========================================================
     *
     * This is where the MAL synchronization will happen.
     *
     * We intentionally wait until LibVLC reports
     * EndReached.
     */

    private fun onEpisodeFinished() {

        val uri =
            videoUri
                ?: return

        val filename =
            uri.lastPathSegment
                ?: return

        /*
         * Don't sync yet.
         *
         * The next step will connect this filename to:
         *
         * AnimeParser
         *      ↓
         * MAL search
         *      ↓
         * MalTracker
         */
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        // Nothing required here.
    }

    override fun surfaceDestroyed(
        holder: SurfaceHolder
    ) {

        mediaPlayer
            .vlcVout
            .detachViews()
    }

    override fun onStop() {

        if (::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
        }

        super.onStop()
    }

    override fun onDestroy() {

        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }

        if (::libVLC.isInitialized) {
            libVLC.release()
        }

        super.onDestroy()
    }
    }
