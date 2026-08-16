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
            intent.getParcelableExtra("video_uri")

        if (videoUri == null) {
            finish()
            return
        }

        buildUi()
        initializePlayer()
    }

    private fun buildUi() {

        val root = FrameLayout(this)

        surfaceView = SurfaceView(this)

        status = TextView(this)

        status.text =
            "Loading video..."

        status.textSize = 16f

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

        surfaceView.holder.addCallback(this)
    }

    private fun initializePlayer() {

        val options =
            arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--network-caching=150"
            )

        libVLC =
            LibVLC(
                this,
                options
            )

        mediaPlayer =
            MediaPlayer(libVLC)

        mediaPlayer.setEventListener { event ->

            when (event.type) {

                MediaPlayer.Event.Opening -> {

                    runOnUiThread {
                        status.visibility =
                            View.VISIBLE

                        status.text =
                            "Opening video..."
                    }
                }

                MediaPlayer.Event.Buffering -> {

                    runOnUiThread {
                        status.visibility =
                            View.VISIBLE

                        status.text =
                            "Buffering ${event.buffering.toInt()}%"
                    }
                }

                MediaPlayer.Event.Playing -> {

                    runOnUiThread {
                        status.visibility =
                            View.GONE
                    }
                }

                MediaPlayer.Event.EndReached -> {

                    if (!videoFinished) {

                        videoFinished = true

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
                            "Unable to play video.\n\n" +
                            "URI:\n" +
                            videoUri
                    }
                }
            }
        }
    }

    override fun surfaceCreated(
        holder: SurfaceHolder
    ) {

        mediaPlayer.vlcVout.setVideoSurface(
            holder.surface,
            holder
        )

        mediaPlayer.vlcVout.attachViews()

        startVideo()
    }

    private fun startVideo() {

        val uri =
            videoUri
                ?: return

        try {

            status.visibility =
                View.VISIBLE

            status.text =
                "Opening:\n" +
                (uri.lastPathSegment
                    ?: "video")

            val media =
                Media(
                    libVLC,
                    uri
                )

            /*
             * Tell VLC that this is a local Android
             * document rather than a network stream.
             */
            media.addOption(
                ":network-caching=150"
            )

            mediaPlayer.media =
                media

            media.release()

            mediaPlayer.play()

        } catch (e: Exception) {

            status.visibility =
                View.VISIBLE

            status.text =
                "Player error:\n\n" +
                (e.message ?: "Unknown error")
        }
    }

    private fun onEpisodeFinished() {

        val uri =
            videoUri
                ?: return

        val filename =
            uri.lastPathSegment
                ?: return

        /*
         * MAL synchronization will be connected here.
         *
         * filename →
         * AnimeParser →
         * MalTracker
         */
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
    }

    override fun surfaceDestroyed(
        holder: SurfaceHolder
    ) {

        if (::mediaPlayer.isInitialized) {

            mediaPlayer.vlcVout.detachViews()
        }
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
