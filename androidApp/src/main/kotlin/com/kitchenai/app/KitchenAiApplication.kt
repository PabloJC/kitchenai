package com.kitchenai.app

import android.app.Application
import com.kitchenai.shared.di.initKoin
import org.koin.android.ext.koin.androidContext

class KitchenAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase se inicializa solo vía el ContentProvider de google-services.
        initKoin { androidContext(this@KitchenAiApplication) }
    }
}
