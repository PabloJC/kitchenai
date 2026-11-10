package com.kitchenai.shared.data.local

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.scope.Scope

internal const val DATABASE_FILE_NAME = "kitchenai.db"

/**
 * Android needs a [android.content.Context] to place the file; iOS needs the documents
 * directory. Resolving the platform half from the [Scope] the database is built in, rather than
 * from a parameter, is what lets this declaration stay identical on both sides — the leak stays
 * in `androidMain`/`iosMain`.
 */
internal expect fun Scope.recipeDatabaseBuilder(): RoomDatabase.Builder<KitchenAiDatabase>

internal fun RoomDatabase.Builder<KitchenAiDatabase>.open(queryDispatcher: CoroutineDispatcher): KitchenAiDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryDispatcher)
        .build()
