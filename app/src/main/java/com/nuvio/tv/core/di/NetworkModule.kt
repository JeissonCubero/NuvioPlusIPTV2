package com.nuvio.tv.core.di

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.network.IPv4FirstDns
import com.nuvio.tv.core.network.NoDelaySocketFactory
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.data.remote.api.TmdbApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/** Live TV only build: the network pieces Live TV still uses (TMDB look-ups, Sports' addon API). */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .socketFactory(NoDelaySocketFactory)
            .cache(Cache(File(context.cacheDir, "http_cache_v2"), 50L * 1024 * 1024))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val version = BuildConfig.NUVIO_BASE_VERSION.ifBlank { BuildConfig.VERSION_NAME }.ifBlank { "dev" }
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Nuvio/$version")
                        .build()
                )
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://placeholder.nuvio.tv/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    @Named("tmdb")
    fun provideTmdbRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideAddonApi(retrofit: Retrofit): AddonApi =
        retrofit.create(AddonApi::class.java)

    @Provides
    @Singleton
    fun provideTmdbApi(@Named("tmdb") retrofit: Retrofit): TmdbApi =
        retrofit.create(TmdbApi::class.java)
}
