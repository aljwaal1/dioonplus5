package com.dioonplus.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val BANNER_AD_UNIT_ID = "ca-app-pub-3082968903080396/6840406325"

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = BANNER_AD_UNIT_ID,
) {
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
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
