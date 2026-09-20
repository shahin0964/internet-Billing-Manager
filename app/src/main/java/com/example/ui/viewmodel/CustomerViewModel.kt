package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Customer
import com.example.data.model.AddCustomerRequest
import com.example.data.repository.IspRepository
import com.example.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CustomerViewModel(private val repository: IspRepository) : ViewModel() {

    private val _customers = MutableStateFlow<Resource<List<Customer>>>(Resource.Loading)
    val customers: StateFlow<Resource<List<Customer>>> = _customers.asStateFlow()

    private val _addCustomerState = MutableStateFlow<Resource<Unit>?>(null)
    val addCustomerState: StateFlow<Resource<Unit>?> = _addCustomerState.asStateFlow()

    fun loadCustomers(userId: String) {
        viewModelScope.launch {
            _customers.value = Resource.Loading
            repository.getCustomers(userId).collectLatest { resource ->
                _customers.value = resource
            }
        }
    }

    fun addCustomer(userId: String, request: AddCustomerRequest) {
        viewModelScope.launch {
            _addCustomerState.value = Resource.Loading
            repository.addCustomer(request).collectLatest { resource ->
                _addCustomerState.value = resource
                if (resource is Resource.Success) {
                    loadCustomers(userId)
                }
            }
        }
    }

    fun resetAddCustomerState() {
        _addCustomerState.value = null
    }
}
