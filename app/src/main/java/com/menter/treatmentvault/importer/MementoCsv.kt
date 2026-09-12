package com.menter.treatmentvault.importer

import com.menter.treatmentvault.data.AppDao
import com.menter.treatmentvault.data.SupplyEntity

object MementoCsv {
    fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    out += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        out += current.toString()
        return out
    }

    suspend fun import(text: String, dao: AppDao): Int {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return 0

        val header = parseLine(lines.first())
        val indexByName = header.withIndex().associate { it.value.trim() to it.index }

        fun value(row: List<String>, key: String): String {
            val index = indexByName[key] ?: return ""
            return row.getOrNull(index)?.trim().orEmpty()
        }

        var imported = 0
        for (line in lines.drop(1)) {
            val row = parseLine(line)
            val name = value(row, "名称")
            if (name.isBlank()) continue

            val existing = dao.supplyByName(name)
            dao.saveSupply(
                SupplyEntity(
                    id = existing?.id ?: 0,
                    name = name,
                    stages = value(row, "使用阶段"),
                    purpose = value(row, "作用"),
                    necessity = value(row, "必要性").ifBlank { "按需购买" },
                    purchaseStatus = value(row, "购买状态").ifBlank {
                        value(row, "已购").ifBlank { "待购" }
                    },
                    quantity = value(row, "数量").toIntOrNull(),
                    price = value(row, "价格").toDoubleOrNull(),
                    purchaseUrl = value(row, "购买链接"),
                    notes = value(row, "备注"),
                )
            )
            imported++
        }
        return imported
    }
}
