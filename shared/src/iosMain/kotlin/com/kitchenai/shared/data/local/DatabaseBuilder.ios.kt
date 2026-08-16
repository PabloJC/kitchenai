package com.kitchenai.shared.data.local

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.scope.Scope
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun Scope.recipeDatabaseBuilder(): RoomDatabase.Builder<KitchenAiDatabase> {
    val documentsDirectory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
    return Room.databaseBuilder<KitchenAiDatabase>(
        name = requireNotNull(documentsDirectory?.path) { "no documents directory" } + "/$DATABASE_FILE_NAME",
    )
}
