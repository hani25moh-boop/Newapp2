package com.newapp.fieldreport

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="projects") data class Project(@PrimaryKey val code:String,val name:String,val nameAr:String)
@Entity(tableName="items") data class Item(@PrimaryKey val code:String,val name:String,val nameAr:String,val unit:String,val category:String)
@Entity(tableName="entries") data class Entry(@PrimaryKey val id:String,val role:String,val recordType:String,val projectCode:String,val itemCode:String,val itemName:String,val unit:String,val quantity:Double?,val hours:Double?,val status:String,val priority:String,val personCode:String,val personName:String,val eventAt:String,val notes:String)
@Dao interface AppDao {
 @Query("SELECT * FROM projects ORDER BY nameAr") fun projects():Flow<List<Project>>
 @Query("SELECT * FROM items ORDER BY nameAr") fun items():Flow<List<Item>>
 @Query("SELECT * FROM entries ORDER BY eventAt DESC") fun entries():Flow<List<Entry>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun projects(v:List<Project>)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun items(v:List<Item>)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun entry(v:Entry)
 @Query("DELETE FROM entries") suspend fun clearEntries()
}
@Database(entities=[Project::class,Item::class,Entry::class],version=1,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){abstract fun dao():AppDao;companion object{@Volatile private var i:AppDatabase?=null;fun get(c:Context)=i?: synchronized(this){i?:Room.databaseBuilder(c.applicationContext,AppDatabase::class.java,"fieldreport_v3.db").build().also{i=it}}}}
