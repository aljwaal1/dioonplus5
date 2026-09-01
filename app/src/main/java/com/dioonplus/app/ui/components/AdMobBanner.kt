package com.dioonplus.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

private const val PRODUCTION_BANNER_AD_UNIT_ID = "ca-app-pub-3082968903080396/6781261228"

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt().coerceAtLeast(320)
        val adSize = remember(widthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                context,
                widthDp,
            )
        }
        val adView = remember(widthDp) {
            AdView(context).apply {
                adUnitId = PRODUCTION_BANNER_AD_UNIT_ID
                setAdSize(adSize)
                loadAd(AdRequest.Builder().build())
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(adSize.height.dp),
            factory = { adView },
        )

        DisposableEffect(adView) {
            onDispose { adView.destroy() }
        }
    }
}
