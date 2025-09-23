package marcinlowercase.oo.browser

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.BitmapFactoryDecoder
import coil.decode.SvgDecoder

class CustomApplication : Application(), ImageLoaderFactory {

    /**
     * This method will be called by Coil automatically to create the
     * singleton ImageLoader for the entire app.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                // This is the key. BitmapFactoryDecoder is better at handling
                // oversized images before they hit the GPU texture limits.
                add(SvgDecoder.Factory())
                add(BitmapFactoryDecoder.Factory())
            }
            .crossfade(true) // You can set defaults for all image loads here
            .build()
    }
}