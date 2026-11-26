package com.materials.stockalert.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {
    @Query("SELECT * FROM merchants ORDER BY name ASC")
    fun observeMerchants(): Flow<List<Merchant>>

    @Query("SELECT * FROM merchants WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Merchant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(merchant: Merchant): Long

    @Delete
    suspend fun delete(merchant: Merchant)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE merchantId = :merchantId ORDER BY name ASC")
    fun observeItemsByMerchant(merchantId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items ORDER BY name ASC")
    fun observeAllItems(): Flow<List<Item>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Delete
    suspend fun delete(item: Item)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM scheduled_orders ORDER BY triggerAtMillis ASC")
    fun observeOrders(): Flow<List<ScheduledOrder>>

    @Query("SELECT * FROM scheduled_orders WHERE id = :id LIMIT 1")
    suspend fun getOrderById(id: Long): ScheduledOrder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: ScheduledOrder): Long

    @Update
    suspend fun update(order: ScheduledOrder)

    @Delete
    suspend fun delete(order: ScheduledOrder)
}
