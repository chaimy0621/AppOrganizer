package com.apporganizer.launcher.widget

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.apporganizer.launcher.App

/** Invisible hop: opens the app tapped in the widget, remembers the tap, then disappears. */
class LaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val cls = intent.getStringExtra(EXTRA_CLASS)
        if (pkg != null && cls != null) {
            val ok = (application as App).container.repository.launchComponent(pkg, cls, intent.sourceBounds)
            if (!ok) Toast.makeText(this, "Can't open this app", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_CLASS = "cls"
    }
}
