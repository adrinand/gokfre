package com.adrinand.gokfre

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

/**
 * JUnit 5 extension that launches a fresh gokfre E2E instance before each test
 * and stops it afterwards.
 */
class E2EExtension : BeforeEachCallback, AfterEachCallback {
    lateinit var app: E2EAppHandle
        private set

    lateinit var settingsPath: Path
        private set

    override fun beforeEach(context: ExtensionContext) {
        settingsPath = createTempDirectory("gokfre-e2e-settings").resolve("settings.json")
        app = E2EApplication.launch(settingsPath)
    }

    override fun afterEach(context: ExtensionContext) {
        if (::app.isInitialized) {
            app.stop()
        }
    }
}
