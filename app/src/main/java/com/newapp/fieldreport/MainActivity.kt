@file:OptIn(ExperimentalMaterial3Api::class)
package com.newapp.fieldreport
import android.content.*
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.File

private val roles=listOf("مدير المشروع","مدير الموقع","المخازن","مهندس الكهرباء","مهندس الميكانيك","الجودة","السلامة","الحسابات والمالية","المشتريات")
private fun typeFor(r:String)=when(r){"المخازن","المشتريات"->"Material";"الجودة","السلامة"->"Issue";"مدير المشروع","الحسابات والمالية"->"Progress";else->"Quantity"}
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){MaterialTheme{App()}}}}}
@Composable fun App(vm:MainViewModel=viewModel()){
 var role by remember{mutableStateOf<String?>(null)};val ctx=LocalContext.current;val scope=rememberCoroutineScope();val msg by vm.message.collectAsState();val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->u?.let{vm.importFile(ctx.contentResolver,it)}}
 LaunchedEffect(msg){if(msg.isNotBlank())Toast.makeText(ctx,msg,Toast.LENGTH_LONG).show()}
 Scaffold(topBar={TopAppBar(title={Text(role?:"Field Report",fontWeight=FontWeight.Bold)},navigationIcon={if(role!=null)TextButton({role=null}){Text("الأدوار")}})},bottomBar={if(role!=null)NavigationBar{NavigationBarItem(selected=true,onClick={picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))},icon={},label={Text("استيراد")});NavigationBarItem(selected=false,onClick={scope.launch{share(ctx,vm.export())}},icon={},label={Text("تصدير")});NavigationBarItem(selected=false,onClick={role=null},icon={},label={Text("تبديل الدور")})}}){p->if(role==null)RolePicker{role=it}else EntryScreen(role!!,vm,Modifier.padding(p))}}
@Composable fun RolePicker(on:(String)->Unit){LazyColumn(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Image(painterResource(com.newapp.fieldreport.R.drawable.fieldreport_logo),"شعار Field Report",Modifier.size(86.dp),contentScale=ContentScale.Fit);Text("Field Report",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("واجهة إدخال ميداني بسيطة بدل جداول Excel المعقدة")};items(roles){r->Card(onClick={on(r)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(18.dp)){Text(r,fontWeight=FontWeight.Bold);Text("فتح واجهة ${typeFor(r)}",style=MaterialTheme.typography.bodySmall)}}}}}
@Composable fun EntryScreen(role:String,vm:MainViewModel,mod:Modifier){val projects by vm.projects.collectAsState();val items by vm.items.collectAsState();val entries by vm.entries.collectAsState();var project by remember{mutableStateOf("")};var selectedItem by remember{mutableStateOf<Item?>(null)};var qty by remember{mutableStateOf("")};var hours by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var personCode by remember{mutableStateOf("")};var personName by remember{mutableStateOf("")};var status by remember{mutableStateOf(if(typeFor(role)=="Issue")"Open" else "")};var priority by remember{mutableStateOf(if(typeFor(role)=="Issue")"Medium" else "")};var menu by remember{mutableStateOf(false)};val ctx=LocalContext.current
 LazyColumn(mod.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("إدخال بيانات — $role",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("السجل يظهر أسفل النموذج فور حفظه للمراجعة قبل التصدير.")};item{OutlinedTextField(project,{project=it},label={Text("كود المشروع")},placeholder={Text(projects.firstOrNull()?.code?:"مثال PRJ-001")},modifier=Modifier.fillMaxWidth())};item{Box{OutlinedButton({menu=true},Modifier.fillMaxWidth()){Text(selectedItem?.let{"${it.nameAr} (${it.unit})"}?:"اختر البند أو المادة")};DropdownMenu(menu,{menu=false}){items.forEach{x->DropdownMenuItem({Text("${x.nameAr} — ${x.code}")},{selectedItem=x;menu=false})}}}};item{OutlinedTextField(qty,{qty=it},label={Text("الكمية")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())};item{OutlinedTextField(hours,{hours=it},label={Text("الساعات — اختياري")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())};item{OutlinedTextField(personCode,{personCode=it},label={Text("كود المدخل")},modifier=Modifier.fillMaxWidth())};item{OutlinedTextField(personName,{personName=it},label={Text("اسم المدخل")},modifier=Modifier.fillMaxWidth())};if(typeFor(role)=="Issue"){item{OutlinedTextField(priority,{priority=it},label={Text("الأولوية")},modifier=Modifier.fillMaxWidth())};item{OutlinedTextField(status,{status=it},label={Text("الحالة")},modifier=Modifier.fillMaxWidth())}};item{OutlinedTextField(note,{note=it},label={Text("الملاحظات")},minLines=3,modifier=Modifier.fillMaxWidth())};item{Button({val n=qty.toDoubleOrNull();if(selectedItem==null&&typeFor(role)!="Issue")Toast.makeText(ctx,"اختر البند أولاً",Toast.LENGTH_SHORT).show()else{vm.save(role,typeFor(role),project,selectedItem,n,hours.toDoubleOrNull(),status,priority,personCode,personName,note);qty="";hours="";note=""}},Modifier.fillMaxWidth()){Text("حفظ ومراجعة السجل")}};item{HorizontalDivider();Text("السجلات المحفوظة (${entries.count{it.role==role}})",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)};items(entries.filter{it.role==role}.take(30),key={it.id}){e->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${e.itemName.ifBlank{"بلاغ/تحديث"}} — ${e.quantity?:""} ${e.unit}",fontWeight=FontWeight.Bold);Text("${e.personName.ifBlank{"بدون اسم"} } (${e.personCode.ifBlank{"بدون كود"}}) • ${e.eventAt}");if(e.notes.isNotBlank())Text(e.notes,style=MaterialTheme.typography.bodySmall)}}}}
}
private suspend fun share(c:Context,f:File){val u=FileProvider.getUriForFile(c,"${c.packageName}.fileprovider",f);c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"مشاركة ملف Excel الموحد"))}
