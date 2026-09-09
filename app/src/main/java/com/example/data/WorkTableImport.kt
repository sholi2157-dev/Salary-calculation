package com.example.data

import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.Calendar

/** Clipboard tables: columns are identified by headers, never by a guessed position. */
object WorkTableImport {
    private fun clean(s: String) = s.replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\ufeff]"), "").trim()
    private fun key(s: String) = clean(s).lowercase(Locale.ROOT).replace(Regex("[\\s\"׳״'_:/-]"), "")
    private val aliases = mapOf(
        "category" to listOf("קטגוריה", "מעסיק", "מעסיק/קטגוריה", "category", "employer"),
        "date" to listOf("תאריך", "date"),
        "hours" to listOf("שעות", "משך", "משך שעות", "hours", "duration"),
        "rate" to listOf("תעריף", "תעריף שעתי", "שכר לשעה", "hourlyRate", "rate"),
        "total" to listOf("שכר לתשלום", "סהכ רווח", "סהכ", "סך הכל", "סכום", "totalEarnings", "total"),
        "paid" to listOf("סטטוס", "סטטוס תשלום", "שולם", "isPaid", "paid", "status"),
        "notes" to listOf("הערות", "notes"),
        "currency" to listOf("מטבע", "currency"),
        "start" to listOf("שעת כניסה", "התחלה", "startTime", "start"),
        "end" to listOf("שעת יציאה", "סיום", "endTime", "end")
    ).mapValues { (_, v) -> v.map(::key) }

    fun decode(input: String): WorkBackup.Contents {
        val text = input.replace("\uFEFF", "").trim('\r', '\n')
        val candidates = listOf('\t', ',', ';', '|').mapNotNull { d ->
            try { table(text, d).takeIf { it.isNotEmpty() } } catch (_: IllegalArgumentException) { null }
        }
        val rows = candidates.maxByOrNull { row -> row.first().count { h -> aliases.values.any { key(h) in it } } }
            ?: error("לא נמצאה טבלה תקינה")
        val header = rows.first().map(::key)
        require(aliases.values.none { names -> header.count { it in names } > 1 }) { "כותרת מופיעה יותר מפעם אחת" }
        val columns = aliases.mapValues { (_, names) -> header.indexOfFirst { it in names } }
        require(listOf("category", "date", "hours", "rate").all { columns.getValue(it) >= 0 }) {
            "יש להעתיק גם כותרות: קטגוריה, תאריך, שעות ותעריף שעתי"
        }
        require(rows.size > 1) { "הטבלה מכילה כותרות בלבד" }
        val entries = rows.drop(1).mapIndexed { i, row ->
            try {
                require(row.size == header.size) { "מספר העמודות אינו תואם לכותרות" }
                fun cell(name: String) = row.getOrNull(columns.getValue(name))?.let(::clean).orEmpty()
                val category = cell("category"); require(category.isNotBlank()) { "חסרה קטגוריה" }
                val hoursText = cell("hours")
                val hours = if (hoursText.matches(Regex("\\d+:\\d{2}"))) {
                    val p = hoursText.split(':'); require(p[1].toInt() < 60) { "דקות לא תקינות" }; p[0].toDouble() + p[1].toDouble()/60
                } else number(hoursText)
                val rate = number(cell("rate"))
                val total = cell("total").takeIf { it.isNotBlank() }?.let(::number) ?: hours * rate
                require(total.isFinite()) { "סכום לא תקין" }
                val currencies = listOf(cell("currency"), cell("rate"), cell("total")).mapNotNull { value ->
                    when {
                        value.contains('$') || value.contains("USD", true) -> "$"
                        value.contains('₪') || value.contains("ILS", true) || value == "שקל" || value == "שקלים" -> "₪"
                        else -> null
                    }
                }.distinct()
                require(currencies.size <= 1) { "נמצאו מטבעות סותרים" }
                require(cell("currency").isBlank() || cell("currency").uppercase(Locale.ROOT) in listOf("₪", "$", "ILS", "USD", "שקל", "שקלים")) { "מטבע לא מזוהה" }
                val paid = when (cell("paid").lowercase(Locale.ROOT)) {
                    "שולם", "כן", "true", "paid", "yes", "1" -> true
                    "", "ממתין", "לא", "לא שולם", "false", "unpaid", "no", "0" -> false
                    else -> error("סטטוס תשלום לא מזוהה")
                }
                val start = cell("start").ifBlank { null }; val end = cell("end").ifBlank { null }
                require((start == null) == (end == null)) { "חסרה שעת התחלה או סיום" }
                for (t in listOfNotNull(start,end)) require(t.matches(Regex("(?:[01]?\\d|2[0-3]):[0-5]\\d"))) { "שעה לא תקינה" }
                val date = date(cell("date"))
                WorkEntry(category=category, date=date, createdAt=date, isTimeRange=start != null,
                    startTime=start, endTime=end, hours=hours, hourlyRate=rate, totalEarnings=total,
                    isPaid=paid, notes=cell("notes"), currency=currencies.firstOrNull() ?: "₪")
            } catch (e: Exception) { throw IllegalArgumentException("שורה ${i+2}: ${e.message}", e) }
        }
        val categories = entries.distinctBy { it.category }.map { WorkCategory(name=it.category,defaultRate=it.hourlyRate) }
        return WorkBackup.Contents(categories, entries, emptyList())
    }

    fun number(text: String): Double {
        var s = clean(text).replace(Regex("(?i)USD|ILS|₪|\\$|\\s|\\u00a0"), "")
        if (s.contains(',') && s.contains('.')) {
            require(s.matches(Regex("\\d{1,3}(,\\d{3})+\\.\\d+")) || s.matches(Regex("\\d{1,3}(\\.\\d{3})+,\\d+"))) { "מפרידי מספר לא תקינים: $text" }
            s = if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        } else if (s.contains(',')) {
            // Three trailing digits are ambiguous (decimal or thousands): request clarification.
            require(!s.matches(Regex("[+-]?\\d{1,3},\\d{3}"))) { "מספר עמום: $text — השתמש בנקודה עשרונית וללא מפריד אלפים" }
            s = s.replace(',', '.')
        }
        val n = s.toDoubleOrNull()
        require(n != null && n.isFinite() && n >= 0) { "מספר לא תקין: $text" }
        return n
    }

    private fun date(text: String): Long {
        for ((format, pattern) in listOf(
            "yyyy-MM-dd" to "\\d{4}-\\d{1,2}-\\d{1,2}",
            "dd/MM/yyyy" to "\\d{1,2}/\\d{1,2}/\\d{4}",
            "dd.MM.yyyy" to "\\d{1,2}\\.\\d{1,2}\\.\\d{4}",
            "dd-MM-yyyy" to "\\d{1,2}-\\d{1,2}-\\d{4}")) {
            if (!text.matches(Regex(pattern))) continue
            val f=SimpleDateFormat(format,Locale.ROOT).apply { isLenient=false }
            val pos=ParsePosition(0); val parsed=f.parse(text,pos)
            if (parsed != null && pos.index == text.length && text.matches(Regex(".*\\d{4}.*"))) return parsed.time
        }
        if (text.matches(Regex("\\d{13}"))) return text.toLong()
        if (text.matches(Regex("\\d{5}"))) {
            val serial=text.toInt(); require(serial in 20000..100000) { "תאריך אקסל מחוץ לטווח" }
            return Calendar.getInstance().apply { clear(); set(1899,11,30); add(Calendar.DAY_OF_MONTH,serial) }.timeInMillis
        }
        error("תאריך לא מזוהה: $text. השתמש ביום/חודש/שנה או שנה-חודש-יום")
    }

    fun table(text: String, delimiter: Char): List<List<String>> {
        val rows=mutableListOf<List<String>>(); var row=mutableListOf<String>(); val cell=StringBuilder()
        var quoted=false; var i=0
        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow() { endCell(); if(row.any { it.isNotBlank() }) rows.add(row); row=mutableListOf() }
        while(i<text.length) {
            val c=text[i]
            when {
                c=='"' && quoted && i+1<text.length && text[i+1]=='"' -> { cell.append('"'); i++ }
                c=='"' && (quoted || cell.isEmpty()) -> quoted=!quoted
                c==delimiter && !quoted -> endCell()
                (c=='\n' || c=='\r') && !quoted -> { endRow(); if(c=='\r' && i+1<text.length && text[i+1]=='\n') i++ }
                else -> cell.append(c)
            }; i++
        }
        require(!quoted) { "מרכאות לא סגורות" }; endRow(); return rows
    }
}
