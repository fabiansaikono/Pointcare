package be.saikono.poincare

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineDutchTtsAssetsTest {
    @Test
    fun bundledModelContainsRuntimeFiles() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        assets.open("tts/vits-piper-nl_BE-nathalie-medium/nl_BE-nathalie-medium.onnx").use {
            assertTrue(it.read() >= 0)
        }
        assets.open("tts/vits-piper-nl_BE-nathalie-medium/tokens.txt").use {
            assertTrue(it.read() >= 0)
        }
        assets.open("tts/vits-piper-nl_BE-nathalie-medium/espeak-ng-data/nl_dict").use {
            assertTrue(it.read() >= 0)
        }
    }
}
