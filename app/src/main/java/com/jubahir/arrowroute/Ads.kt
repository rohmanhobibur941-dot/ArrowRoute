package com.jubahir.arrowroute

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

/**
 * Unity Ads settings.
 * TEST_MODE = false serves real ads. Set it to true only while testing, so you never click your own live ads.
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
    private var initStarted = false
    private var ready = false
    private var rewardedReady = false
    private var rewardedLoading = false
    private var interstitialReady = false
    private var interstitialLoading = false

    // A rewarded ad was requested while it was not loaded yet (e.g. slow internet): it is loaded now
    // and shown as soon as it is ready, or given up after WAIT_MS.
    private var pendingReward: (() -> Unit)? = null
    private var pendingUnavailable: (() -> Unit)? = null
    private var pendingWait: ((Boolean) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finishPending(false) }

    /** Initialises Unity Ads (once) and then preloads the rewarded and interstitial placements. */
    fun loadAll() {
        if (ready) {
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
                        finishPending(false)
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
                    if (pendingReward != null) {
                        val r = pendingReward
                        val u = pendingUnavailable
                        val w = pendingWait
                        clearPending()
                        w?.invoke(false)
                        if (r != null && u != null) showRewarded(r, u)
                    }
                }
            }

            override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                activity.runOnUiThread {
                    rewardedReady = false
                    rewardedLoading = false
                    finishPending(false)
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
                }
            }

            override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                activity.runOnUiThread {
                    interstitialReady = false
                    interstitialLoading = false
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

    /** Ends a waiting request: loaded = false means it timed out or the load failed. */
    private fun finishPending(loaded: Boolean) {
        val u = pendingUnavailable
        val w = pendingWait
        clearPending()
        if (!loaded) {
            w?.invoke(false)
            u?.invoke()
        }
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
        UnityAds.show(activity, AdIds.REWARDED, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                activity.runOnUiThread {
                    loadRewarded()
                    onUnavailable()
                }
            }

            override fun onUnityAdsShowStart(placementId: String?) {}

            override fun onUnityAdsShowClick(placementId: String?) {}

            override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                activity.runOnUiThread {
                    loadRewarded()
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) onReward()
                }
            }
        })
    }

    /** Shows an interstitial if one is ready, then always calls onDone. */
    fun showInterstitial(onDone: () -> Unit) {
        if (!ready || !interstitialReady) {
            loadAll()
            onDone()
            return
        }
        interstitialReady = false
        UnityAds.show(activity, AdIds.INTERSTITIAL, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                activity.runOnUiThread {
                    loadInterstitial()
                    onDone()
                }
            }

            override fun onUnityAdsShowStart(placementId: String?) {}

            override fun onUnityAdsShowClick(placementId: String?) {}

            override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                activity.runOnUiThread {
                    loadInterstitial()
                    onDone()
                }
            }
        })
    }
}
