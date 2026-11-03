package com.musarum.mobile.data.library

import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.AddFolderRequest
import com.musarum.mobile.data.network.dto.DiscoveredFolderDto
import com.musarum.mobile.data.network.dto.LibraryFolderDto

class LibraryFolderRepository(private val api: ApiService) {

    suspend fun list(): List<LibraryFolderDto> = api.listLibraryFolders()

    suspend fun discoverable(): List<DiscoveredFolderDto> = api.listDiscoverableFolders()

    suspend fun add(path: String): LibraryFolderDto =
        api.addLibraryFolder(AddFolderRequest(path))

    suspend fun toggle(id: Long, active: Boolean): LibraryFolderDto =
        api.toggleLibraryFolder(id, active)

    suspend fun delete(id: Long) {
        api.deleteLibraryFolder(id)
    }
}
