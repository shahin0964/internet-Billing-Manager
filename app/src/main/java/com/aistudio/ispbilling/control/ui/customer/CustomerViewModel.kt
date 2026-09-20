package com.aistudio.ispbilling.control.ui.customer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.ispbilling.control.data.local.entity.CustomerEntity
import com.aistudio.ispbilling.control.data.repository.RepositoryProvider
import com.aistudio.ispbilling.control.data.sync.SyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        RepositoryProvider.get(application)

    val customers: StateFlow<List<CustomerEntity>> =
        repository.observeCustomers()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun addCustomer(customer: CustomerEntity) {

        viewModelScope.launch {

            repository.saveCustomer(customer)

            SyncWorker.enqueue(
                getApplication<Application>()
            )
        }
    }

    fun updateCustomer(customer: CustomerEntity) {

        viewModelScope.launch {

            repository.updateCustomer(customer)

            SyncWorker.enqueue(
                getApplication<Application>()
            )
        }
    }

    fun deleteCustomer(id: Long) {

        viewModelScope.launch {

            repository.deleteCustomer(id)

            SyncWorker.enqueue(
                getApplication<Application>()
            )
        }
    }
}
