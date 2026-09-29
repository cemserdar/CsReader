package com.anonymous.csreader.ui.epub

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

object EpubPageTransformers {

    // 1. 3D Kitap Kıvrılma & Perspektif (Curl / 3D Book Turn)
    class CurlPageTransformer : ViewPager.PageTransformer {
        override fun transformPage(view: View, position: Float) {
            val density = view.resources.displayMetrics.density
            view.cameraDistance = 16000f * density

            if (position < -1f) {
                view.alpha = 0f
            } else if (position <= 0f) {
                // Sola dönen sayfa
                view.alpha = 1f
                view.pivotX = view.width.toFloat()
                view.pivotY = view.height * 0.5f
                view.rotationY = 45f * position
                val scale = 1f - (abs(position) * 0.08f)
                view.scaleX = scale
                view.scaleY = scale
            } else if (position <= 1f) {
                // Sağdan gelen sayfa
                view.alpha = 1f
                view.pivotX = 0f
                view.pivotY = view.height * 0.5f
                view.rotationY = 45f * position
                val scale = 1f - (abs(position) * 0.08f)
                view.scaleX = scale
                view.scaleY = scale
            } else {
                view.alpha = 0f
            }
        }
    }

    // 2. Apple Books & Kindle Tarzı Derinlikli Kaydırma (Depth Card Slide)
    class DepthPageTransformer : ViewPager.PageTransformer {
        override fun transformPage(view: View, position: Float) {
            val pageWidth = view.width.toFloat()
            if (position < -1f) {
                view.alpha = 0f
            } else if (position <= 0f) {
                // Normal yatay kayarak çıkan sayfa
                view.alpha = 1f
                view.translationX = 0f
                view.scaleX = 1f
                view.scaleY = 1f
            } else if (position <= 1f) {
                // Altta kalan arka plan sayfası (hafif derinlik ve yumuşak kararma)
                view.alpha = 1f - (position * 0.35f)
                view.translationX = pageWidth * -position
                val scaleFactor = 0.90f + (1f - 0.90f) * (1f - abs(position))
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
            } else {
                view.alpha = 0f
            }
        }
    }

    // 3. Yumuşak Erime / Çapraz Geçiş (Fade Dissolve)
    class FadePageTransformer : ViewPager.PageTransformer {
        override fun transformPage(view: View, position: Float) {
            view.translationX = view.width.toFloat() * -position
            if (position in -1f..1f) {
                view.alpha = 1f - abs(position)
            } else {
                view.alpha = 0f
            }
        }
    }

    /**
     * Readium EPUB view hiyerarşisine seçilen geçiş efektini uygular.
     */
    fun applyTransition(rootView: View?, transition: String) {
        if (rootView == null) return

        // 1. ViewPager'ı bul ve seçilen sayfa geçiş efektini bağla
        val viewPager = findViewPager(rootView)
        if (viewPager != null) {
            when (transition.lowercase()) {
                "curl" -> viewPager.setPageTransformer(true, CurlPageTransformer())
                "fade" -> viewPager.setPageTransformer(true, FadePageTransformer())
                "slide" -> viewPager.setPageTransformer(true, DepthPageTransformer())
                else -> viewPager.setPageTransformer(false, null)
            }
        }

        // 2. Tüm WebView'lara donanım hızlandırması ve CSS smooth-scroll enjekte et
        enhanceWebViews(rootView)
    }

    private fun findViewPager(view: View): ViewPager? {
        if (view is ViewPager) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findViewPager(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun enhanceWebViews(view: View) {
        if (view is WebView) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val js = """
                (function() {
                    try {
                        var id = 'csreader-smooth-scroll-style';
                        if (!document.getElementById(id)) {
                            var style = document.createElement('style');
                            style.id = id;
                            style.innerHTML = 'html { scroll-behavior: smooth !important; } body { -webkit-font-smoothing: antialiased; }';
                            document.head.appendChild(style);
                        }
                    } catch(e) {}
                })();
            """.trimIndent()
            view.evaluateJavascript(js, null)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                enhanceWebViews(view.getChildAt(i))
            }
        }
    }
}
