package com.jubahir.arrowroute

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import kotlin.math.min

/**
 * Unity Ads settings (real ads).
 * Set TEST_MODE = true only while testing on your own phone, so you never click your own live ads.
 */
object AdIds {
    const val GAME_ID = "800005010"
    const val REWARDED = "Rewarded_Android"
    const val INTERSTITIAL = "Interstitial_Android"

    /** Not used: the game has no banner ads. Kept so the placement name is not forgotten. */
    const val BANNER = "Banner_Android"

    const val TEST_MODE = false

    /** How long a tapped ad button waits for the ad to load (slow internet) before giving up. */
    const val WAIT_MS = 10_000L
}

class AdManager(private val activity: Activity) {
    /** True while a full-screen ad is open; the app covers itself with black so no app UI peeks out
     *  behind the ad's status bar area. */
    var adShowing by mutableStateOf(false)
        private set

    /** Last ad problem. Tap the "Ad not ready" message in the game to see it. */
    var lastError: String = ""
        private set

    private var initStarted = false
    private var ready = false
    private var rewardedReady = false
    private var rewardedLoading = false
    private var interstitialReady = false
    private var interstitialLoading = false
    private var rewardedFails = 0
    private var interstitialFails = 0
    private var lastInterstitialAt = 0L

    // A rewarded ad was requested while it was not loaded yet (e.g. slow internet): it is loaded now
    // and shown as soon as it is ready, or given up after WAIT_MS.
    private var pendingReward: (() -> Unit)? = null
    private var pendingUnavailable: (() -> Unit)? = null
    private var pendingWait: ((Boolean) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finishPending() }

    /** Wait before the next automatic retry: 8s, 16s, 32s, then 60s. Gives up after 6 failed tries. */
    private fun retryDelay(fails: Int): Long = min(60_000L, 8_000L shl min(fails - 1, 3))

    /** Initialises Unity Ads (once) and then preloads the rewarded and interstitial placements. */
    fun loadAll() {
        if (ready) {
            if (rewardedFails >= 6) rewardedFails = 0
            if (interstitialFails >= 6) interstitialFails = 0
            loadRewarded()
            loadInterstitial()
            return
        }
        if (initStarted) return
        initStarted = true
        UnityAds.initialize(
            activity.applicationContext,
            AdIds.GAME_ID,
            AdIds.TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    activity.runOnUiThread {
                        ready = true
                        loadRewarded()
                        loadInterstitial()
                    }
                }

                override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                    activity.runOnUiThread {
                        initStarted = false
                        lastError = "Init: $error - $message"
                        finishPending()
                    }
                }
            }
        )
    }

    fun loadRewarded() {
        if (!ready || rewardedReady || rewardedLoading) return
        rewardedLoading = true
        UnityAds.load(AdIds.REWARDED, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                activity.runOnUiThread {
                    rewardedReady = true
                    rewardedLoading = false
                    rewardedFails = 0
                    val r = pendingReward
                    val u = pendingUnavailable
                    val w = pendingWait
                    if (r != null && u != null) {
                        clearPending()
                        w?.invoke(false)
                        showRewarded(r, u)
                    }
                }
            }

            override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                activity.runOnUiThread {
                    rewardedReady = false
                    rewardedLoading = false
                    rewardedFails++
                    lastError = "Rewarded: $error - $message"
                    finishPending()
                    // try again by itself (no fill / bad network are often temporary)
                    if (rewardedFails < 6) handler.postDelayed({ loadRewarded() }, retryDelay(rewardedFails))
                }
            }
        })
    }

    fun loadInterstitial() {
        if (!ready || interstitialReady || interstitialLoading) return
        interstitialLoading = true
        UnityAds.load(AdIds.INTERSTITIAL, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                activity.runOnUiThread {
                    interstitialReady = true
                    interstitialLoading = false
                    interstitialFails = 0
                }
            }

            override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                activity.runOnUiThread {
                    interstitialReady = false
                    interstitialLoading = false
                    interstitialFails++
                    lastError = "Interstitial: $error - $message"
                    if (interstitialFails < 6) handler.postDelayed({ loadInterstitial() }, retryDelay(interstitialFails))
                }
            }
        })
    }

    private fun clearPending() {
        handler.removeCallbacks(timeout)
        pendingReward = null
        pendingUnavailable = null
        pendingWait = null
    }

    /** Ends a waiting request because it timed out or the load failed. */
    private fun finishPending() {
        val u = pendingUnavailable
        val w = pendingWait
        clearPending()
        w?.invoke(false)
        u?.invoke()
    }

    /**
     * The reward is given only when the video was watched to the end.
     * If the ad is not loaded yet, loading starts now and the ad is shown when it is ready;
     * onWait(true) is called while waiting and onWait(false) when the wait is over.
     */
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit, onWait: (Boolean) -> Unit = {}) {
        if (!ready || !rewardedReady) {
            if (pendingReward != null) return   // already waiting for one
            pendingReward = onReward
            pendingUnavailable = onUnavailable
            pendingWait = onWait
            onWait(true)
            handler.postDelayed(timeout, AdIds.WAIT_MS)
            if (!ready) loadAll() else loadRewarded()
            return
        }
        rewardedReady = false
        adShowing = true
        UnityAds.show(activity, AdIds.REWARDED, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                activity.runOnUiThread {
                    adShowing = false
                    lastError = "Rewarded show: $error - $message"
                    loadRewarded()
                    onUnavailable()
                }
            }

            override fun onUnityAdsShowStart(placementId: String?) {}

            override fun onUnityAdsShowClick(placementId: String?) {}

            override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                activity.runOnUiThread {
                    adShowing = false
                    loadRewarded()
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) onReward()
                }
            }
        })
    }

    /** Shows an interstitial if one is ready (and not more often than every 60 seconds), then calls onDone. */
    fun showInterstitial(onDone: () -> Unit) {
        val now = System.currentTimeMillis()
        if (!ready || !interstitialReady || now - lastInterstitialAt < 60_000L) {
            loadAll()
            onDone()
            return
        }
        lastInterstitialAt = now
        interstitialReady = false
        adShowing = true
        UnityAds.show(activity, AdIds.INTERSTITIAL, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                activity.runOnUiThread {
                    adShowing = false
                    lastError = "Interstitial show: $error - $message"
                    loadInterstitial()
                    onDone()
                }
            }

            override fun onUnityAdsShowStart(placementId: String?) {}

            override fun onUnityAdsShowClick(placementId: String?) {}

            override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                activity.runOnUiThread {
                    adShowing = false
                    loadInterstitial()
                    onDone()
                }
            }
        })
    }
}
