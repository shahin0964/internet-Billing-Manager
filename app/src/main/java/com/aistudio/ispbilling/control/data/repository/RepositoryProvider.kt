package com.aistudio.ispbilling.control.data.repository

import android.content.Context
import com.aistudio.ispbilling.control.data.local.AppDatabase
import com.aistudio.ispbilling.control.data.network.ApiClient

object RepositoryProvider {

    @Volatile
    private var instance: OfflineFirstRepository? = null

    fun get(context: Context): OfflineFirstRepository {

        return instance ?: synchronized(this) {

            instance ?: run {

                val database =
                    AppDatabase.getInstance(context)

                OfflineFirstRepository(
                    apiService = ApiClient.apiService,
                    customerDao = database.customerDao(),
                    packageDao = database.packageDao(),
                    billDao = database.billDao()
                ).also {
                    instance = it
                }
            }
        }
    }
}
