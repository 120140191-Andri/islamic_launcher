package com.kraftech.islamic_launcher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import io.flutter.plugins.googlemobileads.NativeAdFactory

class AppTileNativeAdFactory(private val context: Context) : NativeAdFactory {
    override fun createNativeAd(nativeAd: NativeAd, customOptions: MutableMap<String, Any>?): NativeAdView {
        val view = LayoutInflater.from(context).inflate(R.layout.native_ad_app_tile, null) as NativeAdView
        val iconView = view.findViewById<ImageView>(R.id.ad_app_icon)
        val headlineView = view.findViewById<TextView>(R.id.ad_headline)
        val badgeView = view.findViewById<TextView>(R.id.ad_badge)

        badgeView?.text = "Ad"

        val icon = nativeAd.icon
        if (icon != null && iconView != null) {
            iconView.setImageDrawable(icon.drawable)
            view.iconView = iconView
            iconView.visibility = View.VISIBLE
        }

        if (headlineView != null) {
            headlineView.text = nativeAd.headline ?: "Sponsor"
            view.headlineView = headlineView
        }

        view.setNativeAd(nativeAd)
        return view
    }
}

class HorizontalNativeAdFactory(private val context: Context) : NativeAdFactory {
    override fun createNativeAd(nativeAd: NativeAd, customOptions: MutableMap<String, Any>?): NativeAdView {
        val view = LayoutInflater.from(context).inflate(R.layout.native_ad_horizontal, null) as NativeAdView
        val iconView = view.findViewById<ImageView>(R.id.ad_app_icon)
        val headlineView = view.findViewById<TextView>(R.id.ad_headline)
        val bodyView = view.findViewById<TextView>(R.id.ad_body)
        val advertiserView = view.findViewById<TextView>(R.id.ad_advertiser)
        val ctaView = view.findViewById<TextView>(R.id.ad_call_to_action)

        val icon = nativeAd.icon
        if (icon != null && iconView != null) {
            iconView.setImageDrawable(icon.drawable)
            view.iconView = iconView
            iconView.visibility = View.VISIBLE
        } else {
            iconView?.visibility = View.GONE
        }

        if (headlineView != null) {
            headlineView.text = nativeAd.headline
            view.headlineView = headlineView
        }

        if (bodyView != null) {
            bodyView.text = nativeAd.body ?: ""
            view.bodyView = bodyView
        }

        if (advertiserView != null) {
            advertiserView.text = nativeAd.advertiser ?: (nativeAd.store ?: "")
            view.advertiserView = advertiserView
        }

        if (ctaView != null) {
            ctaView.text = nativeAd.callToAction ?: "Buka"
            view.callToActionView = ctaView
        }

        view.setNativeAd(nativeAd)
        return view
    }
}

class MediumNativeAdFactory(private val context: Context) : NativeAdFactory {
    override fun createNativeAd(nativeAd: NativeAd, customOptions: MutableMap<String, Any>?): NativeAdView {
        val view = LayoutInflater.from(context).inflate(R.layout.native_ad_medium, null) as NativeAdView
        val iconView = view.findViewById<ImageView>(R.id.ad_app_icon)
        val headlineView = view.findViewById<TextView>(R.id.ad_headline)
        val bodyView = view.findViewById<TextView>(R.id.ad_body)
        val advertiserView = view.findViewById<TextView>(R.id.ad_advertiser)
        val ctaView = view.findViewById<TextView>(R.id.ad_call_to_action)
        val mediaView = view.findViewById<MediaView>(R.id.ad_media)

        val icon = nativeAd.icon
        if (icon != null && iconView != null) {
            iconView.setImageDrawable(icon.drawable)
            view.iconView = iconView
            iconView.visibility = View.VISIBLE
        } else {
            iconView?.visibility = View.GONE
        }

        if (mediaView != null) {
            view.mediaView = mediaView
        }

        if (headlineView != null) {
            headlineView.text = nativeAd.headline
            view.headlineView = headlineView
        }

        if (bodyView != null) {
            bodyView.text = nativeAd.body ?: ""
            view.bodyView = bodyView
        }

        if (advertiserView != null) {
            advertiserView.text = nativeAd.advertiser ?: (nativeAd.store ?: "")
            view.advertiserView = advertiserView
        }

        if (ctaView != null) {
            ctaView.text = nativeAd.callToAction ?: "Lihat"
            view.callToActionView = ctaView
        }

        view.setNativeAd(nativeAd)
        return view
    }
}

class ShowcaseNativeAdFactory(private val context: Context) : NativeAdFactory {
    override fun createNativeAd(nativeAd: NativeAd, customOptions: MutableMap<String, Any>?): NativeAdView {
        val view = LayoutInflater.from(context).inflate(R.layout.native_ad_showcase, null) as NativeAdView
        val iconView = view.findViewById<ImageView>(R.id.ad_app_icon)
        val headlineView = view.findViewById<TextView>(R.id.ad_headline)
        val bodyView = view.findViewById<TextView>(R.id.ad_body)
        val advertiserView = view.findViewById<TextView>(R.id.ad_advertiser)
        val ctaView = view.findViewById<TextView>(R.id.ad_call_to_action)
        val mediaView = view.findViewById<MediaView>(R.id.ad_media)

        val icon = nativeAd.icon
        if (icon != null && iconView != null) {
            iconView.setImageDrawable(icon.drawable)
            view.iconView = iconView
            iconView.visibility = View.VISIBLE
        } else {
            iconView?.visibility = View.GONE
        }

        if (mediaView != null) {
            view.mediaView = mediaView
        }

        if (headlineView != null) {
            headlineView.text = nativeAd.headline
            view.headlineView = headlineView
        }

        if (bodyView != null) {
            bodyView.text = nativeAd.body ?: ""
            view.bodyView = bodyView
        }

        if (advertiserView != null) {
            advertiserView.text = nativeAd.advertiser ?: (nativeAd.store ?: "")
            view.advertiserView = advertiserView
        }

        if (ctaView != null) {
            ctaView.text = nativeAd.callToAction ?: "Install Sekarang"
            view.callToActionView = ctaView
        }

        view.setNativeAd(nativeAd)
        return view
    }
}

class Square2x2NativeAdFactory(private val context: Context) : NativeAdFactory {
    override fun createNativeAd(nativeAd: NativeAd, customOptions: MutableMap<String, Any>?): NativeAdView {
        val view = LayoutInflater.from(context).inflate(R.layout.native_ad_square_2x2, null) as NativeAdView
        val iconView = view.findViewById<ImageView>(R.id.ad_app_icon)
        val headlineView = view.findViewById<TextView>(R.id.ad_headline)
        val bodyView = view.findViewById<TextView>(R.id.ad_body)
        val advertiserView = view.findViewById<TextView>(R.id.ad_advertiser)
        val ctaView = view.findViewById<TextView>(R.id.ad_call_to_action)

        val icon = nativeAd.icon
        if (icon != null && iconView != null) {
            iconView.setImageDrawable(icon.drawable)
            view.iconView = iconView
            iconView.visibility = View.VISIBLE
        } else {
            iconView?.visibility = View.GONE
        }

        if (headlineView != null) {
            headlineView.text = nativeAd.headline
            view.headlineView = headlineView
        }

        if (bodyView != null) {
            bodyView.text = nativeAd.body ?: ""
            view.bodyView = bodyView
        }

        if (advertiserView != null) {
            advertiserView.text = nativeAd.advertiser ?: (nativeAd.store ?: "")
            view.advertiserView = advertiserView
        }

        if (ctaView != null) {
            ctaView.text = nativeAd.callToAction ?: "Buka"
            view.callToActionView = ctaView
        }

        view.setNativeAd(nativeAd)
        return view
    }
}

class HomeScreenNativeAdFactory(private val context: Context) : NativeAdFactory {
    override fun createNativeAd(nativeAd: NativeAd, customOptions: MutableMap<String, Any>?): NativeAdView {
        val view = LayoutInflater.from(context).inflate(R.layout.native_ad_homescreen, null) as NativeAdView
        val iconView = view.findViewById<ImageView>(R.id.ad_app_icon)
        val headlineView = view.findViewById<TextView>(R.id.ad_headline)
        val bodyView = view.findViewById<TextView>(R.id.ad_body)
        val advertiserView = view.findViewById<TextView>(R.id.ad_advertiser)
        val ctaView = view.findViewById<TextView>(R.id.ad_call_to_action)

        val icon = nativeAd.icon
        if (icon != null && iconView != null) {
            iconView.setImageDrawable(icon.drawable)
            view.iconView = iconView
            iconView.visibility = View.VISIBLE
        } else {
            iconView?.visibility = View.GONE
        }

        if (headlineView != null) {
            headlineView.text = nativeAd.headline
            view.headlineView = headlineView
        }

        if (bodyView != null) {
            bodyView.text = nativeAd.body ?: ""
            view.bodyView = bodyView
        }

        if (advertiserView != null) {
            advertiserView.text = nativeAd.advertiser ?: (nativeAd.store ?: "")
            view.advertiserView = advertiserView
        }

        if (ctaView != null) {
            ctaView.text = nativeAd.callToAction ?: "Buka"
            view.callToActionView = ctaView
        }

        view.setNativeAd(nativeAd)
        return view
    }
}


