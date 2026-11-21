package app.openfitness.app

import androidx.compose.ui.window.ComposeUIViewController
import app.openfitness.app.data.AppStore
import app.openfitness.app.data.JsonFileStore
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private val store: AppStore by lazy {
    val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    AppStore(JsonFileStore(FileSystem.SYSTEM, "$docs/openfitness.json".toPath()))
}

@Suppress("FunctionName", "unused") // Called from Swift.
fun MainViewController() = ComposeUIViewController { App(store) }
