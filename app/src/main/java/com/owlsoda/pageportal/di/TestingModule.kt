package com.owlsoda.pageportal.di

import com.owlsoda.pageportal.features.testing.DefaultServiceValidator
import com.owlsoda.pageportal.features.testing.ServiceValidator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TestingModule {

    @Binds
    @Singleton
    abstract fun bindServiceValidator(
        defaultServiceValidator: DefaultServiceValidator
    ): ServiceValidator
}
