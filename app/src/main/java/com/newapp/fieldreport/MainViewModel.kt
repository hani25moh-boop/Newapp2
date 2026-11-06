package com.newapp.fieldreport

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Values edited once from the Settings screen. Roles are optional and can be several. */
data class Settings(
    val userName: String = "",
    val personCode: String = "",
    val company: String = "",
    val orgCode: String = "",
    val projectCode: String = "",
    val projectName: String = "",
    val projectNameAr: String = "",
    val myRoles: Set<String> = emptySet()
)

val KINDS = setOf("Quantity", "Activity", "Event", "Issue")

class MainViewModel(a: Application) : AndroidViewModel(a) {
    private val dao = AppDatabase.get(a).dao()
    private val prefs = a.getSharedPreferences("fieldreport", Context.MODE_PRIVATE)
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    val projects = dao.projects().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val roles = dao.roles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val items = dao.items().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val entries = dao.entries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings
    private val _lastExport = MutableStateFlow(prefs.getString("lastExportAt", "").orEmpty())
    val lastExport: StateFlow<String> = _lastExport
    fun clearMessage() { _message.value = "" }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (dao.projectCount() == 0 && _settings.value.projectCode.isBlank()) dao.projects(Seed.projects)
            } catch (e: Exception) {
                _message.value = "تعذر تجهيز البيانات الأولية: ${e.message}"
            }
        }
    }

    private fun loadSettings() = Settings(
        userName = prefs.getString("userName", "").orEmpty(),
        personCode = prefs.getString("personCode", "").orEmpty(),
        company = prefs.getString("company", "").orEmpty(),
        orgCode = prefs.getString("orgCode", "").orEmpty(),
        projectCode = prefs.getString("projectCode", "").orEmpty(),
        projectName = prefs.getString("projectName", "").orEmpty(),
        projectNameAr = prefs.getString("projectNameAr", "").orEmpty(),
        myRoles = prefs.getStringSet("myRoles", emptySet())?.toSet() ?: emptySet()
    )

    fun saveSettings(s0: Settings) {
        val s = s0.copy(myRoles = _settings.value.myRoles)
        val old = _settings.value.projectCode.ifBlank { Seed.projects.first().code }
        prefs.edit()
            .putString("userName", s.userName).putString("personCode", s.personCode)
            .putString("company", s.company).putString("orgCode", s.orgCode)
            .putString("projectCode", s.projectCode).putString("projectName", s.projectName)
            .putString("projectNameAr", s.projectNameAr)
            .apply()
        _settings.value = s
        if (s.projectCode.isNotBlank()) viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.projects(listOf(Project(s.projectCode, s.projectName.ifBlank { s.projectCode }, s.projectNameAr.ifBlank { s.projectName.ifBlank { s.projectCode } })))
                if (old != s.projectCode) dao.deleteProjectIfUnused(old)
            } catch (e: Exception) {
                _message.value = "تعذر حفظ المشروع: ${e.message}"
            }
        }
        _message.value = "تم حفظ الإعدادات"
    }

    // ---------- roles ----------
    private fun persistMyRoles(set: Set<String>) {
        prefs.edit().putStringSet("myRoles", set).apply()
        _settings.value = _settings.value.copy(myRoles = set)
    }

    fun toggleMine(code: String) {
        val cur = _settings.value.myRoles
        persistMyRoles(if (code in cur) cur - code else cur + code)
    }

    /** Adds optional starter roles (with their starter items) and marks them as the user's roles. */
    fun addTemplates(codes: Set<String>) {
        if (codes.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.roles(Seed.roles.filter { it.code in codes })
                dao.items(Seed.items.filter { it.category in codes })
                persistMyRoles(_settings.value.myRoles + codes)
            } catch (e: Exception) {
                _message.value = "تعذرت إضافة الصفات: ${e.message}"
            }
        }
    }

    fun addRole(role: Role) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.role(role)
                persistMyRoles(_settings.value.myRoles + role.code)
                _message.value = "تمت إضافة الصفة ${role.nameAr}"
            } catch (e: Exception) {
                _message.value = "تعذرت إضافة الصفة: ${e.message}"
            }
        }
    }

    fun deleteRole(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (dao.entryCount(code) > 0) {
                    _message.value = "لا يمكن حذف صفة لها سجلات محفوظة"
                } else {
                    dao.deleteItemsOf(code)
                    dao.deleteRole(code)
                    persistMyRoles(_settings.value.myRoles - code)
                    _message.value = "تم حذف الصفة"
                }
            } catch (e: Exception) {
                _message.value = "تعذر حذف الصفة: ${e.message}"
            }
        }
    }

    // ---------- import ----------
    fun importFile(r: ContentResolver, u: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val b = XlsxImport.read(r, u)
                fun rows(name: String) = b.sheets[name].orEmpty().drop(1).filter { it.isNotEmpty() && it[0].isNotBlank() }

                val known = roles.value.toMutableList()
                val fresh = linkedMapOf<String, Role>()
                rows("Roles").forEach { row ->
                    val code = row[0].trim().uppercase()
                    fresh[code] = Role(code, row.getOrNull(1).orEmpty().ifBlank { code }, row.getOrNull(2).orEmpty())
                }
                fun all() = known + fresh.values
                fun need(code: String) {
                    if (all().any { it.code == code }) return
                    fresh[code] = if (code == "GEN") Role("GEN", "عام", "General") else Role(code, code, code)
                }
                fun resolveRole(text: String, fallbackGen: Boolean): String {
                    var c = RoleMap.codeFor(text, all())
                    if (!RoleMap.validCode.matches(c)) { if (fallbackGen) c = "GEN" else return c }
                    need(c)
                    return c
                }

                val p = rows("Projects").map { Project(it[0], it.getOrNull(1).orEmpty(), it.getOrNull(2).orEmpty()) }
                val i = rows("Items").map {
                    val kind = it.getOrNull(5).orEmpty().trim().let { k -> if (k in KINDS) k else "Quantity" }
                    Item(it[0], it.getOrNull(1).orEmpty(), it.getOrNull(2).orEmpty(), it.getOrNull(3).orEmpty(),
                        resolveRole(it.getOrNull(4).orEmpty(), true), kind)
                }
                val e = rows("Entries").map { rowToEntry(it) { t -> resolveRole(t, false) } }

                if (p.isEmpty() && i.isEmpty() && e.isEmpty() && fresh.isEmpty()) {
                    _message.value = "الملف لا يحتوي بيانات مقروءة. تأكد أن أسماء الأوراق Projects وRoles وItems وEntries."
                    return@launch
                }
                if (fresh.isNotEmpty()) dao.roles(fresh.values.toList())
                if (p.isNotEmpty()) dao.projects(p)
                if (i.isNotEmpty()) dao.items(i)
                e.forEach { dao.entry(it) }
                _message.value = "تم الاستيراد: ${p.size} مشاريع، ${fresh.size} صفات، ${i.size} بنود، ${e.size} سجلات"
            } catch (ex: Exception) {
                _message.value = "تعذر استيراد الملف: ${ex.message}"
            }
        }
    }

    /** Excel turns a typed date into a serial number; convert it back to yyyy-MM-dd HH:mm. */
    private fun excelDate(s: String): String {
        val d = s.toDoubleOrNull() ?: return s
        if (d < 20000) return s
        val days = d.toLong()
        val secs = Math.round((d - days) * 86400)
        return LocalDate.of(1899, 12, 30).plusDays(days).atStartOfDay().plusSeconds(secs).format(fmt)
    }

    private fun excelDay(s: String): String {
        val d = s.toDoubleOrNull() ?: return s.take(10)
        if (d < 20000) return s
        return LocalDate.of(1899, 12, 30).plusDays(d.toLong()).toString()
    }
    private fun rowToEntry(r: List<String>, roleOf: (String) -> String): Entry {
        fun c(n: Int) = r.getOrNull(n).orEmpty()
        val rawRole = c(1)
        return Entry(
            id = c(0), role = if (rawRole.isBlank()) "" else roleOf(rawRole), recordType = c(2),
            projectCode = c(3), itemCode = c(4), itemName = c(5), unit = c(6),
            quantity = r.getOrNull(7)?.toDoubleOrNull(), hours = r.getOrNull(8)?.toDoubleOrNull(),
            status = c(9), priority = c(10), personCode = c(11), personName = c(12),
            eventAt = excelDate(c(13)), notes = c(14), personTitle = c(15), reportDate = excelDay(c(16)),
            latitude = r.getOrNull(17)?.toDoubleOrNull(), longitude = r.getOrNull(18)?.toDoubleOrNull(),
            photoRef = c(19), linkType = c(20), linkPerson = c(21), linkItemCode = c(22), linkItemName = c(23)
        )
    }

    // ---------- records ----------
    fun save(
        role: String, item: Item, project: String, value: Double?, hours: Double?, status: String,
        priority: String, note: String, reportDate: String, linkType: String, linkPerson: String, linkItem: Item?
    ) {
        val now = LocalDateTime.now().format(fmt)
        val s = _settings.value
        val names = roles.value.filter { it.code in s.myRoles }.joinToString(" / ") { it.nameAr }
        val title = names.ifBlank { roles.value.firstOrNull { it.code == role }?.nameAr.orEmpty() }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.entry(
                    Entry(
                        id = UUID.randomUUID().toString(), role = role, recordType = item.kind, projectCode = project,
                        itemCode = item.code, itemName = item.nameAr.ifBlank { item.name }, unit = item.unit,
                        quantity = value, hours = hours, status = status, priority = priority,
                        personCode = s.personCode, personName = s.userName, eventAt = now, notes = note,
                        personTitle = title, reportDate = reportDate,
                        linkType = linkType, linkPerson = linkPerson,
                        linkItemCode = linkItem?.code.orEmpty(),
                        linkItemName = linkItem?.let { it.nameAr.ifBlank { it.name } }.orEmpty()
                    )
                )
                _message.value = "تم الحفظ — $now"
            } catch (e: Exception) {
                _message.value = "تعذر الحفظ: ${e.message}"
            }
        }
    }

    fun addItem(item: Item) {
        viewModelScope.launch(Dispatchers.IO) {
            try { dao.item(item); _message.value = "تمت إضافة البند ${item.code}" }
            catch (e: Exception) { _message.value = "تعذرت إضافة البند: ${e.message}" }
        }
    }

    fun delete(e: Entry) {
        viewModelScope.launch(Dispatchers.IO) {
            try { dao.deleteEntry(e); _message.value = "تم حذف السجل" }
            catch (x: Exception) { _message.value = "تعذر الحذف: ${x.message}" }
        }
    }

    suspend fun export(onlyDate: String? = null): File? = withContext(Dispatchers.IO) {
        val all = entries.value
        val list = if (onlyDate == null) all
                   else all.filter { it.reportDate.ifBlank { it.eventAt.take(10) } == onlyDate }
        if (list.isEmpty()) {
            _message.value = "لا توجد سجلات لتصديرها"
            return@withContext null
        }
        val dir = File(getApplication<Application>().cacheDir, "exports")
        dir.mkdirs()
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val now = LocalDateTime.now()
        val stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val code = _settings.value.projectCode.replace(Regex("[^A-Za-z0-9_-]"), "").ifBlank { "PRJ" }
        val tag = onlyDate?.replace("-", "") ?: "ALL"
        val f = File(dir, "FieldReport_${code}_${tag}_$stamp.xlsx")
        XlsxWriter.write(f, projects.value, roles.value, items.value, list)
        val nowText = now.format(fmt)
        prefs.edit().putString("lastExportAt", nowText).apply()
        _lastExport.value = nowText
        f
    }
}
