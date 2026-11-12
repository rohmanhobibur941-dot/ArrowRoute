package com.jubahir.arrowroute

import android.app.Activity
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

/**
 * Unity Ads settings.
 * TEST_MODE = true shows Unity test ads only. Set it to false before publishing the game.
 */
object AdIds {
    const val GAME_ID = "800005010"
    const val REWARDED = "Rewarded_Android"
    const val INTERSTITIAL = "Interstitial_Android"

    /** Not used: the game has no banner ads. Kept so the placement name is not forgotten. */
    const val BANNER = "Banner_Android"

    const val TEST_MODE = true
}

class AdManager(private val activity: Activity) {
    private var initStarted = false
    private var ready = false
    private var rewardedReady = false
    private var rewardedLoading = false
    private var interstitialReady = false
    private var interstitialLoading = false

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
                    activity.runOnUiThread { initStarted = false }
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
                }
            }

            override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                activity.runOnUiThread {
                    rewardedReady = false
                    rewardedLoading = false
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

    /** The reward is given only when the video was watched to the end. */
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit) {
        if (!ready) {
            loadAll()
            onUnavailable()
            return
        }
        if (!rewardedReady) {
            loadRewarded()
            onUnavailable()
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
