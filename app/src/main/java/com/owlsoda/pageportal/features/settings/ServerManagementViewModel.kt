package com.owlsoda.pageportal.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owlsoda.pageportal.core.database.dao.ServerDao
import com.owlsoda.pageportal.core.database.entity.ServerEntity
import com.owlsoda.pageportal.services.ServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val serviceManager: ServiceManager
) : ViewModel() {

    val servers: StateFlow<List<ServerEntity>> = serverDao.getAllServers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            // Remove from ServiceManager (cleans up active connections)
            serviceManager.removeServer(server.id)
            // Remove from Database (cascading delete triggers if configured, otherwise manual cleanup might be needed)
            // Assuming DB is set up to cascade or we just remove the server config
            serverDao.deleteServer(server)
        }
    }
}
