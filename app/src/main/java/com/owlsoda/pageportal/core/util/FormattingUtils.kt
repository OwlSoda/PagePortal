package com.owlsoda.pageportal.core.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object FormattingUtils {
    fun formatAuthors(authorsJson: String): String {
        return try {
            if (authorsJson.trim().startsWith("[")) {
                val type = object : TypeToken<List<String>>() {}.type
                val authorsList: List<String> = Gson().fromJson(authorsJson, type)
                authorsList.joinToString(", ")
            } else {
                authorsJson
            }
        } catch (e: Exception) {
            authorsJson.replace("[\"", "").replace("\"]", "").replace("\",\"", ", ")
        }
    }
}
