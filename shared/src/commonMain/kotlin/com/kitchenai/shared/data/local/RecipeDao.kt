package com.kitchenai.shared.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes")
    suspend fun getAll(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recipes: List<RecipeEntity>)

    @Query("DELETE FROM recipes WHERE savedAtMillis < :cutoffMillis")
    suspend fun deleteStoredBefore(cutoffMillis: Long)

    /**
     * Adds a generation, every row stamped with the same [RecipeEntity.savedAtMillis], and drops
     * all but the newest [keep]. One transaction, so a reader never sees the old ones half gone.
     */
    @Transaction
    suspend fun addGeneration(
        recipes: List<RecipeEntity>,
        keep: Int,
    ) {
        // Always after the newest kept one: a clock moved backwards would otherwise file this
        // generation as the oldest and delete it below, and one in the same millisecond would merge.
        val newest = getAll().maxOfOrNull { it.savedAtMillis }
        val stamped =
            if (newest == null || recipes.all { it.savedAtMillis > newest }) {
                recipes
            } else {
                recipes.map { it.copy(savedAtMillis = newest + 1) }
            }
        insertAll(stamped)
        val stamps = getAll().map { it.savedAtMillis }.distinct().sortedDescending()
        if (stamps.size > keep) deleteStoredBefore(stamps[keep - 1])
    }
}
