package com.example.vlcmasync

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.Locale

class PlayerActivity : Activity(),
    SurfaceHolder.Callback {

    private lateinit var root: FrameLayout
    private lateinit var surfaceView: SurfaceView

    private lateinit var controls: LinearLayout
    private lateinit var playButton: Button
    private lateinit var seekBar: SeekBar
    private lateinit var timeText: TextView
    private lateinit var titleText: TextView
    private lateinit var statusText: TextView

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    private var videoUri: Uri? = null
    private var videoFinished = false
    private var controlsVisible = true
    private var isUserSeeking = false

    private val handler =
        Handler(Looper.getMainLooper())

    private val hideControlsRunnable =
        Runnable {
            hideControls()
        }

    private val progressRunnable =
        object : Runnable {

            override fun run() {

                if (::mediaPlayer.isInitialized) {

                    if (!isUserSeeking) {

                        val length =
                            mediaPlayer.length

                        val time =
                            mediaPlayer.time

                        if (length > 0) {

                            seekBar.max =
                                length.toInt()

                            seekBar.progress =
                                time
                                    .coerceIn(
                                        0,
                                        length
                                    )
                                    .toInt()

                            timeText.text =
                                formatTime(time) +
                                " / " +
                                formatTime(length)
                        }
                    }
                }

                handler.postDelayed(
                    this,
                    500
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        /*
         * Keep the screen awake while watching.
         */
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        /*
         * Start in fullscreen.
         */
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

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

        handler.post(
            progressRunnable
        )
    }

    /*
     * =========================================================
     * UI
     * =========================================================
     */

    private fun buildUi() {

        root =
            FrameLayout(this)

        root.setBackgroundColor(
            android.graphics.Color.BLACK
        )

        /*
         * VIDEO
         */

        surfaceView =
            SurfaceView(this)

        root.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        /*
         * TOP BAR
         */

        val topBar =
            LinearLayout(this)

        topBar.orientation =
            LinearLayout.HORIZONTAL

        topBar.gravity =
            Gravity.CENTER_VERTICAL

        topBar.setPadding(
            20,
            12,
            20,
            12
        )

        titleText =
            TextView(this)

        titleText.text =
            videoUri
                ?.lastPathSegment
                ?: "Anime"

        titleText.textSize =
            16f

        titleText.setTextColor(
            android.graphics.Color.WHITE
        )

        titleText.maxLines = 1

        topBar.addView(
            titleText,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val close =
            Button(this)

        close.text =
            "×"

        close.setOnClickListener {
            finish()
        }

        topBar.addView(
            close,
            LinearLayout.LayoutParams(
                60,
                60
            )
        )

        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        /*
         * CENTER STATUS
         */

        statusText =
            TextView(this)

        statusText.text =
            "Loading video..."

        statusText.textSize =
            16f

        statusText.setTextColor(
            android.graphics.Color.WHITE
        )

        statusText.gravity =
            Gravity.CENTER

        root.addView(
            statusText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        /*
         * BOTTOM CONTROLS
         */

        controls =
            LinearLayout(this)

        controls.orientation =
            LinearLayout.VERTICAL

        controls.setPadding(
            20,
            10,
            20,
            20
        )

        /*
         * SEEKBAR
         */

        seekBar =
            SeekBar(this)

        seekBar.max = 100

        seekBar.setOnSeekBarChangeListener(
            object :
                SeekBar.OnSeekBarChangeListener {

                override fun onStartTrackingTouch(
                    bar: SeekBar
                ) {

                    isUserSeeking = true
                }

                override fun onProgressChanged(
                    bar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    if (fromUser &&
                        ::mediaPlayer.isInitialized
                    ) {

                        timeText.text =
                            formatTime(
                                progress.toLong()
                            ) +
                            " / " +
                            formatTime(
                                mediaPlayer.length
                            )
                    }
                }

                override fun onStopTrackingTouch(
                    bar: SeekBar
                ) {

                    if (::mediaPlayer.isInitialized) {

                        mediaPlayer.time =
                            bar.progress.toLong()
                    }

                    isUserSeeking = false
                }
            }
        )

        controls.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        /*
         * BUTTON ROW
         */

        val buttonRow =
            LinearLayout(this)

        buttonRow.orientation =
            LinearLayout.HORIZONTAL

        buttonRow.gravity =
            Gravity.CENTER_VERTICAL

        /*
         * BACK 10 SEC
         */

        val back =
            Button(this)

        back.text =
            "-10"

        back.setOnClickListener {

            if (::mediaPlayer.isInitialized) {

                mediaPlayer.time =
                    (
                        mediaPlayer.time - 10_000
                    ).coerceAtLeast(0)
            }

            showControls()
        }

        buttonRow.addView(
            back
        )

        /*
         * PLAY / PAUSE
         */

        playButton =
            Button(this)

        playButton.text =
            "▶"

        playButton.setOnClickListener {

            if (!::mediaPlayer.isInitialized) {
                return@setOnClickListener
            }

            if (mediaPlayer.isPlaying) {

                mediaPlayer.pause()

            } else {

                mediaPlayer.play()
            }

            updatePlayButton()
            showControls()
        }

        buttonRow.addView(
            playButton
        )

        /*
         * FORWARD 10 SEC
         */

        val forward =
            Button(this)

        forward.text =
            "+10"

        forward.setOnClickListener {

            if (::mediaPlayer.isInitialized) {

                mediaPlayer.time =
                    (
                        mediaPlayer.time + 10_000
                    ).coerceAtMost(
                        mediaPlayer.length
                    )
            }

            showControls()
        }

        buttonRow.addView(
            forward
        )

        /*
         * TIME
         */

        timeText =
            TextView(this)

        timeText.text =
            "00:00 / 00:00"

        timeText.textSize =
            14f

        timeText.setTextColor(
            android.graphics.Color.WHITE
        )

        timeText.gravity =
            Gravity.CENTER_VERTICAL

        buttonRow.addView(
            timeText,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        /*
         * FULLSCREEN BUTTON
         */

        val fullscreen =
            Button(this)

        fullscreen.text =
            "⛶"

        fullscreen.setOnClickListener {

            toggleFullscreen()
        }

        buttonRow.addView(
            fullscreen
        )

        controls.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            controls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        /*
         * TAPPING THE VIDEO SHOWS/HIDES CONTROLS.
         */

        surfaceView.setOnTouchListener {
                _,
                event ->

            if (
                event.action ==
                MotionEvent.ACTION_UP
            ) {

                if (controlsVisible) {
                    hideControls()
                } else {
                    showControls()
                }
            }

            true
        }

        setContentView(root)

        showControls()
    }

    /*
     * =========================================================
     * LIBVLC
     * =========================================================
     */

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

        mediaPlayer.setEventListener {
            event ->

            runOnUiThread {

                when (event.type) {

                    MediaPlayer.Event.Opening -> {

                        statusText.visibility =
                            View.VISIBLE

                        statusText.text =
                            "Opening video..."
                    }

                    MediaPlayer.Event.Buffering -> {

                        statusText.visibility =
                            View.VISIBLE

                        statusText.text =
                            "Buffering " +
                            event.buffering
                                .toInt() +
                            "%"
                    }

                    MediaPlayer.Event.Playing -> {

                        statusText.visibility =
                            View.GONE

                        updatePlayButton()

                        showControls()
                    }

                    MediaPlayer.Event.Paused -> {

                        updatePlayButton()
                        showControls()
                    }

                    MediaPlayer.Event.Stopped -> {

                        updatePlayButton()
                    }

                    MediaPlayer.Event.EndReached -> {

                        updatePlayButton()

                        if (!videoFinished) {

                            videoFinished = true

                            statusText.visibility =
                                View.VISIBLE

                            statusText.text =
                                "Episode finished ✓"

                            onEpisodeFinished()
                        }
                    }

                    MediaPlayer.Event.EncounteredError -> {

                        statusText.visibility =
                            View.VISIBLE

                        statusText.text =
                            "Unable to play video."
                    }
                }
            }
        }
    }

    /*
     * =========================================================
     * SURFACE
     * =========================================================
     */

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

            statusText.visibility =
                View.VISIBLE

            statusText.text =
                "Opening..."

            val media =
                Media(
                    libVLC,
                    uri
                )

            media.addOption(
                ":network-caching=150"
            )

            mediaPlayer.media =
                media

            media.release()

            mediaPlayer.play()

        } catch (e: Exception) {

            statusText.visibility =
                View.VISIBLE

            statusText.text =
                "Player error:\n" +
                (
                    e.message
                        ?: "Unknown error"
                )
        }
    }

    /*
     * =========================================================
     * PLAY BUTTON
     * =========================================================
     */

    private fun updatePlayButton() {

        if (!::mediaPlayer.isInitialized) {
            return
        }

        playButton.text =
            if (mediaPlayer.isPlaying) {
                "Ⅱ"
            } else {
                "▶"
            }
    }

    /*
     * =========================================================
     * CONTROLS
     * =========================================================
     */

    private fun showControls() {

        controlsVisible = true

        controls.visibility =
            View.VISIBLE

        handler.removeCallbacks(
            hideControlsRunnable
        )

        /*
         * Don't immediately hide controls while paused.
         */

        if (
            ::mediaPlayer.isInitialized &&
            mediaPlayer.isPlaying
        ) {

            handler.postDelayed(
                hideControlsRunnable,
                4000
            )
        }
    }

    private fun hideControls() {

        if (
            ::mediaPlayer.isInitialized &&
            !mediaPlayer.isPlaying
        ) {
            return
        }

        controlsVisible = false

        controls.visibility =
            View.GONE
    }

    /*
     * =========================================================
     * FULLSCREEN
     * =========================================================
     */

    private fun toggleFullscreen() {

        val flags =
            window.decorView.systemUiVisibility

        val fullscreen =
            flags and
            View.SYSTEM_UI_FLAG_FULLSCREEN != 0

        if (fullscreen) {

            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        } else {

            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }

        showControls()
    }

    /*
     * =========================================================
     * EPISODE FINISHED
     * =========================================================
     */

    private fun onEpisodeFinished() {

        val uri =
            videoUri
                ?: return

        val filename =
            uri.lastPathSegment
                ?: return

        /*
         * The next connection is:
         *
         * filename
         *      ↓
         * AnimeParser
         *      ↓
         * MAL search
         *      ↓
         * MalTracker
         *
         * This is intentionally kept separate from the
         * playback engine so we don't break the player.
         */
    }

    /*
     * =========================================================
     * TIME FORMAT
     * =========================================================
     */

    private fun formatTime(
        milliseconds: Long
    ): String {

        if (milliseconds < 0) {
            return "00:00"
        }

        val totalSeconds =
            milliseconds / 1000

        val seconds =
            totalSeconds % 60

        val minutes =
            (totalSeconds / 60) % 60

        val hours =
            totalSeconds / 3600

        return if (hours > 0) {

            String.format(
                Locale.US,
                "%d:%02d:%02d",
                hours,
                minutes,
                seconds
            )

        } else {

            String.format(
                Locale.US,
                "%02d:%02d",
                minutes,
                seconds
            )
        }
    }

    /*
     * =========================================================
     * SURFACE CALLBACKS
     * =========================================================
     */

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

    /*
     * =========================================================
     * LIFECYCLE
     * =========================================================
     */

    override fun onStop() {

        if (::mediaPlayer.isInitialized) {

            mediaPlayer.stop()
        }

        super.onStop()
    }

    override fun onDestroy() {

        handler.removeCallbacks(
            progressRunnable
        )

        handler.removeCallbacks(
            hideControlsRunnable
        )

        if (::mediaPlayer.isInitialized) {

            mediaPlayer.release()
        }

        if (::libVLC.isInitialized) {

            libVLC.release()
        }

        super.onDestroy()
    }
    }
