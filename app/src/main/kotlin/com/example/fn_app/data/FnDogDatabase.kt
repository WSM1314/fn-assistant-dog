package com.example.fn_app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AccountEntity::class], version = 1, exportSchema = true)
abstract class FnDogDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    companion object {
        fun build(context: Context): FnDogDatabase =
            Room.databaseBuilder(context.applicationContext, FnDogDatabase::class.java, DB_NAME).build()

        private const val DB_NAME = "fn_assistant_dog.db"
    }
}
