package com.philpf.dualsensemanager

import android.app.Application
import com.philpf.dualsensemanager.device.ControllerManager
import com.philpf.dualsensemanager.device.ProfileStore
import com.philpf.dualsensemanager.service.LightKeeperService

class DualsenseApp : Application() {

    lateinit var store: ProfileStore
        private set

    lateinit var manager: ControllerManager
        private set

    override fun onCreate() {
        super.onCreate()
        store = ProfileStore(this)
        manager = ControllerManager(this, store) { LightKeeperService.ensureRunning(this) }
        manager.start()
    }
}
