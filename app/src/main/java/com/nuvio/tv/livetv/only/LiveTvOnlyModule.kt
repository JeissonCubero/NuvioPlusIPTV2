package com.nuvio.tv.livetv.only

import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import dagger.Binds
import dagger.Module
import dagger.multibindings.Multibinds
import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live TV only build: Nuvio's addons and catalogs are gone. Live TV still asks for them (program
 * posters, sports streams), so these always answer "nothing installed".
 */
@Singleton
class NoAddonRepository @Inject constructor() : AddonRepository {
    private val none = MutableStateFlow<List<Addon>>(emptyList())
    override fun getInstalledAddons(): Flow<List<Addon>> = none
    override suspend fun fetchAddon(baseUrl: String): NetworkResult<Addon> =
        NetworkResult.Error("Addons are not available in this build")
    override suspend fun addAddon(url: String) = Unit
    override suspend fun removeAddon(url: String) = Unit
    override suspend fun setAddonOrder(urls: List<String>) = Unit
    override suspend fun setAddonEnabled(url: String, enabled: Boolean) = Unit
}

@Singleton
class NoCatalogRepository @Inject constructor() : CatalogRepository {
    override fun getCatalog(
        addonBaseUrl: String,
        addonId: String,
        addonName: String,
        catalogId: String,
        catalogName: String,
        type: String,
        skip: Int,
        skipStep: Int,
        extraArgs: Map<String, String>,
        supportsSkip: Boolean,
        posterScreen: com.nuvio.tv.core.poster.CustomPosterScreen
    ): Flow<NetworkResult<CatalogRow>> = emptyFlow()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveTvOnlyModule {
    @Binds
    @Singleton
    abstract fun bindAddonRepository(impl: NoAddonRepository): AddonRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: NoCatalogRepository): CatalogRepository

    // Profiles clear their saved sign-ins through these; none are left in this build (they were
    // Nuvio's Trakt/Simkl/servers), so the set is empty.
    @Multibinds
    abstract fun credentialStores(): Set<ProfileScopedCredentialStore>
}
