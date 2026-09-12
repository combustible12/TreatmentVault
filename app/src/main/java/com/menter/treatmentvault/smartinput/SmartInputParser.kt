package com.menter.treatmentvault.smartinput

import com.menter.treatmentvault.data.CategoryEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class SmartCategory(val label: String) {
    TEMPERATURE("体温"), QUESTION("待问医生"), EXPENSE("费用"), EXAM("检查报告"),
    TREATMENT("治疗记录"), SYMPTOM("症状"), SUPPLY("用品/药品待确认"), CUSTOM("自定义类目"), OTHER("待确认")
}

data class SmartParsedItem(
    val category: SmartCategory,
    val text: String,
    val date: String,
    val time: String? = null,
    val temperature: Double? = null,
    val amount: Double? = null,
    val customCategoryId: String? = null,
    val customCategoryName: String? = null,
) {
    val displayLabel: String get() = customCategoryName ?: category.label
}

object SmartInputParser {
    private val temperatureRegex = Regex("(3[4-9]|4[0-3])(?:\\.(\\d))?\\s*(?:℃|度)")
    private val currencyRegex = Regex("(?:¥|￥)\\s*(\\d+(?:\\.\\d{1,2})?)|(\\d+(?:\\.\\d{1,2})?)\\s*元")
    private val timeRegex = Regex("(?:^|\\D)([01]?\\d|2[0-3])[:：]([0-5]\\d)(?:\\D|$)")
    private val fullDateRegex = Regex("(20\\d{2})[年/.-](\\d{1,2})[月/.-](\\d{1,2})日?")
    private val shortDateRegex = Regex("(?<!\\d)(\\d{1,2})月(\\d{1,2})日")

    fun parse(raw: String, defaultDate: String, defaultTime: String, categories: List<CategoryEntity> = emptyList()): List<SmartParsedItem> {
        if (raw.isBlank()) return emptyList()
        val custom = categories.filter { !it.isSystem && !it.hidden && it.smartEnabled }
        val segments = raw.replace("\r", "").split(Regex("(?<=[。！？!?；;])|\\n+")).map { it.trim().trim('。', '；', ';') }.filter { it.isNotBlank() }
        val out = mutableListOf<SmartParsedItem>()
        for (segment in segments) {
            val date = extractDate(segment, defaultDate)
            val time = extractTime(segment) ?: defaultTime
            var matched = false
            val temperature = extractTemperature(segment)
            if (temperature != null) { out += SmartParsedItem(SmartCategory.TEMPERATURE, segment, date, time, temperature = temperature); matched = true }
            if (containsAny(segment, listOf("要问","问医生","请问","想问","需要问","？","?"))) { out += SmartParsedItem(SmartCategory.QUESTION, segment, date); matched = true }
            val amount = extractAmount(segment)
            if (amount != null && containsAny(segment, listOf("花了","费用","挂号","缴费","交了","支付","自费","报销","元","¥","￥"))) { out += SmartParsedItem(SmartCategory.EXPENSE, segment, date, amount = amount); matched = true }
            if (containsAny(segment, listOf("MRI","磁共振","CT","PET","病理","活检","免疫组化","彩超","心电图","抽血","血常规","尿检","检查","报告"))) { out += SmartParsedItem(SmartCategory.EXAM, segment, date); matched = true }
            if (containsAny(segment, listOf("化疗","放疗","手术","治疗","定位","输液港","PICC","方案","周期","复查","评估"))) { out += SmartParsedItem(SmartCategory.TREATMENT, segment, date); matched = true }
            if (containsAny(segment, listOf("尿频","尿急","尿痛","恶心","呕吐","腹泻","便秘","出血","疼","痛","乏力","疲劳","没胃口","食欲","发热","发烧","分泌物","头晕","腹胀"))) { out += SmartParsedItem(SmartCategory.SYMPTOM, segment, date, temperature = temperature); matched = true }
            if (containsAny(segment, listOf("买","购买","准备","用品","喷雾","体温计","蛋白粉","营养粉","乳果糖","益生菌","比亚芬","康复新液","药店"))) { out += SmartParsedItem(SmartCategory.SUPPLY, segment, date); matched = true }
            custom.forEach { category ->
                val keys = category.smartKeywords.split(',', '，', ';', '；').map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf(category.name) }
                if (containsAny(segment, keys)) {
                    out += SmartParsedItem(SmartCategory.CUSTOM, segment, date, customCategoryId = category.key, customCategoryName = category.name)
                    matched = true
                }
            }
            if (!matched) out += SmartParsedItem(SmartCategory.OTHER, segment, date)
        }
        return out.distinctBy { listOf(it.category.name, it.customCategoryId.orEmpty(), it.text, it.date).joinToString("|") }
    }

    private fun extractTemperature(text:String):Double? { val m=temperatureRegex.find(text)?:return null; return if(m.groupValues[2].isBlank()) m.groupValues[1].toDoubleOrNull() else "${m.groupValues[1]}.${m.groupValues[2]}".toDoubleOrNull() }
    private fun extractAmount(text:String):Double? { val m=currencyRegex.find(text)?:return null; return m.groupValues.drop(1).firstOrNull{it.isNotBlank()}?.toDoubleOrNull() }
    private fun extractTime(text:String):String? { val m=timeRegex.find(text)?:return null; return "%02d:%02d".format(m.groupValues[1].toInt(),m.groupValues[2].toInt()) }
    private fun extractDate(text:String,defaultDate:String):String {
        fullDateRegex.find(text)?.let{m->return "%04d-%02d-%02d".format(m.groupValues[1].toInt(),m.groupValues[2].toInt(),m.groupValues[3].toInt())}
        shortDateRegex.find(text)?.let{m->val y=defaultDate.take(4).toIntOrNull()?:Calendar.getInstance().get(Calendar.YEAR);return "%04d-%02d-%02d".format(y,m.groupValues[1].toInt(),m.groupValues[2].toInt())}
        if(text.contains("昨天")){val f=SimpleDateFormat("yyyy-MM-dd",Locale.getDefault());val d=f.parse(defaultDate)?:return defaultDate;val c=Calendar.getInstance().apply{time=d;add(Calendar.DAY_OF_YEAR,-1)};return f.format(c.time)}
        return defaultDate
    }
    private fun containsAny(text:String,keywords:List<String>)=keywords.any{text.contains(it,ignoreCase=true)}
}
