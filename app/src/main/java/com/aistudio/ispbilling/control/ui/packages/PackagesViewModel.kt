package com.aistudio.ispbilling.control.ui.packages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.ispbilling.control.data.local.entity.PackageEntity
import com.aistudio.ispbilling.control.data.repository.RepositoryProvider
import com.aistudio.ispbilling.control.data.sync.SyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PackagesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        RepositoryProvider.get(application)

    val packages: StateFlow<List<PackageEntity>> =
        repository.observePackages()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun addPackage(packageEntity: PackageEntity) {

        viewModelScope.launch {

            repository.savePackage(packageEntity)

            SyncWorker.enqueue(
                getApplication<Application>()
            )
        }
    }

    fun updatePackage(packageEntity: PackageEntity) {

        viewModelScope.launch {

            repository.updatePackage(packageEntity)

            SyncWorker.enqueue(
                getApplication<Application>()
            )
        }
    }

    fun deletePackage(id: Long) {

        viewModelScope.launch {

            repository.deletePackage(id)

            SyncWorker.enqueue(
                getApplication<Application>()
            )
        }
    }
}
