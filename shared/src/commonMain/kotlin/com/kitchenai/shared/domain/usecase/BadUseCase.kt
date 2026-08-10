package com.kitchenai.shared.domain.usecase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fichero de prueba del revisor automático. NO es código real.
 *
 * Viola, a propósito:
 *  1. domain importando dev.gitlive (regla de dependencias)
 *  2. Dispatchers hardcodeado en vez de DispatcherProvider
 *  3. clave de API hardcodeada
 *  4. excepción tragada en un catch vacío
 *  5. caso de uso sin ningún test
 *
 * Está formateado correctamente a propósito: ktlint y detekt deben pasar, para que
 * lo único que pueda hacer fallar la PR sea el criterio del revisor.
 */
class BadUseCase {
    private val apiKey = "AIzaSyD-fake-hardcoded-key-1234567890"

    suspend operator fun invoke(id: String): String =
        withContext(Dispatchers.Default) {
            try {
                Firebase.firestore.collection("recipes").document(id).get().toString()
            } catch (e: Exception) {
                ""
            }
        }
}
