package com.apporganizer.launcher.domain

/** Smart-folder categories. Declaration order = default folder order. */
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
