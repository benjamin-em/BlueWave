package com.ounben.amaradio.cast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.mediarouter.app.MediaRouteChooserDialogFragment
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors

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

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session started: $sessionId")
            _isCasting.value = true
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _isCasting.value = false
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.d(TAG, "Cast session ended")
            _isCasting.value = false
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.d(TAG, "Cast session resumed")
            _isCasting.value = true
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _isCasting.value = false
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _isCasting.value = false
        }
    }

    fun ensureInitializedAsync(context: Context, onInitialized: ((CastContext) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Cast initialization deferred until local network access is granted")
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
                    _castPlayer = CastPlayer(contextInstance, DefaultMediaItemConverter())
                    contextInstance.sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
                    _isCasting.value = contextInstance.sessionManager.currentCastSession?.isConnected == true
                    onInitialized?.invoke(contextInstance)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to setup CastPlayer after sharedInstance success", e)
                }
            }
            .addOnFailureListener(mainExecutor) { e ->
                initializing = false
                Log.e(TAG, "Failed to initialize CastContext asynchronously", e)
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
                Log.e(TAG, "Error opening MediaRouteChooserDialogFragment", e)
            }
        }
    }

    companion object {
        private const val TAG = "CastManager"
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
