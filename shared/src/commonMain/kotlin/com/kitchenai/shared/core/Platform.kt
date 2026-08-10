package com.kitchenai.shared.core

/** Identidad de la plataforma en tiempo de ejecución. Sólo para diagnóstico. */
interface Platform {
    val name: String
}

expect fun currentPlatform(): Platform
