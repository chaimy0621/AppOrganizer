package com.apporganizer.launcher.domain

/**
 * Local, offline auto-classifier (no LLM, no network).
 *
 * Priority (high → low):
 *  0. Manual user choice (handled elsewhere: auto-classification is skipped when AppEntity.userFolderId is set)
 *  1. Exact package-name map
 *  2. Package-prefix map
 *  3. Game flag / Android ApplicationInfo.category
 *  4. Keyword rules on package name + app label (English and Chinese keywords)
 *  5. OTHER
 */
object Classifier {

    data class Input(
        val packageName: String,
        val label: String,
        /** ApplicationInfo.category; -1 when not declared */
        val systemCategory: Int,
        val isGame: Boolean,
    )

    // Same values as android.content.pm.ApplicationInfo.CATEGORY_*;
    // redeclared here so the domain layer has no Android SDK dependency and runs in JVM unit tests.
    private const val CAT_GAME = 0
    private const val CAT_AUDIO = 1
    private const val CAT_VIDEO = 2
    private const val CAT_IMAGE = 3
    private const val CAT_SOCIAL = 4
    private const val CAT_NEWS = 5
    private const val CAT_MAPS = 6
    private const val CAT_PRODUCTIVITY = 7
    private const val CAT_ACCESSIBILITY = 8

    fun classify(input: Input): Category {
        val pkg = input.packageName.lowercase()
        PACKAGE_MAP[pkg]?.let { return it }
        PREFIX_MAP.firstOrNull { pkg.startsWith(it.first) }?.let { return it.second }
        if (input.isGame) return Category.GAMES
        fromSystemCategory(input.systemCategory)?.let { return it }
        fromKeywords(pkg, input.label)?.let { return it }
        return Category.OTHER
    }

    internal fun fromSystemCategory(cat: Int): Category? = when (cat) {
        CAT_GAME -> Category.GAMES
        CAT_AUDIO, CAT_VIDEO, CAT_IMAGE, CAT_NEWS -> Category.MEDIA
        CAT_SOCIAL -> Category.COMMUNICATION
        CAT_MAPS -> Category.TRAVEL
        CAT_PRODUCTIVITY -> Category.WORK
        CAT_ACCESSIBILITY -> Category.TOOLS
        else -> null
    }

    internal fun fromKeywords(packageName: String, label: String): Category? {
        val haystack = "$packageName $label".lowercase()
        val tokens = haystack.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
        for ((category, words) in KEYWORD_RULES) {
            for (w in words) {
                val matched = when {
                    w.any { it.code > 127 } -> haystack.contains(w) // Chinese keyword: substring match
                    w.length <= 3 -> w in tokens                     // Short word: whole-word match to avoid false hits
                    else -> haystack.contains(w)
                }
                if (matched) return category
            }
        }
        return null
    }

    /** Exact map of common apps (including popular Malaysian apps). Extend over time. */
    private val PACKAGE_MAP: Map<String, Category> = mapOf(
        // Communication
        "com.whatsapp" to Category.COMMUNICATION,
        "com.whatsapp.w4b" to Category.COMMUNICATION,
        "org.telegram.messenger" to Category.COMMUNICATION,
        "com.facebook.orca" to Category.COMMUNICATION,
        "com.facebook.katana" to Category.COMMUNICATION,
        "com.instagram.android" to Category.COMMUNICATION,
        "com.tencent.mm" to Category.COMMUNICATION,
        "jp.naver.line.android" to Category.COMMUNICATION,
        "com.discord" to Category.COMMUNICATION,
        "org.thoughtcrime.securesms" to Category.COMMUNICATION,
        "com.samsung.android.messaging" to Category.COMMUNICATION,
        "com.google.android.apps.messaging" to Category.COMMUNICATION,
        "com.samsung.android.dialer" to Category.COMMUNICATION,
        "com.google.android.dialer" to Category.COMMUNICATION,
        "com.samsung.android.app.contacts" to Category.COMMUNICATION,
        "com.twitter.android" to Category.COMMUNICATION,
        // Work
        "com.google.android.gm" to Category.WORK,
        "com.microsoft.office.outlook" to Category.WORK,
        "com.microsoft.teams" to Category.WORK,
        "com.slack" to Category.WORK,
        "us.zoom.videomeetings" to Category.WORK,
        "com.google.android.apps.meetings" to Category.WORK,
        "com.google.android.calendar" to Category.WORK,
        "com.samsung.android.calendar" to Category.WORK,
        "com.linkedin.android" to Category.WORK,
        "com.notion.id" to Category.WORK,
        "com.azure.authenticator" to Category.WORK,
        "com.google.android.apps.authenticator2" to Category.WORK,
        "com.microsoft.skydrive" to Category.WORK,
        // Media
        "com.google.android.youtube" to Category.MEDIA,
        "com.spotify.music" to Category.MEDIA,
        "com.netflix.mediaclient" to Category.MEDIA,
        "com.zhiliaoapp.musically" to Category.MEDIA,
        "com.ss.android.ugc.trill" to Category.MEDIA,
        "com.google.android.apps.photos" to Category.MEDIA,
        "com.sec.android.gallery3d" to Category.MEDIA,
        "com.sec.android.app.camera" to Category.MEDIA,
        "com.google.android.apps.youtube.music" to Category.MEDIA,
        // Shopping
        "com.shopee.my" to Category.SHOPPING,
        "com.lazada.android" to Category.SHOPPING,
        "com.amazon.mshop.android.shopping" to Category.SHOPPING,
        "com.einnovation.temu" to Category.SHOPPING,
        "com.zzkko" to Category.SHOPPING,
        // Travel
        "com.grabtaxi.passenger" to Category.TRAVEL,
        "com.google.android.apps.maps" to Category.TRAVEL,
        "com.waze" to Category.TRAVEL,
        "com.airbnb.android" to Category.TRAVEL,
        "com.agoda.mobile.consumer" to Category.TRAVEL,
        "com.booking" to Category.TRAVEL,
        "com.airasia.mobile" to Category.TRAVEL,
        // Finance
        "my.com.tngdigital.ewallet" to Category.FINANCE,
        "com.paypal.android.p2pmobile" to Category.FINANCE,
        "com.samsung.android.spay" to Category.FINANCE,
        // Tools
        "com.android.chrome" to Category.TOOLS,
        "com.sec.android.app.sbrowser" to Category.TOOLS,
        "com.android.vending" to Category.TOOLS,
        "com.android.settings" to Category.TOOLS,
        "com.sec.android.app.myfiles" to Category.TOOLS,
        "com.sec.android.app.popupcalculator" to Category.TOOLS,
        "com.sec.android.app.clockpackage" to Category.TOOLS,
        "com.samsung.android.app.notes" to Category.TOOLS,
        "com.google.android.apps.translate" to Category.TOOLS,
        "com.google.android.googlequicksearchbox" to Category.TOOLS,
    )

    private val PREFIX_MAP: List<Pair<String, Category>> = listOf(
        "com.microsoft.office" to Category.WORK,
        "com.google.android.apps.docs" to Category.WORK,
        "com.atlassian" to Category.WORK,
        "com.maybank2u" to Category.FINANCE,
        "com.cimb" to Category.FINANCE,
        "com.rhbgroup" to Category.FINANCE,
        "com.binance" to Category.FINANCE,
        "com.samsung.android.game" to Category.GAMES,
        "com.supercell" to Category.GAMES,
        "com.mihoyo" to Category.GAMES,
    )

    /** Keyword rules are checked in order and the first hit wins (finance before shopping so "pay" is not misread). */
    private val KEYWORD_RULES: List<Pair<Category, List<String>>> = listOf(
        Category.FINANCE to listOf(
            "bank", "banking", "pay", "paypal", "wallet", "ewallet", "finance", "invest", "trading",
            "crypto", "insurance", "securities", "stock", "tng", "maybank", "cimb", "ambank", "money",
            "银行", "支付", "钱包", "理财", "证券", "保险",
        ),
        Category.COMMUNICATION to listOf(
            "whatsapp", "telegram", "messenger", "messaging", "messages", "chat", "dialer", "contacts",
            "call", "sms", "line", "signal", "viber", "wechat", "discord", "skype",
            "聊天", "短信", "电话", "信息", "通讯录",
        ),
        Category.WORK to listOf(
            "mail", "email", "office", "docs", "drive", "calendar", "meet", "teams", "slack", "zoom",
            "vpn", "authenticator", "outlook", "notion", "jira", "confluence", "salesforce", "sheets",
            "slides", "crm", "workspace",
            "邮件", "日历", "办公", "会议", "文档",
        ),
        Category.SHOPPING to listOf(
            "shop", "shopping", "mall", "store", "lazada", "shopee", "mart", "amazon", "taobao", "ebay",
            "zalora", "temu", "shein", "grocer",
            "购物", "商城", "超市",
        ),
        Category.TRAVEL to listOf(
            "maps", "map", "navigation", "travel", "flight", "airline", "airasia", "hotel", "booking",
            "taxi", "ride", "transit", "train", "bus", "grab", "waze", "uber", "airbnb", "agoda", "trip",
            "地图", "出行", "旅行", "酒店", "航空", "打车",
        ),
        Category.MEDIA to listOf(
            "music", "video", "player", "movie", "tv", "camera", "gallery", "photo", "photos", "podcast",
            "radio", "stream", "youtube", "spotify", "netflix", "tiktok", "musically", "recorder", "news",
            "音乐", "视频", "相机", "相册", "电影", "新闻",
        ),
        Category.GAMES to listOf("game", "games", "游戏"),
        Category.TOOLS to listOf(
            "calculator", "calc", "clock", "settings", "files", "filemanager", "myfiles", "tool", "tools",
            "weather", "scanner", "notes", "note", "compass", "flashlight", "backup", "cleaner",
            "keyboard", "translate", "browser",
            "计算器", "设置", "文件", "时钟", "天气", "备忘", "浏览器",
        ),
    )
}
