package com.dioonplus.app.ui.components

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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

private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
private const val PRODUCTION_BANNER_AD_UNIT_ID = "ca-app-pub-3082968903080396/6840406325"

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val adUnitId = if (isDebuggable) TEST_BANNER_AD_UNIT_ID else PRODUCTION_BANNER_AD_UNIT_ID
    var diagnosticStatus by remember(adUnitId) {
        mutableStateOf("AdMob: loading | ${if (isDebuggable) "TEST" else "PRODUCTION"}")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = diagnosticStatus,
            style = MaterialTheme.typography.labelSmall,
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widthDp = maxWidth.value.toInt().coerceAtLeast(320)
            val adView = remember(widthDp, adUnitId) {
                AdView(context).apply {
                    this.adUnitId = adUnitId
                    setAdSize(
                        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                            context,
                            widthDp,
                        ),
                    )
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            diagnosticStatus = "AdMob: LOADED | ${if (isDebuggable) "TEST" else "PRODUCTION"}"
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            diagnosticStatus = "AdMob: FAILED | code=${error.code} | ${error.message}"
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
