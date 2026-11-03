package com.newapp.fieldreport

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "activities") data class ActivityItem(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val unit: String)
@Entity(tableName = "reports") data class Report(@PrimaryKey(autoGenerate = true) val id: Long = 0, val date: String, val activityName: String, val unit: String, val quantity: Double, val notes: String, val createdAt: Long)
@Entity(tableName = "projects") data class ProjectRef(@PrimaryKey val code: String, val name: String, val nameAr: String)
@Entity(tableName = "people") data class PersonRef(@PrimaryKey val code: String, val name: String, val nameAr: String, val jobTitle: String, val departmentId: String)
@Entity(tableName = "tasks") data class TaskRef(@PrimaryKey val code: String, val name: String, val nameAr: String, val wbs: String, val ownerId: String)
@Entity(tableName = "work_items") data class WorkItemRef(@PrimaryKey val code: String, val parentId: String, val type: String, val mfCode: String, val title: String, val titleAr: String, val discipline: String, val unit: String, val taskId: String, val plannedQty: Double?)
@Entity(tableName = "materials") data class MaterialRef(@PrimaryKey val code: String, val name: String, val nameAr: String, val mfCode: String, val ufCode: String, val unit: String)
@Entity(tableName = "daily_qty_log") data class DailyQtyLog(@PrimaryKey(autoGenerate = true) val id: Long = 0, val reportId: String, val projectId: String, val reportDate: String, val itemId: String, val qty: Double, val hours: Double?, val reportedBy: String, val notes: String, val createdAt: Long)
@Entity(tableName = "material_log") data class MaterialLog(@PrimaryKey(autoGenerate = true) val id: Long = 0, val recordId: String, val projectId: String, val recordDate: String, val movementType: String, val materialId: String, val qty: Double, val ufCode: String, val itemId: String, val deliveryNote: String, val supplier: String, val recordedBy: String, val notes: String, val createdAt: Long)
@Entity(tableName = "progress_updates") data class ProgressUpdate(@PrimaryKey(autoGenerate = true) val id: Long = 0, val updateId: String, val taskId: String, val date: String, val pct: Double, val hours: Double?, val cost: Double?, val note: String, val updatedBy: String, val createdAt: Long)
@Entity(tableName = "issue_reports") data class IssueReport(@PrimaryKey(autoGenerate = true) val id: Long = 0, val issueId: String, val projectId: String, val description: String, val raisedDate: String, val raisedBy: String, val ownerId: String, val priority: String, val status: String, val dueDate: String, val resolution: String, val createdAt: Long)

@Dao interface AppDao {
    @Query("SELECT * FROM activities ORDER BY name") fun activities(): Flow<List<ActivityItem>>
    @Query("SELECT COUNT(*) FROM activities") suspend fun activityCount(): Int
    @Insert suspend fun insertActivity(item: ActivityItem): Long
    @Insert suspend fun insertActivities(items: List<ActivityItem>)
    @Delete suspend fun deleteActivity(item: ActivityItem)
    @Query("SELECT * FROM reports WHERE date = :date ORDER BY createdAt DESC") fun reportsByDate(date: String): Flow<List<Report>>
    @Query("SELECT * FROM reports WHERE date = :date ORDER BY createdAt ASC") suspend fun reportsForExport(date: String): List<Report>
    @Insert suspend fun insertReport(report: Report): Long
    @Delete suspend fun deleteReport(report: Report)
    @Query("SELECT * FROM projects ORDER BY nameAr") fun projects(): Flow<List<ProjectRef>>
    @Query("SELECT * FROM people ORDER BY nameAr") fun people(): Flow<List<PersonRef>>
    @Query("SELECT * FROM tasks ORDER BY code") fun tasks(): Flow<List<TaskRef>>
    @Query("SELECT * FROM work_items ORDER BY code") fun workItems(): Flow<List<WorkItemRef>>
    @Query("SELECT * FROM materials ORDER BY nameAr") fun materials(): Flow<List<MaterialRef>>
    @Query("SELECT COUNT(*) FROM projects") suspend fun projectCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProjects(items: List<ProjectRef>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPeople(items: List<PersonRef>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTasks(items: List<TaskRef>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWorkItems(items: List<WorkItemRef>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMaterials(items: List<MaterialRef>)
    @Insert suspend fun insertDaily(item: DailyQtyLog)
    @Insert suspend fun insertMaterial(item: MaterialLog)
    @Insert suspend fun insertProgress(item: ProgressUpdate)
    @Insert suspend fun insertIssue(item: IssueReport)
    @Query("SELECT * FROM daily_qty_log ORDER BY createdAt ASC") suspend fun allDaily(): List<DailyQtyLog>
    @Query("SELECT * FROM material_log ORDER BY createdAt ASC") suspend fun allMaterials(): List<MaterialLog>
    @Query("SELECT * FROM progress_updates ORDER BY createdAt ASC") suspend fun allProgress(): List<ProgressUpdate>
    @Query("SELECT * FROM issue_reports ORDER BY createdAt ASC") suspend fun allIssues(): List<IssueReport>
}

@Database(entities = [ActivityItem::class, Report::class, ProjectRef::class, PersonRef::class, TaskRef::class, WorkItemRef::class, MaterialRef::class, DailyQtyLog::class, MaterialLog::class, ProgressUpdate::class, IssueReport::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object { @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fieldreport.db").fallbackToDestructiveMigration().build().also { instance = it } }
    }
}
val DEFAULT_ACTIVITIES = listOf("حفر" to "م³", "صب خرسانة مسلحة" to "م³", "حديد تسليح" to "طن", "بناء بلوك" to "م²", "أعمال كهرباء" to "م.ط").map { ActivityItem(name = it.first, unit = it.second) }
