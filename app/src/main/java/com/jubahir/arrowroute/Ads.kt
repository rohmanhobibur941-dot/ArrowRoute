package com.jubahir.arrowroute

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Google's official TEST ad unit IDs. Safe for development.
 * Before publishing, replace these AND the App ID in AndroidManifest.xml with your real AdMob IDs.
 */
object AdIds {
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
}

class AdManager(private val activity: Activity) {
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false
    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false

    fun loadAll() {
        loadRewarded()
        loadInterstitial()
    }

    fun loadRewarded() {
        if (rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            activity,
            AdIds.REWARDED,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    rewardedLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    rewardedLoading = false
                }
            }
        )
    }

    fun loadInterstitial() {
        if (interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            activity,
            AdIds.INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    interstitialLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    interstitialLoading = false
                }
            }
        )
    }

    /** Reward is given only after the user closes the ad having earned it. */
    fun showRewarded(onReward: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            loadRewarded()
            onUnavailable()
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                loadRewarded()
                if (earned) onReward()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewarded = null
                loadRewarded()
                onUnavailable()
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { earned = true })
    }

    /** Shows an interstitial if one is ready, then always calls onDone. */
    fun showInterstitial(onDone: () -> Unit) {
        val ad = interstitial
        if (ad == null) {
            loadInterstitial()
            onDone()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null
                loadInterstitial()
                onDone()
            }
        }
        ad.show(activity)
    }
}

@Composable
fun BannerAd() {
    AndroidView(factory = { ctx ->
        AdView(ctx).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = AdIds.BANNER
            loadAd(AdRequest.Builder().build())
        }
    })
}
