package com.quantumaes.yogatiming.core.datastore.di

import com.quantumaes.yogatiming.core.datastore.session.DataStoreSessionStore
import com.quantumaes.yogatiming.timer.engine.persist.SessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    @Binds
    @Singleton
    abstract fun bindSessionStore(impl: DataStoreSessionStore): SessionStore
}
