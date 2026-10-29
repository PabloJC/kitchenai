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
        insertAll(recipes)
        val stamps = getAll().map { it.savedAtMillis }.distinct().sortedDescending()
        if (stamps.size > keep) deleteStoredBefore(stamps[keep - 1])
    }
}
