package com.montecarlo.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(setting: SettingsEntity)

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM settings")
    fun getAll(): Flow<List<SettingsEntity>>

    @Query("SELECT * FROM settings")
    suspend fun getAllSettingsList(): List<SettingsEntity>

    @Query("DELETE FROM settings")
    suspend fun deleteAllSettings()
}
