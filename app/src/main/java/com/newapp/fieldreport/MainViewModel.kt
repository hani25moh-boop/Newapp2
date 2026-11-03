package com.newapp.fieldreport

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao=AppDatabase.get(app).dao()
    val projects=dao.projects().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val people=dao.people().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tasks=dao.tasks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val workItems=dao.workItems().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val materials=dao.materials().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val activities=dao.activities().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _message=MutableStateFlow(""); val message:StateFlow<String> = _message
    private val _date=MutableStateFlow(LocalDate.now()); val date:StateFlow<LocalDate> = _date
    val reports=_date.flatMapLatest{dao.reportsByDate(it.toString())}.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    init { viewModelScope.launch { if(dao.activityCount()==0) dao.insertActivities(DEFAULT_ACTIVITIES) } }
    fun importWorkbook(resolver:ContentResolver, uri:Uri){ viewModelScope.launch(Dispatchers.IO){ try { val w=XlsxImport.read(resolver,uri); importData(w); _message.value="تم الاستيراد: ${w.sheets.size} أوراق" } catch(e:Exception){ _message.value="تعذر قراءة الملف: ${e.message}" } } }
    private suspend fun importData(w:XlsxImport.WorkbookData){
        fun rows(n:String)=w.sheets[n].orEmpty().drop(4).filter{it.any(String::isNotBlank)}
        rows("Ref_Projects").mapNotNull{if(it.size<5||it[1].isBlank())null else ProjectRef(it[1],it.getOrNull(2).orEmpty(),it.getOrNull(3).orEmpty())}.let{if(it.isNotEmpty())dao.upsertProjects(it)}
        rows("Ref_People").mapNotNull{if(it.size<6||it[1].isBlank())null else PersonRef(it[1],it.getOrNull(2).orEmpty(),it.getOrNull(3).orEmpty(),it.getOrNull(4).orEmpty(),it.getOrNull(5).orEmpty())}.let{if(it.isNotEmpty())dao.upsertPeople(it)}
        rows("Ref_Tasks").mapNotNull{if(it.size<6||it[1].isBlank())null else TaskRef(it[1],it.getOrNull(2).orEmpty(),it.getOrNull(3).orEmpty(),it.getOrNull(4).orEmpty(),it.getOrNull(5).orEmpty())}.let{if(it.isNotEmpty())dao.upsertTasks(it)}
        rows("Ref_WorkItems").mapNotNull{if(it.size<15||it[1].isBlank())null else WorkItemRef(it[1],it.getOrNull(2).orEmpty(),it.getOrNull(3).orEmpty(),it.getOrNull(4).orEmpty(),it.getOrNull(8).orEmpty(),it.getOrNull(9).orEmpty(),it.getOrNull(10).orEmpty(),it.getOrNull(11).orEmpty(),it.getOrNull(12).orEmpty(),it.getOrNull(14)?.toDoubleOrNull())}.let{if(it.isNotEmpty())dao.upsertWorkItems(it)}
        rows("Ref_Materials").mapNotNull{if(it.size<9||it[1].isBlank())null else MaterialRef(it[1],it.getOrNull(2).orEmpty(),it.getOrNull(3).orEmpty(),it.getOrNull(4).orEmpty(),it.getOrNull(6).orEmpty(),it.getOrNull(8).orEmpty())}.let{if(it.isNotEmpty())dao.upsertMaterials(it)}
    }
    fun saveDaily(project:String,item:WorkItemRef,qty:Double,hours:Double?,by:String,note:String){viewModelScope.launch{dao.insertDaily(DailyQtyLog(reportId="DQ-${System.currentTimeMillis()}",projectId=project,reportDate=_date.value.toString(),itemId=item.code,qty=qty,hours=hours,reportedBy=by,notes=note,createdAt=System.currentTimeMillis()));_message.value="تم حفظ تحديث الكمية"}}
    fun saveMaterial(project:String,m:MaterialRef,movement:String,qty:Double,uf:String,item:String,by:String,note:String){viewModelScope.launch{dao.insertMaterial(MaterialLog(recordId="MV-${System.currentTimeMillis()}",projectId=project,recordDate=_date.value.toString(),movementType=movement,materialId=m.code,qty=qty,ufCode=uf,itemId=item,deliveryNote="",supplier="",recordedBy=by,notes=note,createdAt=System.currentTimeMillis()));_message.value="تم حفظ حركة المادة"}}
    fun saveReport(activity:ActivityItem,qty:Double,note:String){viewModelScope.launch{dao.insertReport(Report(date=_date.value.toString(),activityName=activity.name,unit=activity.unit,quantity=qty,notes=note,createdAt=System.currentTimeMillis()))}}
    fun shiftDate(days:Long){_date.value=_date.value.plusDays(days)}
    suspend fun buildExport():File?=withContext(Dispatchers.IO){ val dir=File(getApplication<Application>().cacheDir,"exports");dir.mkdirs();val f=File(dir,"PM_EXCH_${_date.value}.xlsx");val daily=dao.allDaily();val mats=dao.allMaterials();XlsxWriter.write(f,listOf("نوع السجل","المعرف","المشروع","التاريخ","البند/المادة","النوع","الكمية","الوحدة/العنصر","المستخدم","الملاحظات"),daily.map{listOf<Any>("Daily_Qty_Log",it.reportId,it.projectId,it.reportDate,it.itemId,"",it.qty,"",it.reportedBy,it.notes)}+mats.map{listOf<Any>("Material_Log",it.recordId,it.projectId,it.recordDate,it.materialId,it.movementType,it.qty,it.ufCode,it.recordedBy,it.notes)});f}
}
