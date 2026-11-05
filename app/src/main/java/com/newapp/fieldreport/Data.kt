package com.newapp.fieldreport

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class Project(@PrimaryKey val code: String, val name: String, val nameAr: String)

/** A job title / responsibility. code = 3 capital English letters (ELC, SAF, PLN ...). */
@Entity(tableName = "roles")
data class Role(@PrimaryKey val code: String, val nameAr: String, val nameEn: String)

/** category = role code. kind = Quantity | Activity | Event | Issue. */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey val code: String,
    val name: String,
    val nameAr: String,
    val unit: String,
    val category: String,
    val kind: String = "Quantity"
)

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String,
    val role: String,            // role code
    val recordType: String,      // item kind
    val projectCode: String,
    val itemCode: String,
    val itemName: String,
    val unit: String,
    val quantity: Double?,       // the recorded value (measure, % progress, count ...)
    val hours: Double?,
    val status: String,
    val priority: String,
    val personCode: String,
    val personName: String,
    val eventAt: String,
    val notes: String,
    // database version 2
    val personTitle: String = "",
    val reportDate: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photoRef: String = "",
    // database version 3: link to another person's item (join keys for Power BI)
    val linkType: String = "",       // AffectedBy | Affects | RelatedTo
    val linkPerson: String = "",     // matches Person_Name of the other person
    val linkItemCode: String = "",   // matches Item_ID of the other item
    val linkItemName: String = ""
)

@Dao
interface AppDao {
    @Query("SELECT * FROM projects ORDER BY nameAr") fun projects(): Flow<List<Project>>
    @Query("SELECT * FROM roles ORDER BY code") fun roles(): Flow<List<Role>>
    @Query("SELECT * FROM items ORDER BY code") fun items(): Flow<List<Item>>
    @Query("SELECT * FROM entries ORDER BY eventAt DESC") fun entries(): Flow<List<Entry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun projects(v: List<Project>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun roles(v: List<Role>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun role(v: Role)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun items(v: List<Item>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun item(v: Item)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun entry(v: Entry)

    @Delete suspend fun deleteEntry(e: Entry)
    @Query("DELETE FROM roles WHERE code = :c") suspend fun deleteRole(c: String)
    @Query("DELETE FROM items WHERE category = :c") suspend fun deleteItemsOf(c: String)
    @Query("SELECT COUNT(*) FROM entries WHERE role = :c") suspend fun entryCount(c: String): Int
    @Query("SELECT COUNT(*) FROM projects") suspend fun projectCount(): Int

    @Query("DELETE FROM projects WHERE code = :c AND code NOT IN (SELECT projectCode FROM entries)")
    suspend fun deleteProjectIfUnused(c: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE entries ADD COLUMN personTitle TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE entries ADD COLUMN reportDate TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE entries ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE entries ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE entries ADD COLUMN photoRef TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `roles` (`code` TEXT NOT NULL, `nameAr` TEXT NOT NULL, `nameEn` TEXT NOT NULL, PRIMARY KEY(`code`))")
        db.execSQL("ALTER TABLE items ADD COLUMN kind TEXT NOT NULL DEFAULT 'Quantity'")
        db.execSQL("ALTER TABLE entries ADD COLUMN linkType TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE entries ADD COLUMN linkPerson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE entries ADD COLUMN linkItemCode TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE entries ADD COLUMN linkItemName TEXT NOT NULL DEFAULT ''")
        // Old versions stored the Arabic role name; convert to the 3-letter role code.
        for ((name, code) in RoleMap.legacy) {
            db.execSQL("UPDATE entries SET role = ? WHERE role = ?", arrayOf<Any>(code, name))
            db.execSQL("UPDATE items SET category = ? WHERE category = ?", arrayOf<Any>(code, name))
        }
        // Remove only the old 2-letter starter items (EL-001 ...); your own items are kept. Starter roles can be re-added from the app.
        for (prefix in listOf("PM", "SM", "ST", "EL", "ME", "QA", "HS", "FN", "PR")) {
            db.execSQL("DELETE FROM items WHERE code GLOB '$prefix-[0-9][0-9][0-9]'")
        }
    }
}

@Database(entities = [Project::class, Role::class, Item::class, Entry::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(c: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(c.applicationContext, AppDatabase::class.java, "fieldreport_v3.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
