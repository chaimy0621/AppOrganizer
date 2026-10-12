package com.apporganizer.launcher.domain

/** 智能文件夹类别。声明顺序 = 首页默认文件夹顺序。 */
enum class Category(val displayName: String) {
    WORK("工作"),
    COMMUNICATION("通讯"),
    FINANCE("金融"),
    MEDIA("影音"),
    TOOLS("工具"),
    SHOPPING("购物"),
    TRAVEL("出行"),
    GAMES("游戏"),
    OTHER("其他");

    companion object {
        fun fromName(name: String?): Category =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
