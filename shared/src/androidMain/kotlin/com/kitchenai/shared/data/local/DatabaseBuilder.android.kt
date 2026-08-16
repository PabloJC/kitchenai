package com.kitchenai.shared.data.local

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import org.koin.core.scope.Scope

internal actual fun Scope.recipeDatabaseBuilder(): RoomDatabase.Builder<KitchenAiDatabase> {
    val context = get<Context>().applicationContext
    return Room.databaseBuilder<KitchenAiDatabase>(
        context = context,
        name = context.getDatabasePath(DATABASE_FILE_NAME).absolutePath,
    )
}
