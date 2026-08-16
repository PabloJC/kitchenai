package com.kitchenai.shared.data.local

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(entities = [RecipeEntity::class], version = 1)
@ConstructedBy(KitchenAiDatabaseConstructor::class)
abstract class KitchenAiDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
}

// Room's KSP processor generates the actual per target; there is no body to write by hand, which
// is also why the usual ktlint/detekt "no actual for expect" rule cannot apply here.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object KitchenAiDatabaseConstructor : RoomDatabaseConstructor<KitchenAiDatabase>
