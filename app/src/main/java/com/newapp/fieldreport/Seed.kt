package com.newapp.fieldreport

/**
 * Generic starter data. Nothing here is forced on the user:
 * starter roles are optional templates, and every company can add its own roles and items.
 */
object Seed {
    val projects = listOf(Project("PRJ-001", "My Project", "مشروعي"))

    val roles = listOf(
        Role("PMG", "مدير المشروع", "Project Manager"),
        Role("SMG", "مدير الموقع", "Site Manager"),
        Role("STR", "المخازن", "Store"),
        Role("ELC", "مهندس الكهرباء", "Electrical Engineer"),
        Role("MEC", "مهندس الميكانيك", "Mechanical Engineer"),
        Role("QAL", "الجودة", "Quality"),
        Role("SAF", "السلامة", "Safety"),
        Role("FIN", "الحسابات والمالية", "Accounts & Finance"),
        Role("PUR", "المشتريات", "Procurement")
    )

    private fun i(code: String, en: String, ar: String, unit: String, cat: String, kind: String = "Quantity") =
        Item(code, en, ar, unit, cat, kind)

    val items = listOf(
        // PMG - مدير المشروع
        i("PMG-001", "Overall physical progress", "نسبة الإنجاز الكلية للمشروع", "%", "PMG", "Activity"),
        i("PMG-002", "Progress variance (planned vs actual)", "فرق الإنجاز بين المخطط والفعلي", "%", "PMG"),
        i("PMG-003", "Client meeting", "اجتماع مع العميل", "No.", "PMG", "Event"),
        i("PMG-004", "Variation orders raised", "أوامر التغيير المقدمة", "No.", "PMG"),
        i("PMG-005", "Subcontractor payments approved", "مستخلصات مقاولي الباطن المعتمدة", "No.", "PMG"),
        i("PMG-006", "Risk escalated", "مخاطر تم رفعها", "No.", "PMG", "Issue"),
        // SMG - مدير الموقع
        i("SMG-001", "Excavation", "أعمال الحفر", "m3", "SMG"),
        i("SMG-002", "Plain concrete", "الخرسانة العادية", "m3", "SMG"),
        i("SMG-003", "Reinforced concrete", "الخرسانة المسلحة", "m3", "SMG"),
        i("SMG-004", "Rebar fixing", "تركيب حديد التسليح", "ton", "SMG"),
        i("SMG-005", "Formwork", "أعمال الشدات الخشبية", "m2", "SMG"),
        i("SMG-006", "Blockwork", "أعمال البلوك", "m2", "SMG"),
        i("SMG-007", "Plastering", "أعمال اللياسة", "m2", "SMG"),
        i("SMG-008", "Tiling", "أعمال البلاط", "m2", "SMG"),
        i("SMG-009", "Painting", "أعمال الدهان", "m2", "SMG"),
        i("SMG-010", "Waterproofing", "أعمال العزل المائي", "m2", "SMG"),
        // STR - المخازن
        i("STR-001", "Cement received", "أسمنت وارد", "bag", "STR"),
        i("STR-002", "Rebar received", "حديد تسليح وارد", "ton", "STR"),
        i("STR-003", "Sand received", "رمل وارد", "m3", "STR"),
        i("STR-004", "Aggregate received", "حصى وارد", "m3", "STR"),
        i("STR-005", "Blocks received", "بلوك وارد", "No.", "STR"),
        i("STR-006", "Material issued to site", "مواد مصروفة للموقع", "No.", "STR"),
        i("STR-007", "Damaged or rejected material", "مواد تالفة أو مرفوضة", "No.", "STR", "Issue"),
        i("STR-008", "Fuel received", "وقود وارد", "l", "STR"),
        // ELC - مهندس الكهرباء
        i("ELC-001", "Conduit installation", "تمديد مواسير الكهرباء", "m", "ELC"),
        i("ELC-002", "Cable pulling", "سحب الكابلات", "m", "ELC"),
        i("ELC-003", "Cable tray installation", "تركيب سلالم الكابلات", "m", "ELC"),
        i("ELC-004", "Junction and switch boxes", "علب التوصيل والمفاتيح", "No.", "ELC"),
        i("ELC-005", "Lighting fixtures installed", "تركيب وحدات الإنارة", "No.", "ELC"),
        i("ELC-006", "Distribution boards installed", "تركيب لوحات التوزيع", "No.", "ELC"),
        i("ELC-007", "Earthing works", "أعمال التأريض", "m", "ELC"),
        i("ELC-008", "Insulation and continuity test", "اختبار العزل والاستمرارية", "No.", "ELC", "Event"),
        // MEC - مهندس الميكانيك
        i("MEC-001", "Water supply pipes", "مواسير تغذية المياه", "m", "MEC"),
        i("MEC-002", "Drainage pipes", "مواسير الصرف", "m", "MEC"),
        i("MEC-003", "Sanitary fixtures installed", "تركيب الأدوات الصحية", "No.", "MEC"),
        i("MEC-004", "Pumps installed", "تركيب المضخات", "No.", "MEC"),
        i("MEC-005", "HVAC ducting", "مجاري التكييف", "m", "MEC"),
        i("MEC-006", "AC units installed", "تركيب وحدات التكييف", "No.", "MEC"),
        i("MEC-007", "Fire fighting pipes", "مواسير مكافحة الحريق", "m", "MEC"),
        i("MEC-008", "Pressure test", "اختبار الضغط", "No.", "MEC", "Event"),
        // QAL - الجودة
        i("QAL-001", "Non-conformance report (NCR)", "تقرير عدم مطابقة", "No.", "QAL", "Issue"),
        i("QAL-002", "Inspection request approved", "طلب فحص معتمد", "No.", "QAL", "Event"),
        i("QAL-003", "Inspection request rejected", "طلب فحص مرفوض", "No.", "QAL", "Event"),
        i("QAL-004", "Concrete cube samples", "عينات مكعبات الخرسانة", "No.", "QAL"),
        i("QAL-005", "Material approval request", "طلب اعتماد مواد", "No.", "QAL", "Event"),
        i("QAL-006", "Snag and defect items", "بنود ملاحظات وعيوب", "No.", "QAL", "Issue"),
        // SAF - السلامة
        i("SAF-001", "Toolbox talk", "اجتماع سلامة قبل العمل", "No.", "SAF", "Event"),
        i("SAF-002", "Near miss", "حادث وشيك", "No.", "SAF", "Issue"),
        i("SAF-003", "Injury incident", "حادث إصابة", "No.", "SAF", "Issue"),
        i("SAF-004", "PPE violation", "مخالفة معدات الحماية الشخصية", "No.", "SAF", "Issue"),
        i("SAF-005", "Safety inspection", "جولة تفتيش سلامة", "No.", "SAF", "Event"),
        i("SAF-006", "Permit-to-work issued", "تصريح عمل صادر", "No.", "SAF", "Event"),
        i("SAF-007", "Housekeeping deficiency", "ملاحظة نظافة وترتيب", "No.", "SAF", "Issue"),
        // FIN - الحسابات والمالية
        i("FIN-001", "Invoices processed", "فواتير تمت معالجتها", "No.", "FIN"),
        i("FIN-002", "Payments released", "دفعات تم صرفها", "No.", "FIN"),
        i("FIN-003", "Petty cash expenses", "مصروفات نثرية", "No.", "FIN"),
        i("FIN-004", "Subcontractor claims reviewed", "مستخلصات تمت مراجعتها", "No.", "FIN"),
        i("FIN-005", "Payroll batches processed", "كشوف رواتب تمت معالجتها", "No.", "FIN"),
        // PUR - المشتريات
        i("PUR-001", "Purchase orders issued", "أوامر شراء صادرة", "No.", "PUR"),
        i("PUR-002", "Quotations received", "عروض أسعار واردة", "No.", "PUR"),
        i("PUR-003", "Deliveries confirmed", "توريدات مؤكدة", "No.", "PUR"),
        i("PUR-004", "Late delivery", "توريد متأخر", "No.", "PUR", "Issue"),
        i("PUR-005", "Supplier evaluations", "تقييمات موردين", "No.", "PUR"),
        i("PUR-006", "Pending material requests", "طلبات مواد معلقة", "No.", "PUR")
    )
}
