package com.apporganizer.launcher.domain

/** 智能文件夹类别。声明顺序 = 首页默认文件夹顺序。 */
enum class Category(val displayName: String) {
    WORK("Work"),
    COMMUNICATION("Social"),
    FINANCE("Finance"),
    MEDIA("Media"),
    TOOLS("Tools"),
    SHOPPING("Shopping"),
    TRAVEL("Travel"),
    GAMES("Games"),
    OTHER("Other");

    companion object {
        fun fromName(name: String?): Category =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
