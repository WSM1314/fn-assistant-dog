package com.example.fn_app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun find(accountId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountEntity)

    @Query("DELETE FROM accounts WHERE accountId = :accountId")
    suspend fun delete(accountId: String)

    @Query(
        "UPDATE accounts SET accountStatus = :status, lastError = :lastError, updatedAt = :now " +
            "WHERE accountId = :accountId",
    )
    suspend fun updateStatus(
        accountId: String,
        status: String,
        lastError: String?,
        now: Long,
    )
}
