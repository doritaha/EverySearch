package com.vita.everysearch

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

/** Sets up Coil once: photo thumbnails plus a decoder that grabs a frame from videos. */
class EverySearchApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve("thumbs")).maxSizeBytes(150L * 1024 * 1024).build()
        }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
