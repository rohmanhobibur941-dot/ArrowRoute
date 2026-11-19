package com.jubahir.arrowroute

import android.app.Activity
import android.os.Handler
import android.os.Looper
import kotlin.math.min
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

/**
 * AdMob settings (real ads).
 * To test on your own phone without risking your AdMob account, put your phone's test device id
 * (AdMob prints "Use RequestConfiguration...setTestDeviceIds" with it in logcat) into TEST_DEVICE_IDS.
 * Never click your own live ads.
 */
object AdIds {
    const val INTERSTITIAL = "ca-app-pub-5693977305113658/8949463213"
    const val REWARDED = "ca-app-pub-5693977305113658/5380260008"

    val TEST_DEVICE_IDS: List<String> = emptyList()

    /** How long a tapped ad button waits for the ad to load (slow internet) before giving up. */
    const val WAIT_MS = 10_000L
}

class AdManager(private val activity: Activity) {
    /** True while a full-screen ad is open; the app covers itself with black so no app UI peeks out
     *  behind the ad's status bar area. */
    var adShowing by mutableStateOf(false)
        private set

    /** Last ad problem (e.g. "Rewarded: code 3 - No fill"). Tap the "Ad not ready" message in the game to see it. */
    var lastError: String = ""
        private set

    private var rewardedFails = 0
    private var interstitialFails = 0
    private var lastInterstitialAt = 0L

    private var initStarted = false
    private var ready = false
    private var rewarded: RewardedAd? = null
    private var interstitial: InterstitialAd? = null
    private var rewardedLoading = false
    private var interstitialLoading = false

    // A rewarded ad was requested while it was not loaded yet (e.g. slow internet): it is loaded now
    // and shown as soon as it is ready, or given up after WAIT_MS.
    private var pendingReward: (() -> Unit)? = null
    private var pendingUnavailable: (() -> Unit)? = null
    private var pendingWait: ((Boolean) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finishPending(false) }

    /** Initialises AdMob (once) and then preloads the rewarded and interstitial ads. */
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
        if (AdIds.TEST_DEVICE_IDS.isNotEmpty()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(AdIds.TEST_DEVICE_IDS).build()
            )
        }
        MobileAds.initialize(activity) {
            activity.runOnUiThread {
                ready = true
                loadRewarded()
                loadInterstitial()
            }
        }
    }

    /** Wait before the next automatic retry: 8s, 16s, 32s, then 60s. Gives up after 6 failed tries. */
    private fun retryDelay(fails: Int): Long = min(60_000L, 8_000L shl min(fails - 1, 3))

    fun loadRewarded() {
        if (!ready || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(activity, AdIds.REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                activity.runOnUiThread {
                    rewarded = ad
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

            override fun onAdFailedToLoad(error: LoadAdError) {
                activity.runOnUiThread {
                    rewarded = null
                    rewardedLoading = false
                    rewardedFails++
                    lastError = "Rewarded: code ${error.code} - ${error.message}"
                    finishPending(false)
                    // try again by itself (no fill / bad network are often temporary)
                    if (rewardedFails < 6) handler.postDelayed({ loadRewarded() }, retryDelay(rewardedFails))
                }
            }
        })
    }

    fun loadInterstitial() {
        if (!ready || interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(activity, AdIds.INTERSTITIAL, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                activity.runOnUiThread {
                    interstitial = ad
                    interstitialLoading = false
                    interstitialFails = 0
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                activity.runOnUiThread {
                    interstitial = null
                    interstitialLoading = false
                    interstitialFails++
                    lastError = "Interstitial: code ${error.code} - ${error.message}"
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
        val ad = rewarded
        if (!ready || ad == null) {
            if (pendingReward != null) return   // already waiting for one
            pendingReward = onReward
            pendingUnavailable = onUnavailable
            pendingWait = onWait
            onWait(true)
            handler.postDelayed(timeout, AdIds.WAIT_MS)
            if (!ready) loadAll() else loadRewarded()
            return
        }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adShowing = false
                loadRewarded()
                if (earned) onReward()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adShowing = false
                loadRewarded()
                onUnavailable()
            }
        }
        adShowing = true
        ad.show(activity) { earned = true }
    }

    /** Shows an interstitial if one is ready, then always calls onDone. */
    fun showInterstitial(onDone: () -> Unit) {
        val ad = interstitial
        val now = System.currentTimeMillis()
        if (!ready || ad == null || now - lastInterstitialAt < 60_000L) {
            loadAll()
            onDone()
            return
        }
        lastInterstitialAt = now
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                adShowing = false
                loadInterstitial()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                adShowing = false
                loadInterstitial()
                onDone()
            }
        }
        adShowing = true
        ad.show(activity)
    }
}
