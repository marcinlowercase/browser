package marcinlowercase.oo.browser

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.BitmapFactoryDecoder
import coil.decode.SvgDecoder
import okhttp3.OkHttpClient

// REMOVE the "ImageLoaderFactory" implementation
class CustomApplication : Application() {

    // The onCreate() method is the first safe place to access context.
    override fun onCreate() {
        super.onCreate() // Always call the superclass method first

        // All the logic is now safely inside onCreate()
        val mobileUserAgent =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        val desktopUserAgent =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

        // This call is now safe
        val prefs = getSharedPreferences("BrowserPrefs", MODE_PRIVATE)
        val isDesktopMode = prefs.getBoolean("is_desktop_mode", false)
        val currentUserAgent = if (isDesktopMode) desktopUserAgent else mobileUserAgent

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newRequest = originalRequest.newBuilder()
                    .header("User-Agent", currentUserAgent)
                    .build()
                chain.proceed(newRequest)
            }
            .build()

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(SvgDecoder.Factory())
                add(BitmapFactoryDecoder.Factory())
            }
            .crossfade(true)
            .build()

        // Set this as the singleton ImageLoader for the entire app.
        Coil.setImageLoader(imageLoader)
    }
}