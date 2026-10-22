package com.ounben.amaradio.players

import android.content.Context
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import com.ounben.amaradio.players.exoplayer.Media3Utils
import com.ounben.amaradio.players.exoplayer.MetadataHandler
import com.ounben.amaradio.cast.CastManager
import com.ounben.amaradio.station.live.ShoutcastInfo
import com.ounben.amaradio.station.live.StreamLiveInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CastPlayerWrapper(private val castPlayer: CastPlayer) : PlayerWrapper {

    private var listener: PlayerWrapper.PlayListener? = null
    private var stationName: String? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val state = when (playbackState) {
                Player.STATE_READY -> if (castPlayer.playWhenReady) PlayState.Playing else PlayState.Paused
                Player.STATE_BUFFERING -> PlayState.PrePlaying
                Player.STATE_ENDED -> PlayState.Idle
                Player.STATE_IDLE -> if (castPlayer.playWhenReady) PlayState.PrePlaying else PlayState.Idle
                else -> PlayState.Idle
            }
            Log.d(TAG, "onPlaybackStateChanged: rawState=$playbackState, mappedState=$state, playWhenReady=${castPlayer.playWhenReady}")
            listener?.onStateChanged(state)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
            if (isPlaying) {
                listener?.onStateChanged(PlayState.Playing)
            } else if (castPlayer.playbackState == Player.STATE_READY && !castPlayer.playWhenReady) {
                listener?.onStateChanged(PlayState.Paused)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "onPlayerError: ${error.message}", error)
            listener?.onPlayerError(com.ounben.amaradio.R.string.error_station_load)
        }

        override fun onMetadata(metadata: Metadata) {
            MetadataHandler.handleMetadata(
                metadata,
                onStreamLiveInfo = { listener?.onDataSourceStreamLiveInfo(it) },
                onShoutcastInfo = { listener?.onDataSourceShoutcastInfo(it, false) }
            )
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            MetadataHandler.handleMediaMetadata(
                mediaMetadata,
                stationName,
                onStreamLiveInfo = { listener?.onDataSourceStreamLiveInfo(it) }
            )
        }
    }

    init {
        castPlayer.addListener(playerListener)
    }

    override fun playRemote(httpClient: OkHttpClient, streamUrl: String, context: Context, metadata: MediaMetadata?) {
        this.stationName = metadata?.station?.toString() ?: metadata?.title?.toString()

        CoroutineScope(Dispatchers.IO).launch {
            val resolvedUrl = try {
                val fastClient = httpClient.newBuilder()
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url(streamUrl)
                    .head()
                    .build()

                fastClient.newCall(request).execute().use { response ->
                    response.request.url.toString()
                }
            } catch (e: Exception) {
                streamUrl
            }

            Log.d(TAG, "URL Resolution: original='$streamUrl' -> resolved='$resolvedUrl'")

            withContext(Dispatchers.Main) {
                val remoteMediaClient = CastManager.getInstance()?.remoteMediaClient
                if (remoteMediaClient != null) {
                    val stationTitle = metadata?.station?.toString()
                        .takeIf { !it.isNullOrEmpty() } 
                        ?: metadata?.title?.toString()
                        .takeIf { !it.isNullOrEmpty() } 
                        ?: "AMARadio Live"

                    val castMetadata = com.google.android.gms.cast.MediaMetadata(
                        com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_GENERIC
                    ).apply {
                        putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, stationTitle)
                        val artist = metadata?.artist?.toString()
                        if (!artist.isNullOrEmpty()) {
                            putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, artist)
                        }
                    }

                    val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(resolvedUrl)
                        .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE)
                        .setContentType("audio/mpeg")
                        .setStreamDuration(0L)
                        .setMetadata(castMetadata)
                        .build()

                    val requestData = com.google.android.gms.cast.MediaLoadRequestData.Builder()
                        .setMediaInfo(mediaInfo)
                        .setAutoplay(true)
                        .build()

                    Log.d(TAG, "Executing RemoteMediaClient.load for URL: $resolvedUrl")
                    remoteMediaClient.load(requestData)
                } else {
                    val mediaItem = Media3Utils.buildLiveMediaItem(resolvedUrl.toUri(), metadata)
                    castPlayer.setMediaItem(mediaItem, /* resetPosition= */ true)
                    castPlayer.playWhenReady = true
                    castPlayer.prepare()
                }
            }
        }
    }

    override fun pause() {
        Log.d(TAG, "Sending STOP command to CastPlayer for Live Stream")
        castPlayer.stop()
        listener?.onStateChanged(PlayState.Paused)
    }

    override fun stop() {
        Log.d(TAG, "Sending STOP command to CastPlayer")
        castPlayer.stop()
    }

    override fun release() {
        castPlayer.removeListener(playerListener)
    }

    override fun isPlaying(): Boolean = castPlayer.isPlaying

    override val player: Player get() = castPlayer

    override val bufferedMs: Long get() = castPlayer.bufferedPosition

    override val audioSessionId: Int get() = 0 

    override val currentPlaybackTransferredBytes: Long get() = 0 

    override val isLocal: Boolean get() = false

    override fun setVolume(newVolume: Float) {
        // Handled via Cast session usually
    }

    override fun setStateListener(listener: PlayerWrapper.PlayListener?) {
        this.listener = listener
    }

    companion object {
        private const val TAG = "ama_cast_castwrapper"
    }
}
