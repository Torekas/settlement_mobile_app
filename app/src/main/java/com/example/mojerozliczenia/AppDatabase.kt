package com.example.mojerozliczenia

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Importy
import com.example.mojerozliczenia.packing.PackingItem
import com.example.mojerozliczenia.packing.PackingDao
// NOWE IMPORTY PLANERA
import com.example.mojerozliczenia.planner.PlannerEvent
import com.example.mojerozliczenia.planner.PlannerDao

@Database(
    entities = [
        User::class,
        Trip::class,
        TripMember::class,
        ExchangeRate::class,
        Transaction::class,
        TransactionSplit::class,
        PlannerEvent::class,
        PackingItem::class,
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun packingDao(): PackingDao
    abstract fun plannerDao(): PlannerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}