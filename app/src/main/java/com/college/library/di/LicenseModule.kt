package com.college.library.di

import android.app.Application
import com.college.library.license.LicenseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LicenseModule {

    @Provides
    @Singleton
    fun provideLicenseManager(app: Application): LicenseManager {
        return LicenseManager(app)
    }
}
