package ninja.richter.soundfork.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ninja.richter.soundfork.MainActivity
import ninja.richter.soundfork.R
import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.data.SoundTouchRepository
import ninja.richter.soundfork.model.RadioStation

class SoundForkMediaService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository = SoundTouchRepository()
    private var mediaSession: MediaSession? = null
    private var speakerHost: String? = null
    private var speakerPort: Int = SoundTouchRepository.DEFAULT_PORT
    private var title: String = DEFAULT_TITLE
    private var subtitle: String = DEFAULT_SUBTITLE
    private var isPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        mediaSession = MediaSession(this, TAG).apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    sendTransportAction(ACTION_PLAY)
                }

                override fun onPause() {
                    sendTransportAction(ACTION_PAUSE)
                }

                override fun onStop() {
                    sendTransportAction(ACTION_STOP)
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE -> {
                speakerHost = intent.getStringExtra(EXTRA_HOST) ?: speakerHost
                speakerPort = intent.getIntExtra(EXTRA_PORT, speakerPort)
                title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: title
                subtitle = intent.getStringExtra(EXTRA_SUBTITLE)?.takeIf { it.isNotBlank() } ?: subtitle
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
                updateMediaSession()
                startInForeground()
            }

            ACTION_PLAY, ACTION_PAUSE, ACTION_STOP -> {
                sendTransportAction(intent.action.orEmpty())
            }

            ACTION_DISMISS -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> startInForeground()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendTransportAction(action: String) {
        val host = speakerHost
        if (host.isNullOrBlank()) {
            Log.w(TAG, "sendTransportAction() ignored: no host")
            return
        }
        val normalizedAction = when (action) {
            ACTION_PLAY -> "PLAY"
            ACTION_PAUSE -> "PAUSE"
            ACTION_STOP -> "STOP"
            else -> return
        }
        serviceScope.launch {
            runCatching {
                repository.sendDlnaTransportAction(host = host, action = normalizedAction)
            }.onSuccess {
                isPlaying = normalizedAction == "PLAY"
                if (normalizedAction == "STOP") {
                    subtitle = "Gestoppt"
                }
                updateMediaSession()
                startInForeground()
            }.onFailure { throwable ->
                Log.w(TAG, "sendTransportAction() failed action=$normalizedAction error=${throwable.message}", throwable)
            }
        }
    }

    private fun updateMediaSession() {
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_STOP or
                    PlaybackState.ACTION_PLAY_PAUSE
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                1f
            )
            .build()
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
            .build()
        mediaSession?.setPlaybackState(playbackState)
        mediaSession?.setMetadata(metadata)
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val sessionToken = mediaSession?.sessionToken
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_DISMISS))
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_notification,
                    if (isPlaying) "Pause" else "Play",
                    serviceIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_notification,
                    "Stop",
                    serviceIntent(ACTION_STOP)
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
                    .also { style ->
                        if (sessionToken != null) {
                            style.setMediaSession(sessionToken)
                        }
                    }
            )
            .build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun serviceIntent(action: String): PendingIntent {
        val intent = Intent(this, SoundForkMediaService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SoundFork Wiedergabe",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Steuerung fuer den verbundenen Bose-Lautsprecher"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "SoundForkMediaService"
        private const val CHANNEL_ID = "soundfork_media"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN_APP = 3001
        private const val DEFAULT_TITLE = "SoundFork"
        private const val DEFAULT_SUBTITLE = "Bose-Lautsprecher verbunden"
        private const val ACTION_UPDATE = "ninja.richter.soundfork.media.UPDATE"
        private const val ACTION_PLAY = "ninja.richter.soundfork.media.PLAY"
        private const val ACTION_PAUSE = "ninja.richter.soundfork.media.PAUSE"
        private const val ACTION_STOP = "ninja.richter.soundfork.media.STOP"
        private const val ACTION_DISMISS = "ninja.richter.soundfork.media.DISMISS"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_IS_PLAYING = "isPlaying"

        fun update(
            context: Context,
            host: String,
            port: Int,
            station: RadioStation?,
            nowPlaying: NowPlayingState?,
            isPlaying: Boolean
        ) {
            val title = station?.name
                ?: nowPlaying?.stationName?.takeIf { it.isNotBlank() }
                ?: nowPlaying?.track?.takeIf { it.isNotBlank() }
                ?: DEFAULT_TITLE
            val subtitle = listOfNotNull(
                nowPlaying?.track?.takeIf { it.isNotBlank() },
                nowPlaying?.artist?.takeIf { it.isNotBlank() },
                station?.description?.takeIf { it.isNotBlank() }
            ).distinct().joinToString(" | ").ifBlank { DEFAULT_SUBTITLE }

            val intent = Intent(context, SoundForkMediaService::class.java)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_PORT, port)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SUBTITLE, subtitle)
                .putExtra(EXTRA_IS_PLAYING, isPlaying)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SoundForkMediaService::class.java))
        }
    }
}
