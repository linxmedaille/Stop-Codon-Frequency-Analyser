package com.tapflow.autoclicker

import android.app.Application
import com.tapflow.autoclicker.data.Store

class TapflowApp : Application() {
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = Store(this)
    }

    companion object {
        lateinit var instance: TapflowApp
            private set

        val store: Store get() = instance.store
    }
}
