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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
class MainViewModel(a:Application):AndroidViewModel(a){
 private val dao=AppDatabase.get(a).dao(); val projects=dao.projects().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList());val items=dao.items().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList());val entries=dao.entries().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList());private val _message=MutableStateFlow("");val message:StateFlow<String> = _message
 private val fmt=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
 fun importFile(r:ContentResolver,u:Uri){viewModelScope.launch(Dispatchers.IO){try{val b=XlsxImport.read(r,u);val p=b.sheets["Projects"].orEmpty().drop(1).filter{it.size>2&&it[0].isNotBlank()}.map{Project(it[0],it.getOrNull(1).orEmpty(),it.getOrNull(2).orEmpty())};val i=b.sheets["Items"].orEmpty().drop(1).filter{it.size>3&&it[0].isNotBlank()}.map{Item(it[0],it.getOrNull(1).orEmpty(),it.getOrNull(2).orEmpty(),it.getOrNull(3).orEmpty(),it.getOrNull(4).orEmpty())};if(p.isNotEmpty())dao.projects(p);if(i.isNotEmpty())dao.items(i);val rows=b.sheets["Entries"].orEmpty().drop(1).filter{it.size>0&&it[0].isNotBlank()};rows.forEach{dao.entry(rowToEntry(it))};_message.value="تم الاستيراد: ${p.size} مشاريع، ${i.size} بنود، ${rows.size} سجلات"}catch(e:Exception){_message.value="تعذر استيراد الملف: ${e.message}"}}}
 private fun rowToEntry(r:List<String>)=Entry(r.getOrNull(0).orEmpty(),r.getOrNull(1).orEmpty(),r.getOrNull(2).orEmpty(),r.getOrNull(3).orEmpty(),r.getOrNull(4).orEmpty(),r.getOrNull(5).orEmpty(),r.getOrNull(6).orEmpty(),r.getOrNull(7)?.toDoubleOrNull(),r.getOrNull(8)?.toDoubleOrNull(),r.getOrNull(9).orEmpty(),r.getOrNull(10).orEmpty(),r.getOrNull(11).orEmpty(),r.getOrNull(12).orEmpty(),r.getOrNull(13).orEmpty(),r.getOrNull(14).orEmpty())
 fun save(role:String,type:String,project:String,item:Item?,qty:Double?,hours:Double?,status:String,priority:String,code:String,name:String,note:String){val now=LocalDateTime.now().format(fmt);viewModelScope.launch{dao.entry(Entry(UUID.randomUUID().toString(),role,type,project,item?.code.orEmpty(),item?.nameAr?:item?.name.orEmpty(),item?.unit.orEmpty(),qty,hours,status,priority,code,name,now,note));_message.value="تم الحفظ — $now"}}
 suspend fun export():File=withContext(Dispatchers.IO){val dir=File(getApplication<Application>().cacheDir,"exports");dir.mkdirs();val f=File(dir,"FieldReport_Exchange_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))}.xlsx");val h=listOf("Record_ID","Role","Record_Type","Project_ID","Item_ID","Item_Name","Unit","Quantity","Hours","Status","Priority","Person_Code","Person_Name","Recorded_At","Notes");XlsxWriter.write(f,h,entries.value.map{listOf<Any>(it.id,it.role,it.recordType,it.projectCode,it.itemCode,it.itemName,it.unit,it.quantity?:"",it.hours?:"",it.status,it.priority,it.personCode,it.personName,it.eventAt,it.notes)});f}
 fun delete(e:Entry)=viewModelScope.launch{dao.entry(e)}
}
