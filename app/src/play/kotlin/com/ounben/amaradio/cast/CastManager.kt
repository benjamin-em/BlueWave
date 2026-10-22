package com.ounben.amaradio.cast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.cast.CastPlayer
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LiveCastMediaItemConverter : androidx.media3.cast.MediaItemConverter {
    override fun toMediaQueueItem(mediaItem: androidx.media3.common.MediaItem): com.google.android.gms.cast.MediaQueueItem {
        val uri = mediaItem.localConfiguration?.uri?.toString() ?: ""

        val stationTitle = mediaItem.mediaMetadata.title?.toString()
            .takeIf { !it.isNullOrEmpty() } 
            ?: mediaItem.mediaMetadata.station?.toString()
            .takeIf { !it.isNullOrEmpty() } 
            ?: "AMARadio Live"

        val castMetadata = com.google.android.gms.cast.MediaMetadata(
            com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_GENERIC
        ).apply {
            putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, stationTitle)
            val artist = mediaItem.mediaMetadata.artist?.toString()
            if (!artist.isNullOrEmpty()) {
                putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, artist)
            }
        }

        val mimeType = mediaItem.localConfiguration?.mimeType ?: "audio/mpeg"

        val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(uri)
            .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(mimeType)
            .setStreamDuration(0L)
            .setMetadata(castMetadata)
            .build()

        val queueItem = com.google.android.gms.cast.MediaQueueItem.Builder(mediaInfo)
            .setAutoplay(true)
            .build()

        try {
            val rawQueueJson = queueItem.toJson().toString()
            Log.d("ama_cast_castmanager", "RAW MediaQueueItem JSON: $rawQueueJson")
        } catch (e: Exception) {
            Log.e("ama_cast_castmanager", "Failed to print raw JSON", e)
        }

        return queueItem
    }

    override fun toMediaItem(mediaQueueItem: com.google.android.gms.cast.MediaQueueItem): androidx.media3.common.MediaItem {
        val uriString = mediaQueueItem.media?.contentId ?: ""
        return androidx.media3.common.MediaItem.Builder()
            .setUri(uriString)
            .build()
    }
}

class CastManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var initializing = false
    
    private var _castPlayer: CastPlayer? = null
    val castPlayer: CastPlayer? get() = _castPlayer

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting

    @Volatile
    private var castContext: CastContext? = null

    val remoteMediaClient: com.google.android.gms.cast.framework.media.RemoteMediaClient?
        get() = castContext?.sessionManager?.currentCastSession?.remoteMediaClient

    @Volatile
    private var currentSessionId: String? = null
    val activeSessionId: String? get() = currentSessionId ?: castContext?.sessionManager?.currentCastSession?.sessionId

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d("ama_cast_castmanager", "Cast session started: $sessionId")
            currentSessionId = sessionId
            _isCasting.value = true
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            currentSessionId = null
            _isCasting.value = false
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.d("ama_cast_castmanager", "Cast session ended")
            currentSessionId = null
            _isCasting.value = false
            try {
                _castPlayer?.stop()
            } catch (e: Exception) {
                Log.e("ama_cast_castmanager", "Error stopping CastPlayer on session ended", e)
            }
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            val sessionId = session.sessionId
            Log.d("ama_cast_castmanager", "Cast session resumed: $sessionId")
            currentSessionId = sessionId
            _isCasting.value = true
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            currentSessionId = null
            _isCasting.value = false
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _isCasting.value = false
        }
    }

    fun ensureInitializedAsync(context: Context, onInitialized: ((CastContext) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            Log.d("ama_cast_castmanager", "Cast initialization deferred until local network access is granted")
            return
        }
        val existingContext = castContext
        if (existingContext != null) {
            onInitialized?.invoke(existingContext)
            return
        }

        if (initializing) return
        initializing = true
        val mainExecutor = ContextCompat.getMainExecutor(context)
        CastContext.getSharedInstance(context, executor)
            .addOnSuccessListener(mainExecutor) { contextInstance ->
                initializing = false
                try {
                    castContext = contextInstance
                    _castPlayer = CastPlayer(contextInstance, LiveCastMediaItemConverter())
                    contextInstance.sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
                    _isCasting.value = contextInstance.sessionManager.currentCastSession?.isConnected == true
                    onInitialized?.invoke(contextInstance)
                } catch (e: Exception) {
                    Log.e("ama_cast_castmanager", "Failed to setup CastPlayer after sharedInstance success", e)
                }
            }
            .addOnFailureListener(mainExecutor) { e ->
                initializing = false
                Log.e("ama_cast_castmanager", "Failed to initialize CastContext asynchronously", e)
            }
    }

    fun stopAndDisconnectCastSession(stopCasting: Boolean = true) {
        Log.d("ama_cast_castmanager", "stopAndDisconnectCastSession called, stopCasting=$stopCasting")
        currentSessionId = null
        _isCasting.value = false
        
        try {
            Log.d("ama_cast_castmanager", "Sending STOP / DISCONNECT command to CastSession")
            _castPlayer?.stop()
        } catch (e: Exception) {
            Log.e("ama_cast_castmanager", "Error stopping CastPlayer during disconnect", e)
        }

        try {
            castContext?.sessionManager?.endCurrentSession(stopCasting)
        } catch (e: Exception) {
            Log.e("ama_cast_castmanager", "Error ending current CastSession", e)
        }
    }

    fun openCastDialog(activity: FragmentActivity) {
        ensureInitializedAsync(activity) { ctx ->
            try {
                val selector = ctx.mergedSelector
                if (selector != null) {
                    val fragment = MediaRouteChooserDialogFragment()
                    fragment.routeSelector = selector
                    fragment.show(activity.supportFragmentManager, "MediaRouteChooserDialogFragment")
                }
            } catch (e: Exception) {
                Log.e("ama_cast_castmanager", "Error opening MediaRouteChooserDialogFragment", e)
            }
        }
    }

    companion object {
        private const val TAG = "ama_cast_castmanager"
        @Volatile
        private var instance: CastManager? = null

        fun init(context: Context): CastManager {
            return instance ?: synchronized(this) {
                instance ?: CastManager(context).also { instance = it }
            }
        }

        fun getInstance(context: Context? = null): CastManager? {
            if (instance == null && context != null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = CastManager(context)
                    }
                }
            }
            return instance
        }
    }
}
