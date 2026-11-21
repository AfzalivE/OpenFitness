package app.openfitness.app

import android.app.Application
import app.openfitness.app.data.AppStore
import app.openfitness.app.data.JsonFileStore
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class OpenFitnessApp : Application() {
    /** One store for the process, so that it stays the same when an activity is made again. */
    val store: AppStore by lazy {
        AppStore(JsonFileStore(FileSystem.SYSTEM, filesDir.resolve("openfitness.json").toOkioPath()))
    }
}
