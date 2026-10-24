package com.absensiapp.school

import android.app.Application
import com.absensiapp.school.data.repository.AppRepository

class AbsensiApplication : Application() {

    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository.getInstance(this)
    }
}
