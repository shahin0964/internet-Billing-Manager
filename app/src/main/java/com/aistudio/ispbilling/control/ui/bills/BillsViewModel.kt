package com.aistudio.ispbilling.control.ui.bills

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.ispbilling.control.data.local.entity.BillEntity
import com.aistudio.ispbilling.control.data.repository.RepositoryProvider
import com.aistudio.ispbilling.control.data.sync.SyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BillsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        RepositoryProvider.get(application)

    val bills: StateFlow<List<BillEntity>> =
        repository.observeBills()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun addBill(bill: BillEntity) {

        viewModelScope.launch {

            repository.saveBill(bill)

            SyncWorker.enqueue(
                getApplication<Application>()
            )
        }
    }

    fun updateBill(bill: BillEntity) {

        viewModelScope.launch {

            repository.updateBill(bill)

            SyncWorker.enqueue(
                getApplication<Application>()
            )
        }
    }

    fun deleteBill(id: Long) {

        viewModelScope.launch {

            repository.deleteBill(id)

            SyncWorker.enqueue(
                getApplication<Application>()
            )
        }
    }
}
