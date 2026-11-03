@file:OptIn(ExperimentalMaterial3Api::class)
package com.newapp.fieldreport

import android.content.*
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.File

private val roles=listOf("مدير المشروع","مدير الموقع","المخازن","مهندس الكهرباء","مهندس الميكانيك","الجودة","السلامة","الحسابات والمالية","المشتريات")
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){MaterialTheme{AppRoot()}}}}}
@Composable fun AppRoot(vm:MainViewModel=viewModel()){
 var role by remember{mutableStateOf<String?>(null)}; var showImport by remember{mutableStateOf(false)}; val ctx=LocalContext.current; val scope=rememberCoroutineScope(); val msg by vm.message.collectAsState()
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let{vm.importWorkbook(ctx.contentResolver,it)}}
 LaunchedEffect(msg){if(msg.isNotBlank())Toast.makeText(ctx,msg,Toast.LENGTH_LONG).show()}
 Scaffold(topBar={TopAppBar(title={Text(role?.let{"Field Report — $it"}?:"Field Report")},navigationIcon={if(role!=null)TextButton(onClick={role=null}){Text("رجوع")}})},bottomBar={if(role!=null)NavigationBar{NavigationBarItem(true,{role=null},{},{Text("الأدوار")});NavigationBarItem(false,{showImport=true},{},{Text("استيراد")});NavigationBarItem(false,{scope.launch{vm.buildExport()?.let{shareFile(ctx,it)} }},{},{Text("تصدير")})}}){pad->Box(Modifier.padding(pad).fillMaxSize()){if(role==null)RolePicker{role=it}else RoleHome(role!!,vm)}}
 if(showImport){showImport=false;LaunchedEffect(Unit){picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-excel"))}}
}
@Composable fun RolePicker(onSelect:(String)->Unit){LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("اختر واجهة العمل",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("كل مستخدم يرى النماذج التي تخص عمله فقط.")};items(roles){r->Card(Modifier.fillMaxWidth(),onClick={onSelect(r)}){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(r,fontWeight=FontWeight.Bold);Text(roleHint(r),style=MaterialTheme.typography.bodySmall)}Text("فتح")}}}}}
fun roleHint(r:String)=when(r){"المخازن"->"استلام وصرف وإرجاع وتالف المواد";"مدير الموقع"->"الكميات المنفذة وساعات العمل";"الجودة"->"بلاغات الجودة والملاحظات";"السلامة"->"بلاغات السلامة والإجراءات";"المشتريات"->"المواد والتوريد";else->"بيانات ومتابعة المشروع"}
@Composable fun RoleHome(role:String,vm:MainViewModel){when(role){"المخازن"->MaterialForm(vm);"مدير الموقع","مهندس الكهرباء","مهندس الميكانيك"->QuantityForm(vm,role);else->GenericHome(role,vm)}}
@Composable fun GenericHome(role:String,vm:MainViewModel){val p by vm.projects.collectAsState();val w by vm.workItems.collectAsState();Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("لوحة $role",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("المشاريع المستوردة: ${p.size}");Text("بنود الأعمال المتاحة: ${w.size}");Text("استخدم زر استيراد لتحميل ملف إدارة المشاريع ثم ابدأ بإدخال السجلات.")}}
@Composable fun QuantityForm(vm:MainViewModel,role:String){val items by vm.workItems.collectAsState();val projects by vm.projects.collectAsState();var selected by remember{mutableStateOf<WorkItemRef?>(null)};var qty by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var menu by remember{mutableStateOf(false)};val ctx=LocalContext.current;Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("تحديث الكميات — $role",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("المشاريع: ${projects.firstOrNull()?.nameAr?:"استورد القالب أولاً"}");Box{OutlinedButton({menu=true},Modifier.fillMaxWidth()){Text(selected?.let{"${it.titleAr} (${it.unit})"}?:"اختر بند العمل")};DropdownMenu(menu,{menu=false}){items.take(100).forEach{DropdownMenuItem({Text("${it.titleAr} — ${it.code}")},{selected=it;menu=false})}}};OutlinedTextField(qty,{qty=it},label={Text("الكمية المنفذة")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth());OutlinedTextField(note,{note=it},label={Text("الملاحظات")},minLines=3,modifier=Modifier.fillMaxWidth());Button({val n=qty.toDoubleOrNull();if(selected==null||n==null||n<0)Toast.makeText(ctx,"اختر البند وأدخل كمية صحيحة",Toast.LENGTH_SHORT).show()else{vm.saveDaily(projects.firstOrNull()?.code.orEmpty(),selected!!,n,null,"",note);qty="";note=""}},Modifier.fillMaxWidth()){Text("حفظ التحديث")}}}
@Composable fun MaterialForm(vm:MainViewModel){val mats by vm.materials.collectAsState();val projects by vm.projects.collectAsState();var selected by remember{mutableStateOf<MaterialRef?>(null)};var type by remember{mutableStateOf("Received")};var qty by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var menu by remember{mutableStateOf(false)};val ctx=LocalContext.current;Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("حركة المواد",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Box{OutlinedButton({menu=true},Modifier.fillMaxWidth()){Text(selected?.let{"${it.nameAr} (${it.unit})"}?:"اختر المادة")};DropdownMenu(menu,{menu=false}){mats.take(100).forEach{DropdownMenuItem({Text("${it.nameAr} — ${it.code}")},{selected=it;menu=false})}}};Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Received" to "استلام","Consumed" to "صرف","Returned" to "إرجاع","Damaged" to "تالف").forEach{(v,t)->FilterChip(type==v,{type=v},label={Text(t)})}};OutlinedTextField(qty,{qty=it},label={Text("الكمية")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth());OutlinedTextField(note,{note=it},label={Text("الملاحظات")},minLines=3,modifier=Modifier.fillMaxWidth());Button({val n=qty.toDoubleOrNull();if(selected==null||n==null||n<0)Toast.makeText(ctx,"اختر المادة وأدخل كمية صحيحة",Toast.LENGTH_SHORT).show()else{vm.saveMaterial(projects.firstOrNull()?.code.orEmpty(),selected!!,type,n,selected!!.ufCode,"","",note);qty="";note=""}},Modifier.fillMaxWidth()){Text("حفظ حركة المادة")}}}
private fun shareFile(c:Context,f:File){val u=FileProvider.getUriForFile(c,"${c.packageName}.fileprovider",f);c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"مشاركة ملف Excel"))}
