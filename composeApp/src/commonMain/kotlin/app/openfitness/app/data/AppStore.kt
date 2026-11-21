package app.openfitness.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/** Reads and writes [AppData] as one JSON file. */
class JsonFileStore(
    private val fileSystem: FileSystem,
    private val path: Path,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): AppData {
        if (!fileSystem.exists(path)) return AppData()
        return runCatching { json.decodeFromString<AppData>(fileSystem.read(path) { readUtf8() }) }
            .getOrElse { AppData() }
    }

    fun save(data: AppData) {
        path.parent?.let { fileSystem.createDirectories(it) }
        val tmp = path.parent!! / (path.name + ".tmp")
        fileSystem.write(tmp) { writeUtf8(json.encodeToString(AppData.serializer(), data)) }
        fileSystem.atomicMove(tmp, path)
    }
}

/** Holds the app state in memory and saves each change to the store. */
class AppStore(private val file: JsonFileStore) {
    private val _state = MutableStateFlow(file.load())
    val state: StateFlow<AppData> = _state.asStateFlow()

    fun update(change: (AppData) -> AppData) {
        val next = change(_state.value)
        if (next == _state.value) return
        _state.value = next
        file.save(next)
    }

    fun updateBlock(change: (Block) -> Block) = update { data ->
        data.block?.let { data.copy(block = change(it)) } ?: data
    }
}
