package com.kitchenai.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * App Check provider for release builds.
 *
 * Play Integrity only answers for APKs signed with the key registered in Play Console. A
 * release built locally with the debug key fails attestation, which is the point.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory =
    PlayIntegrityAppCheckProviderFactory.getInstance()
