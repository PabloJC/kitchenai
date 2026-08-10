package com.kitchenai.app

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.kitchenai.shared.di.initKoin
import org.koin.android.ext.koin.androidContext

class KitchenAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase se inicializa solo vía el ContentProvider de google-services,
        // que corre antes que este onCreate: FirebaseApp ya existe aquí.
        //
        // App Check va antes que initKoin porque Koin es quien construye lo que
        // acaba hablando con Auth y Firestore. Instalar la factoría después
        // dejaría salir sin atestiguar las primeras peticiones, que son
        // justamente las de autenticación.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())

        initKoin { androidContext(this@KitchenAiApplication) }
    }
}
