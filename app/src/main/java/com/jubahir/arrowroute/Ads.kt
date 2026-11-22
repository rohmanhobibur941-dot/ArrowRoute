package com.jubahir.arrowroute

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import kotlin.math.min

/**
 * Ad settings. Both networks serve real ads.
 * To test on your own phone without risking your accounts, put your phone's AdMob test device id into
 * ADMOB_TEST_DEVICE_IDS (AdMob prints it in logcat) and/or set UNITY_TEST_MODE = true.
 * Never click your own live ads.
 */
object AdIds {
    // AdMob
    const val ADMOB_INTERSTITIAL = "ca-app-pub-5693977305113658/8949463213"
    const val ADMOB_REWARDED = "ca-app-pub-5693977305113658/5380260008"
    val ADMOB_TEST_DEVICE_IDS: List<String> = emptyList()

    // Unity Ads
    const val UNITY_GAME_ID = "800005010"
    const val UNITY_REWARDED = "Rewarded_Android"
    const val UNITY_INTERSTITIAL = "Interstitial_Android"
    const val UNITY_TEST_MODE = false

    /** How long a tapped ad button waits for an ad to load (slow internet) before giving up. */
    const val WAIT_MS = 10_000L
}

/**
 * Ad mediation inside the app (a "waterfall"):
 *  1. Both networks are started in the background. AdMob is asked first.
 *  2. If AdMob has no ad (error / no fill), Unity Ads is loaded.
 *  3. When the player wants an ad, an AdMob ad is shown if it is loaded, otherwise a Unity ad.
 * Rewarded ads give the reward only when the video was watched to the end.
 */
class AdManager(private val activity: Activity) {
    /** True while a full-screen ad is open; the app covers itself with black so no app UI peeks out
     *  behind the ad's status bar area. */
    var adShowing by mutableStateOf(false)
        private set

    private var admobError = ""
    private var unityError = ""

    /** Last problem of each network. Tap the "Ad not ready" message in the game to see it. */
    val lastError: String
        get() = listOf(admobError, unityError).filter { it.isNotEmpty() }.joinToString("\n")

    private val handler = Handler(Looper.getMainLooper())

    // ---- AdMob state
    private var admobInitStarted = false
    private var admobReady = false
    private var admobRewarded: RewardedAd? = null
    private var admobInterstitial: InterstitialAd? = null
    private var admobRewardedLoading = false
    private var admobInterstitialLoading = false
    private var admobRewardedFails = 0
    private var admobInterstitialFails = 0

    // ---- Unity state
    private var unityInitStarted = false
    private var unityReady = false
    private var unityRewardedReady = false
    private var unityInterstitialReady = false
    private var unityRewardedLoading = false
    private var unityInterstitialLoading = false
    private var unityRewardedFails = 0
    private var unityInterstitialFails = 0

    private var lastInterstitialAt = 0L

    // A rewarded ad was requested while none was loaded (e.g. slow internet): both networks are asked
    // now and the first ad that arrives is shown; after WAIT_MS (or when both failed) it is given up.
    private var pendingReward: (() -> Unit)? = null
    private var pendingUnavailable: (() -> Unit)? = null
    private var pendingWait: ((Boolean) -> Unit)? = null
    private var waitAdmobFailed = false
    private var waitUnityFailed = false
    private val timeout = Runnable { finishPending() }

    /** Wait before the next automatic retry: 8s, 16s, 32s, then 60s. Gives up after 6 failed tries. */
    private fun retryDelay(fails: Int): Long = min(60_000L, 8_000L shl min(fails - 1, 3))

    /** Starts both SDKs (once) and preloads the ads in the background. */
    fun loadAll() {
        if (admobRewardedFails >= 6) admobRewardedFails = 0
        if (admobInterstitialFails >= 6) admobInterstitialFails = 0
        if (unityRewardedFails >= 6) unityRewardedFails = 0
        if (unityInterstitialFails >= 6) unityInterstitialFails = 0
        initAdmob()
        initUnity()
        fillRewarded()
        fillInterstitial()
    }

    // =========================================================== init

    private fun initAdmob() {
        if (admobReady || admobInitStarted) return
        admobInitStarted = true
        if (AdIds.ADMOB_TEST_DEVICE_IDS.isNotEmpty()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(AdIds.ADMOB_TEST_DEVICE_IDS).build()
            )
        }
        MobileAds.initialize(activity) {
            activity.runOnUiThread {
                admobReady = true
                fillRewarded()
                fillInterstitial()
                if (pendingReward != null) loadAdmobRewarded()
            }
        }
    }

    private fun initUnity() {
        if (unityReady || unityInitStarted) return
        unityInitStarted = true
        UnityAds.initialize(
            activity.applicationContext,
            AdIds.UNITY_GAME_ID,
            AdIds.UNITY_TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    activity.runOnUiThread {
                        unityReady = true
                        // AdMob already had no ad while Unity was starting: use Unity now
                        if (admobRewarded == null && (admobRewardedFails > 0 || pendingReward != null)) loadUnityRewarded()
                        if (admobInterstitial == null && admobInterstitialFails > 0) loadUnityInterstitial()
                    }
                }

                override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                    activity.runOnUiThread {
                        unityInitStarted = false
                        unityError = "Unity init: $error - $message"
                        waitUnityFailed = true
                        checkPendingFailed()
                    }
                }
            }
        )
    }

    // =========================================================== waterfall: AdMob first, Unity next

    private fun fillRewarded() {
        if (admobRewarded != null || unityRewardedReady) return
        if (admobReady) loadAdmobRewarded()
    }

    private fun fillInterstitial() {
        if (admobInterstitial != null || unityInterstitialReady) return
        if (admobReady) loadAdmobInterstitial()
    }

    // =========================================================== AdMob loading

    private fun loadAdmobRewarded() {
        if (!admobReady || admobRewarded != null || admobRewardedLoading) return
        admobRewardedLoading = true
        RewardedAd.load(activity, AdIds.ADMOB_REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                activity.runOnUiThread {
                    admobRewarded = ad
                    admobRewardedLoading = false
                    admobRewardedFails = 0
                    servePending()
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                activity.runOnUiThread {
                    admobRewarded = null
                    admobRewardedLoading = false
                    admobRewardedFails++
                    admobError = "AdMob rewarded: code ${error.code} - ${error.message}"
                    waitAdmobFailed = true
                    loadUnityRewarded()   // AdMob has no ad: ask Unity
                    checkPendingFailed()
                    if (admobRewardedFails < 6) handler.postDelayed({ fillRewarded() }, retryDelay(admobRewardedFails))
                }
            }
        })
    }

    private fun loadAdmobInterstitial() {
        if (!admobReady || admobInterstitial != null || admobInterstitialLoading) return
        admobInterstitialLoading = true
        InterstitialAd.load(activity, AdIds.ADMOB_INTERSTITIAL, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                activity.runOnUiThread {
                    admobInterstitial = ad
                    admobInterstitialLoading = false
                    admobInterstitialFails = 0
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                activity.runOnUiThread {
                    admobInterstitial = null
                    admobInterstitialLoading = false
                    admobInterstitialFails++
                    admobError = "AdMob interstitial: code ${error.code} - ${error.message}"
                    loadUnityInterstitial()   // AdMob has no ad: ask Unity
                    if (admobInterstitialFails < 6) handler.postDelayed({ fillInterstitial() }, retryDelay(admobInterstitialFails))
                }
            }
        })
    }

    // =========================================================== Unity loading

    private fun loadUnityRewarded() {
        if (!unityReady || unityRewardedReady || unityRewardedLoading) return
        unityRewardedLoading = true
        UnityAds.load(AdIds.UNITY_REWARDED, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                activity.runOnUiThread {
                    unityRewardedReady = true
                    unityRewardedLoading = false
                    unityRewardedFails = 0
                    servePending()
                }
            }

            override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                activity.runOnUiThread {
                    unityRewardedReady = false
                    unityRewardedLoading = false
                    unityRewardedFails++
                    unityError = "Unity rewarded: $error - $message"
                    waitUnityFailed = true
                    checkPendingFailed()
                    if (unityRewardedFails < 6) {
                        handler.postDelayed({
                            if (admobRewarded == null) loadUnityRewarded()
                        }, retryDelay(unityRewardedFails))
                    }
                }
            }
        })
    }

    private fun loadUnityInterstitial() {
        if (!unityReady || unityInterstitialReady || unityInterstitialLoading) return
        unityInterstitialLoading = true
        UnityAds.load(AdIds.UNITY_INTERSTITIAL, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String?) {
                activity.runOnUiThread {
                    unityInterstitialReady = true
                    unityInterstitialLoading = false
                    unityInterstitialFails = 0
                }
            }

            override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
                activity.runOnUiThread {
                    unityInterstitialReady = false
                    unityInterstitialLoading = false
                    unityInterstitialFails++
                    unityError = "Unity interstitial: $error - $message"
                    if (unityInterstitialFails < 6) {
                        handler.postDelayed({
                            if (admobInterstitial == null) loadUnityInterstitial()
                        }, retryDelay(unityInterstitialFails))
                    }
                }
            }
        })
    }

    // =========================================================== waiting for a rewarded ad

    private fun clearPending() {
        handler.removeCallbacks(timeout)
        pendingReward = null
        pendingUnavailable = null
        pendingWait = null
    }

    /** Gives up the waiting request (timed out, or both networks failed). */
    private fun finishPending() {
        val u = pendingUnavailable
        val w = pendingWait
        clearPending()
        w?.invoke(false)
        u?.invoke()
    }

    private fun checkPendingFailed() {
        if (pendingReward != null && waitAdmobFailed && waitUnityFailed) finishPending()
    }

    /** An ad just arrived: if a rewarded request is waiting, show it now. */
    private fun servePending() {
        val r = pendingReward
        val u = pendingUnavailable
        val w = pendingWait
        if (r == null || u == null) return
        clearPending()
        w?.invoke(false)
        showRewarded(r, u)
    }

    // =========================================================== show: rewarded

    /**
     * The reward is given only when the video was watched to the end.
     * Shows an AdMob ad if one is loaded, else a Unity ad. If none is loaded yet, both networks are
     * asked now and the first ad that arrives is shown; onWait(true) is called while waiting and
     * onWait(false) when the wait is over.
     */
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit, onWait: (Boolean) -> Unit = {}) {
        val ad = admobRewarded
        if (admobReady && ad != null) {
            showAdmobRewarded(ad, onReward, onUnavailable)
            return
        }
        if (unityReady && unityRewardedReady) {
            showUnityRewarded(onReward, onUnavailable)
            return
        }
        if (pendingReward != null) return   // already waiting for one
        pendingReward = onReward
        pendingUnavailable = onUnavailable
        pendingWait = onWait
        waitAdmobFailed = false
        waitUnityFailed = false
        onWait(true)
        handler.postDelayed(timeout, AdIds.WAIT_MS)
        loadAll()
        loadAdmobRewarded()
        loadUnityRewarded()
    }

    private fun showAdmobRewarded(ad: RewardedAd, onReward: () -> Unit, onUnavailable: () -> Unit) {
        admobRewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adShowing = false
                fillRewarded()
                if (earned) onReward()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adShowing = false
                admobError = "AdMob rewarded show: ${error.code} - ${error.message}"
                fillRewarded()
                // AdMob could not show it: try Unity
                if (unityReady && unityRewardedReady) showUnityRewarded(onReward, onUnavailable) else onUnavailable()
            }
        }
        adShowing = true
        ad.show(activity) { earned = true }
    }

    private fun showUnityRewarded(onReward: () -> Unit, onUnavailable: () -> Unit) {
        unityRewardedReady = false
        adShowing = true
        UnityAds.show(activity, AdIds.UNITY_REWARDED, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                activity.runOnUiThread {
                    adShowing = false
                    unityError = "Unity rewarded show: $error - $message"
                    fillRewarded()
                    if (admobRewarded == null) loadUnityRewarded()
                    onUnavailable()
                }
            }

            override fun onUnityAdsShowStart(placementId: String?) {}

            override fun onUnityAdsShowClick(placementId: String?) {}

            override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                activity.runOnUiThread {
                    adShowing = false
                    fillRewarded()   // next time AdMob gets the first chance again
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) onReward()
                }
            }
        })
    }

    // =========================================================== show: interstitial

    /** Shows an interstitial (AdMob first, else Unity) if one is ready and the last one was over a
     *  minute ago, then always calls onDone. */
    fun showInterstitial(onDone: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastInterstitialAt < 60_000L) {
            fillInterstitial()
            onDone()
            return
        }
        val ad = admobInterstitial
        if (admobReady && ad != null) {
            lastInterstitialAt = now
            showAdmobInterstitial(ad, onDone)
            return
        }
        if (unityReady && unityInterstitialReady) {
            lastInterstitialAt = now
            showUnityInterstitial(onDone)
            return
        }
        loadAll()
        onDone()
    }

    private fun showAdmobInterstitial(ad: InterstitialAd, onDone: () -> Unit) {
        admobInterstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adShowing = false
                fillInterstitial()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adShowing = false
                admobError = "AdMob interstitial show: ${error.code} - ${error.message}"
                fillInterstitial()
                if (unityReady && unityInterstitialReady) showUnityInterstitial(onDone) else onDone()
            }
        }
        adShowing = true
        ad.show(activity)
    }

    private fun showUnityInterstitial(onDone: () -> Unit) {
        unityInterstitialReady = false
        adShowing = true
        UnityAds.show(activity, AdIds.UNITY_INTERSTITIAL, UnityAdsShowOptions(), object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                activity.runOnUiThread {
                    adShowing = false
                    unityError = "Unity interstitial show: $error - $message"
                    fillInterstitial()
                    if (admobInterstitial == null) loadUnityInterstitial()
                    onDone()
                }
            }

            override fun onUnityAdsShowStart(placementId: String?) {}

            override fun onUnityAdsShowClick(placementId: String?) {}

            override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                activity.runOnUiThread {
                    adShowing = false
                    fillInterstitial()
                    onDone()
                }
            }
        })
    }
}
