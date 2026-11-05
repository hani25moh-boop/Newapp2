@file:OptIn(ExperimentalMaterial3Api::class)

package com.newapp.fieldreport

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------- fixed vocabularies (the only things that are not user data) ----------
private val kindOptions = listOf("Quantity" to "قياس", "Activity" to "نشاط", "Event" to "حدث", "Issue" to "مشكلة")
private val kindUnit = mapOf("Quantity" to "m", "Activity" to "%", "Event" to "No.", "Issue" to "No.")
private val priorities = listOf("Low" to "منخفضة", "Medium" to "متوسطة", "High" to "عالية")
private val issueStatuses = listOf("Open" to "مفتوح", "In Progress" to "قيد المعالجة", "Closed" to "مغلق")
private val activityStatuses = listOf("Not Started" to "لم يبدأ", "In Progress" to "جارٍ", "Completed" to "منتهٍ")
private val relations = listOf("" to "بدون ارتباط", "AffectedBy" to "يتأثر بـ", "Affects" to "يؤثر على", "RelatedTo" to "مرتبط بـ")

private fun label(list: List<Pair<String, String>>, key: String) = list.firstOrNull { it.first == key }?.second ?: key
private fun fmtNum(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { App() }
            }
        }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    // screen: home | entry | roles | settings   (back button walks back to "home", then exits)
    var screen by rememberSaveable { mutableStateOf("home") }
    var roleCode by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val msg by vm.message.collectAsState()
    val settings by vm.settings.collectAsState()
    val allRoles by vm.roles.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { vm.importFile(ctx.contentResolver, it) }
    }
    LaunchedEffect(msg) { if (msg.isNotBlank()) Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }

    val needSetup = settings.userName.isBlank() || settings.projectCode.isBlank()
    BackHandler(enabled = !needSetup && screen != "home") { screen = "home" }

    val title = when {
        needSetup -> "الإعداد الأولي"
        screen == "entry" -> allRoles.firstOrNull { it.code == roleCode }?.nameAr ?: "إدخال"
        screen == "roles" -> "إدارة الصفات"
        screen == "settings" -> "الإعدادات"
        else -> "Field Report"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { if (!needSetup && screen != "home") TextButton({ screen = "home" }) { Text("رجوع") } }
            )
        },
        bottomBar = {
            if (!needSetup) NavigationBar {
                NavigationBarItem(selected = false, onClick = { picker.launch(arrayOf("*/*")) }, icon = {}, label = { Text("استيراد") })
                NavigationBarItem(selected = false, onClick = {
                    scope.launch {
                        try { share(ctx, vm.export()) }
                        catch (e: Exception) { Toast.makeText(ctx, "تعذر التصدير: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }, icon = {}, label = { Text("تصدير") })
                NavigationBarItem(selected = screen == "roles", onClick = { screen = "roles" }, icon = {}, label = { Text("الصفات") })
                NavigationBarItem(selected = screen == "settings", onClick = { screen = "settings" }, icon = {}, label = { Text("الإعدادات") })
            }
        }
    ) { p ->
        val mod = Modifier.padding(p)
        when {
            needSetup -> SettingsScreen(settings, true, mod) { s, templates -> vm.saveSettings(s); vm.addTemplates(templates) }
            screen == "settings" -> SettingsScreen(settings, false, mod) { s, _ -> vm.saveSettings(s); screen = "home" }
            screen == "roles" -> RolesScreen(vm, mod)
            screen == "entry" && roleCode.isNotBlank() -> EntryScreen(roleCode, vm, mod)
            else -> HomeScreen(settings, allRoles, mod, onOpen = { roleCode = it; screen = "entry" }, onManage = { screen = "roles" })
        }
    }
}

// ---------- home ----------
@Composable
fun HomeScreen(s: Settings, allRoles: List<Role>, mod: Modifier, onOpen: (String) -> Unit, onManage: () -> Unit) {
    val mine = allRoles.filter { it.code in s.myRoles }
    LazyColumn(mod.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Image(painterResource(com.newapp.fieldreport.R.drawable.fieldreport_logo), "شعار Field Report", Modifier.size(86.dp), contentScale = ContentScale.Fit)
            Text("Field Report", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(s.userName, fontWeight = FontWeight.Bold)
            Text("المشروع: ${s.projectCode}", style = MaterialTheme.typography.bodySmall)
        }
        if (mine.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("لا توجد صفة بعد", fontWeight = FontWeight.Bold)
                        Text("أضف صفتك الأولى (مثل مهندس كهرباء أو مسؤول سلامة)، أو أضف أي صفة تحتاجها برمز من 3 أحرف.")
                        Button(onManage) { Text("إضافة صفة") }
                    }
                }
            }
        } else {
            items(mine, key = { it.code }) { r ->
                Card(onClick = { onOpen(r.code) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(r.nameAr, fontWeight = FontWeight.Bold)
                        Text("${r.code}${if (r.nameEn.isNotBlank()) " • " + r.nameEn else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { TextButton(onManage) { Text("إدارة الصفات") } }
        }
    }
}

// ---------- settings / first run ----------
@Composable
fun SettingsScreen(s: Settings, firstRun: Boolean, mod: Modifier, onSave: (Settings, Set<String>) -> Unit) {
    var userName by rememberSaveable { mutableStateOf(s.userName) }
    var personCode by rememberSaveable { mutableStateOf(s.personCode) }
    var company by rememberSaveable { mutableStateOf(s.company) }
    var orgCode by rememberSaveable { mutableStateOf(s.orgCode) }
    var projectCode by rememberSaveable { mutableStateOf(s.projectCode.ifBlank { "PRJ-001" }) }
    var projectName by rememberSaveable { mutableStateOf(s.projectName) }
    var projectNameAr by rememberSaveable { mutableStateOf(s.projectNameAr) }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    val ctx = LocalContext.current
    LazyColumn(mod.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(if (firstRun) "أهلاً بك" else "الإعدادات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("تُدخل هذه البيانات مرة واحدة، وتظهر في كل سجل وفي التقارير.")
        }
        item { Text("بياناتك", fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(userName, { userName = it }, label = { Text("الاسم الكامل (إلزامي)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(personCode, { personCode = it.trim() }, label = { Text("رمزك الشخصي — اختياري") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { HorizontalDivider(); Text("الشركة والمشروع", fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(company, { company = it }, label = { Text("اسم الشركة") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(orgCode, { orgCode = it.uppercase().take(6) }, label = { Text("رمز الشركة (مثال: ABC)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(projectCode, { projectCode = it.uppercase().trim() }, label = { Text("رمز المشروع (إلزامي)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(projectName, { projectName = it }, label = { Text("اسم المشروع بالإنجليزية") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(projectNameAr, { projectNameAr = it }, label = { Text("اسم المشروع بالعربية") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (firstRun) {
            item {
                HorizontalDivider()
                Text("صفاتي — اختياري", fontWeight = FontWeight.Bold)
                Text("اختر صفة أو أكثر، أو تجاوز هذه الخطوة وأضف صفاتك لاحقاً من إدارة الصفات.", style = MaterialTheme.typography.bodySmall)
            }
            items(Seed.roles, key = { it.code }) { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(r.code in picked, { on -> picked = if (on) picked + r.code else picked - r.code })
                    Text("${r.nameAr}  (${r.code})")
                }
            }
        }
        item {
            Button({
                if (userName.isBlank()) Toast.makeText(ctx, "اكتب اسمك", Toast.LENGTH_SHORT).show()
                else if (projectCode.isBlank()) Toast.makeText(ctx, "اكتب رمز المشروع", Toast.LENGTH_SHORT).show()
                else onSave(
                    Settings(userName.trim(), personCode.trim(), company.trim(), orgCode.trim(), projectCode.trim(), projectName.trim(), projectNameAr.trim(), s.myRoles),
                    picked.toSet()
                )
            }, Modifier.fillMaxWidth()) { Text("حفظ") }
        }
    }
}

// ---------- roles manager ----------
@Composable
fun RolesScreen(vm: MainViewModel, mod: Modifier) {
    val allRoles by vm.roles.collectAsState()
    val settings by vm.settings.collectAsState()
    val ctx = LocalContext.current
    var nameAr by rememberSaveable { mutableStateOf("") }
    var nameEn by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Role?>(null) }
    val missing = Seed.roles.filter { t -> allRoles.none { it.code == t.code } }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف الصفة") },
            text = { Text("سيتم حذف الصفة «${r.nameAr}» وبنودها. لا يمكن حذف صفة لها سجلات.") },
            confirmButton = { TextButton({ vm.deleteRole(r.code); toDelete = null }) { Text("حذف") } },
            dismissButton = { TextButton({ toDelete = null }) { Text("إلغاء") } }
        )
    }

    LazyColumn(mod.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("صفاتي وكل الصفات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("فعّل المفتاح للصفات التي تعمل بها. يمكن للشخص الواحد أن يحمل أكثر من صفة.", style = MaterialTheme.typography.bodySmall)
        }
        if (allRoles.isEmpty()) item { Text("لا توجد صفات بعد.") }
        items(allRoles, key = { it.code }) { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Switch(r.code in settings.myRoles, { vm.toggleMine(r.code) })
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(r.nameAr, fontWeight = FontWeight.Bold)
                    Text("${r.code}${if (r.nameEn.isNotBlank()) " • " + r.nameEn else ""}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton({ toDelete = r }) { Text("حذف") }
            }
        }
        if (missing.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text("صفات جاهزة (اختيارية) مع بنودها", fontWeight = FontWeight.Bold)
            }
            items(missing, key = { "t" + it.code }) { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.nameAr} (${r.code})", Modifier.weight(1f))
                    TextButton({ vm.addTemplates(setOf(r.code)) }) { Text("+ إضافة") }
                }
            }
        }
        item {
            HorizontalDivider()
            Text("إضافة صفة جديدة", fontWeight = FontWeight.Bold)
            Text("الرمز 3 أحرف إنجليزية كبيرة، وتبدأ به أرقام بنودها (مثال: PLN → PLN-001).", style = MaterialTheme.typography.bodySmall)
        }
        item { OutlinedTextField(nameAr, { nameAr = it }, label = { Text("اسم الصفة بالعربية") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم بالإنجليزية — اختياري") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(code, { code = it.uppercase().filter { c -> c in 'A'..'Z' }.take(3) }, label = { Text("الرمز (3 أحرف)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item {
            Button({
                when {
                    nameAr.isBlank() -> Toast.makeText(ctx, "اكتب اسم الصفة", Toast.LENGTH_SHORT).show()
                    !RoleMap.validCode.matches(code) -> Toast.makeText(ctx, "الرمز يجب أن يكون 3 أحرف إنجليزية", Toast.LENGTH_SHORT).show()
                    allRoles.any { it.code == code } -> Toast.makeText(ctx, "هذا الرمز مستخدم لصفة أخرى", Toast.LENGTH_SHORT).show()
                    else -> { vm.addRole(Role(code, nameAr.trim(), nameEn.trim())); nameAr = ""; nameEn = ""; code = "" }
                }
            }, Modifier.fillMaxWidth()) { Text("إضافة الصفة") }
        }
    }
}

// ---------- small shared widgets ----------
@Composable
fun Choice(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text("$label: ${options.firstOrNull { it.first == value }?.second ?: "—"}") }
        DropdownMenu(open, { open = false }) {
            options.forEach { o -> DropdownMenuItem({ Text(o.second) }, { onPick(o.first); open = false }) }
        }
    }
}

// ---------- entry ----------
@Composable
fun EntryScreen(roleCode: String, vm: MainViewModel, mod: Modifier) {
    val projects by vm.projects.collectAsState()
    val allItems by vm.items.collectAsState()
    val entries by vm.entries.collectAsState()
    val settings by vm.settings.collectAsState()
    val ctx = LocalContext.current

    var projectPick by rememberSaveable { mutableStateOf("") }
    var itemCode by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("Medium") }
    var reportDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var linkRel by rememberSaveable { mutableStateOf("") }
    var linkPerson by rememberSaveable { mutableStateOf("") }
    var linkItemCode by rememberSaveable { mutableStateOf("") }
    var projectMenu by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf(false) }
    var personMenu by remember { mutableStateOf(false) }
    var linkItemMenu by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Entry?>(null) }

    val project = projectPick.ifBlank { settings.projectCode.ifBlank { projects.firstOrNull()?.code.orEmpty() } }
    val roleItems = allItems.filter { it.category == roleCode }
    val selected = roleItems.firstOrNull { it.code == itemCode }
    val kind = selected?.kind ?: "Quantity"
    val statusOptions = if (kind == "Activity") activityStatuses else issueStatuses
    val effStatus = status.ifBlank { if (kind == "Activity") "In Progress" else "Open" }
    val mine = entries.filter { it.role == roleCode }

    val persons = (entries.map { it.personName } + settings.userName).filter { it.isNotBlank() }.distinct().sorted()
    val linkItems = if (linkPerson.isBlank()) allItems else {
        val codes = entries.filter { it.personName == linkPerson }.map { it.itemCode }.toSet()
        allItems.filter { it.code in codes }.ifEmpty { allItems }
    }
    val linkItem = allItems.firstOrNull { it.code == linkItemCode }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(reportDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { reportDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    showDate = false
                }) { Text("موافق") }
            },
            dismissButton = { TextButton({ showDate = false }) { Text("إلغاء") } }
        ) { DatePicker(state = st) }
    }

    if (showAdd) AddItemDialog(roleCode, allItems, onDismiss = { showAdd = false }) { vm.addItem(it); itemCode = it.code; status = ""; showAdd = false }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف السجل") },
            text = { Text("هل تريد حذف سجل «${e.itemName}»؟ لا يمكن التراجع.") },
            confirmButton = { TextButton({ vm.delete(e); toDelete = null }) { Text("حذف") } },
            dismissButton = { TextButton({ toDelete = null }) { Text("إلغاء") } }
        )
    }

    LazyColumn(mod.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(settings.userName, style = MaterialTheme.typography.bodySmall) }
        item {
            Box {
                OutlinedButton({ projectMenu = true }, Modifier.fillMaxWidth()) {
                    val n = projects.firstOrNull { it.code == project }
                    Text("المشروع: $project${if (n != null) " — " + n.nameAr.ifBlank { n.name } else ""}")
                }
                DropdownMenu(projectMenu, { projectMenu = false }) {
                    projects.forEach { x -> DropdownMenuItem({ Text("${x.code} — ${x.nameAr.ifBlank { x.name }}") }, { projectPick = x.code; projectMenu = false }) }
                }
            }
        }
        item { OutlinedButton({ showDate = true }, Modifier.fillMaxWidth()) { Text("تاريخ التقرير: $reportDate") } }
        item {
            Box {
                OutlinedButton({ itemMenu = true }, Modifier.fillMaxWidth()) {
                    Text(selected?.let { "${it.code} — ${it.nameAr} (${label(kindOptions, it.kind)})" } ?: "اختر البند أو النشاط أو الحدث")
                }
                DropdownMenu(itemMenu, { itemMenu = false }) {
                    roleItems.forEach { x ->
                        DropdownMenuItem({ Text("${x.code} — ${x.nameAr}") }, { itemCode = x.code; status = ""; itemMenu = false })
                    }
                }
            }
        }
        if (roleItems.isEmpty()) item { Text("لا توجد بنود لهذه الصفة بعد. أضف أول بند.", style = MaterialTheme.typography.bodySmall) }
        item { TextButton({ showAdd = true }) { Text("+ إضافة بند جديد") } }

        if (selected != null) {
            val valueLabel = when (kind) {
                "Quantity" -> "القيمة (${selected.unit}) — إلزامي"
                "Activity" -> "نسبة الإنجاز % — اختياري"
                else -> "العدد — اختياري"
            }
            item {
                OutlinedTextField(value, { value = it }, label = { Text(valueLabel) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
            if (kind == "Activity" || kind == "Issue") item { Choice("الحالة", effStatus, statusOptions) { status = it } }
            if (kind == "Issue") item { Choice("الأولوية", priority, priorities) { priority = it } }
            item {
                OutlinedTextField(hours, { hours = it }, label = { Text("الساعات أو المدة — اختياري") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            OutlinedTextField(note, { note = it }, label = { Text(if (kind == "Event") "وصف الحدث" else "الملاحظات") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        }

        // ----- link to another person's item (join keys: person name + item code) -----
        item { HorizontalDivider(); Text("الارتباط ببند أو شخص آخر — اختياري", fontWeight = FontWeight.Bold) }
        item { Choice("العلاقة", linkRel, relations) { linkRel = it; if (it.isBlank()) { linkPerson = ""; linkItemCode = "" } } }
        if (linkRel.isNotBlank()) {
            item {
                OutlinedTextField(linkPerson, { linkPerson = it }, label = { Text("اسم الشخص المرتبط") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Box {
                    TextButton({ personMenu = true }) { Text("اختيار من الأسماء المعروفة") }
                    DropdownMenu(personMenu, { personMenu = false }) {
                        if (persons.isEmpty()) DropdownMenuItem({ Text("لا توجد أسماء بعد") }, { personMenu = false })
                        persons.forEach { n -> DropdownMenuItem({ Text(n) }, { linkPerson = n; linkItemCode = ""; personMenu = false }) }
                    }
                }
            }
            item {
                Box {
                    OutlinedButton({ linkItemMenu = true }, Modifier.fillMaxWidth()) {
                        Text(linkItem?.let { "البند المرتبط: ${it.code} — ${it.nameAr}" } ?: "رمز البند المرتبط (اختياري)")
                    }
                    DropdownMenu(linkItemMenu, { linkItemMenu = false }) {
                        linkItems.forEach { x -> DropdownMenuItem({ Text("${x.code} — ${x.nameAr}") }, { linkItemCode = x.code; linkItemMenu = false }) }
                    }
                }
            }
        }

        item {
            Button({
                val v = value.trim().replace(',', '.').toDoubleOrNull()
                val h = hours.trim().replace(',', '.').toDoubleOrNull()
                when {
                    project.isBlank() -> Toast.makeText(ctx, "اختر المشروع أولاً", Toast.LENGTH_SHORT).show()
                    selected == null -> Toast.makeText(ctx, "اختر البند أولاً", Toast.LENGTH_SHORT).show()
                    kind == "Quantity" && (v == null || v < 0) -> Toast.makeText(ctx, "أدخل قيمة صحيحة", Toast.LENGTH_SHORT).show()
                    kind == "Activity" && value.isNotBlank() && (v == null || v < 0 || v > 100) -> Toast.makeText(ctx, "النسبة من 0 إلى 100", Toast.LENGTH_SHORT).show()
                    value.isNotBlank() && v == null -> Toast.makeText(ctx, "القيمة غير صحيحة", Toast.LENGTH_SHORT).show()
                    hours.isNotBlank() && h == null -> Toast.makeText(ctx, "الساعات غير صحيحة", Toast.LENGTH_SHORT).show()
                    linkRel.isNotBlank() && linkPerson.isBlank() && linkItemCode.isBlank() -> Toast.makeText(ctx, "اختر الشخص أو البند المرتبط", Toast.LENGTH_SHORT).show()
                    else -> {
                        vm.save(
                            roleCode, selected, project, v, h,
                            if (kind == "Activity" || kind == "Issue") effStatus else "",
                            if (kind == "Issue") priority else "",
                            note.trim(), reportDate, linkRel, linkPerson.trim(), linkItem
                        )
                        value = ""; hours = ""; note = ""; linkRel = ""; linkPerson = ""; linkItemCode = ""
                    }
                }
            }, Modifier.fillMaxWidth()) { Text("حفظ") }
        }

        item {
            HorizontalDivider()
            Text("السجلات المحفوظة (${mine.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(mine.take(30), key = { it.id }) { e ->
            val back = entries.count {
                it.id != e.id && it.linkItemCode.isNotBlank() && it.linkItemCode == e.itemCode &&
                    (it.linkPerson.isBlank() || it.linkPerson == e.personName)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val v = e.quantity?.let { " — ${fmtNum(it)} ${e.unit}" }.orEmpty()
                    Text("${e.itemCode} ${e.itemName}$v", fontWeight = FontWeight.Bold)
                    val st = if (e.status.isNotBlank()) " • " + label(issueStatuses + activityStatuses, e.status) else ""
                    Text("${label(kindOptions, e.recordType)}$st • ${e.personName.ifBlank { "بدون اسم" }}", style = MaterialTheme.typography.bodySmall)
                    Text("تاريخ التقرير: ${e.reportDate.ifBlank { e.eventAt.take(10) }} • سُجّل: ${e.eventAt}", style = MaterialTheme.typography.bodySmall)
                    if (e.notes.isNotBlank()) Text(e.notes, style = MaterialTheme.typography.bodySmall)
                    if (e.linkType.isNotBlank()) {
                        val who = e.linkPerson.ifBlank { "—" }
                        val what = e.linkItemCode.ifBlank { "—" }
                        Text("${label(relations, e.linkType)}: $who | $what", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                    if (back > 0) Text("مرتبط به: $back سجل", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    TextButton({ toDelete = e }) { Text("حذف") }
                }
            }
        }
    }
}

@Composable
fun AddItemDialog(roleCode: String, existing: List<Item>, onDismiss: () -> Unit, onAdd: (Item) -> Unit) {
    var nameAr by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Quantity") }
    var unit by remember { mutableStateOf("m") }
    val ctx = LocalContext.current
    val code = RoleMap.nextItemCode(roleCode, existing)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بند جديد — $code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nameAr, { nameAr = it }, label = { Text("الاسم بالعربية") }, singleLine = true)
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم بالإنجليزية — اختياري") }, singleLine = true)
                Choice("النوع", kind, kindOptions) { kind = it; unit = kindUnit[it].orEmpty() }
                OutlinedTextField(unit, { unit = it }, label = { Text("الوحدة (m, m2, m3, ton, %, No.)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton({
                if (nameAr.isBlank()) Toast.makeText(ctx, "اكتب اسم البند", Toast.LENGTH_SHORT).show()
                else onAdd(Item(code, nameEn.trim().ifBlank { nameAr.trim() }, nameAr.trim(), unit.trim().ifBlank { "No." }, roleCode, kind))
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

private fun share(c: Context, f: File) {
    val u = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
    c.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, u)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "مشاركة ملف Excel الموحد"
        )
    )
}
