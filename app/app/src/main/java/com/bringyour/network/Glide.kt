package com.bringyour.network

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule


@GlideModule
class BringYourGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
//        val memoryCacheSizeBytes = 128 * 1024 * 1024L
//        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes))
//
//        val bitmapPoolSizeBytes = 1024 * 1024 * 128L
//        builder.setBitmapPool(LruBitmapPool(bitmapPoolSizeBytes))

        builder.setDiskCache(null)
    }
}
