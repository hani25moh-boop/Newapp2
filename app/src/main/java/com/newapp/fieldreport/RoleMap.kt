package com.newapp.fieldreport

/** Pure helpers for role codes (no Android dependencies, unit-tested). */
object RoleMap {
    val validCode = Regex("^[A-Z]{3}$")

    /** Old Arabic role names / categories -> 3-letter codes. */
    val legacy: Map<String, String> = linkedMapOf(
        "مدير المشروع" to "PMG", "مدير الموقع" to "SMG", "المخازن" to "STR",
        "مهندس الكهرباء" to "ELC", "الكهرباء" to "ELC", "مهندس الميكانيك" to "MEC", "الميكانيك" to "MEC",
        "الجودة" to "QAL", "الجودة والسلامة" to "QAL", "السلامة" to "SAF",
        "الحسابات والمالية" to "FIN", "المشتريات" to "PUR"
    )

    /** Accepts a code, an Arabic/English role name or an old category and returns the best role code. */
    fun codeFor(text: String, roles: List<Role>): String {
        val t = text.trim()
        if (validCode.matches(t)) return t
        roles.firstOrNull { it.nameAr == t || it.nameEn.equals(t, ignoreCase = true) }?.let { return it.code }
        return legacy[t] ?: t
    }

    /** Next free item code for a role: ELC-001, ELC-002 ... */
    fun nextItemCode(role: String, items: List<Item>): String {
        val re = Regex("^" + role + "-(\\d+)$")
        val n = items.mapNotNull { re.matchEntire(it.code)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
        return role + "-" + (n + 1).toString().padStart(3, '0')
    }
}
