package com.kitchenai.app

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.kitchenai.ui.di.initKoinUi
import org.koin.android.ext.koin.androidContext

class KitchenAiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase is already initialised here: the google-services ContentProvider runs
        // before onCreate. App Check goes before Koin, which builds whatever talks to Auth
        // and Firestore — installing it later lets the first requests out unattested.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())

        initKoinUi(
            functionsRegion = BuildConfig.FUNCTIONS_REGION,
            googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            privacyPolicyUrl = BuildConfig.PRIVACY_POLICY_URL,
            firebaseEmulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST,
            firestoreEmulatorPort = BuildConfig.FIRESTORE_EMULATOR_PORT,
        ) {
            androidContext(this@KitchenAiApplication)
        }
    }
}
