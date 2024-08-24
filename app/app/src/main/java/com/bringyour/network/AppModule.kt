package com.bringyour.network

import android.app.Application
import com.bringyour.client.BringYourDevice
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBringYourDevice(application: Application): BringYourDevice? {
        // Cast the application to MainApplication to access byDevice
        // and initiate our gomobile states inside of our ViewModels
        return (application as MainApplication).byDevice
    }
}
