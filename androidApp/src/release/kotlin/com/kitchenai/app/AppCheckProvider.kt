package com.kitchenai.app

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Proveedor de App Check para builds de release.
 *
 * Play Integrity atestigua que la petición sale de un binario legítimo instalado
 * desde Play. Sólo responde para APKs firmados con la clave registrada en Play
 * Console y vinculados al proyecto de Firebase: un release compilado en local y
 * firmado con la clave de debug fallará la atestación, y eso es lo correcto.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory =
    PlayIntegrityAppCheckProviderFactory.getInstance()
