package com.apporganizer.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifierTest {
    private fun c(pkg: String, label: String = "", sys: Int = -1, game: Boolean = false) =
        Classifier.classify(Classifier.Input(pkg, label, sys, game))

    @Test fun `exact package map wins`() {
        assertEquals(Category.COMMUNICATION, c("com.whatsapp", "WhatsApp"))
        assertEquals(Category.SHOPPING, c("com.shopee.my", "Shopee"))
        assertEquals(Category.TRAVEL, c("com.grabtaxi.passenger", "Grab"))
        assertEquals(Category.WORK, c("com.Slack", "Slack"))
    }

    @Test fun `prefix map`() {
        assertEquals(Category.WORK, c("com.microsoft.office.excel", "Excel"))
        assertEquals(Category.FINANCE, c("com.maybank2u.life", "MAE"))
    }

    @Test fun `system category used when no map entry`() {
        assertEquals(Category.MEDIA, c("org.example.player", "X", sys = 2))
        assertEquals(Category.GAMES, c("org.example.fun", "X", game = true))
    }

    @Test fun `keywords in english and chinese`() {
        assertEquals(Category.FINANCE, c("com.examplebank.mobile", "Example Bank"))
        assertEquals(Category.FINANCE, c("cn.example.app", "某某银行"))
        assertEquals(Category.TOOLS, c("org.example.calculator", "Calc"))
    }

    @Test fun `short keywords need whole token`() {
        // "tv" 不应误匹配 "...activity..."
        assertEquals(Category.OTHER, c("org.activitylab.app", "Activity Lab"))
        assertEquals(Category.MEDIA, c("org.example.tv", "Example TV"))
    }

    @Test fun `unknown falls back to other`() {
        assertEquals(Category.OTHER, c("org.unknown.zzz", "Zzz"))
    }
}
