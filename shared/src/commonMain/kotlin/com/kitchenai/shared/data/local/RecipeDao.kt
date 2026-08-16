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

    @Query("DELETE FROM recipes")
    suspend fun clear()

    /**
     * One transaction, so a reader between the delete and the insert never sees an empty table:
     * a generation being replaced is not the same thing as a generation being briefly absent.
     */
    @Transaction
    suspend fun replaceAll(recipes: List<RecipeEntity>) {
        clear()
        insertAll(recipes)
    }
}
