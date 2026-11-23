package com.adrinand.gokfre.core.screen

import io.kotest.matchers.shouldBe
import org.junit.Test

class LinuxExtensionStateParserTest {
    @Test
    fun `extension state is parsed from gdbus output`() {
        parseExtensionState("({'state': <uint32 1>, 'enabled': <true>},)") shouldBe 1
        parseExtensionState("({'state': <1.0>, 'enabled': <true>},)") shouldBe 1
        parseExtensionState("({'state': <uint32 4>},)") shouldBe 4
        parseExtensionState("({'_generated': <'x'>, 'version': <66.0>, 'state': <2.0>},)") shouldBe 2
        parseExtensionState("Error: not found") shouldBe null
    }
}
