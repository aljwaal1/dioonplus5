package com.dioonplus.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

private const val NEW_ADMOB_BANNER_AD_UNIT_ID = "ca-app-pub-3082968903080396/6781261228"

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("AdMob NEW: loading...") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = status)

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widthDp = maxWidth.value.toInt().coerceAtLeast(320)
            val adView = remember(widthDp) {
                AdView(context).apply {
                    adUnitId = NEW_ADMOB_BANNER_AD_UNIT_ID
                    setAdSize(
                        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                            context,
                            widthDp,
                        ),
                    )
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            status = "AdMob NEW: LOADED"
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            status = "AdMob NEW: FAILED | code=${error.code} | ${error.message}"
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { adView },
            )

            DisposableEffect(adView) {
                onDispose { adView.destroy() }
            }
        }
    }
}
