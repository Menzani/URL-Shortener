package it.menzani.urlshortener

import com.beust.klaxon.Klaxon
import java.nio.file.Files
import java.nio.file.Paths

class DataSource {
    private val config: Config

    init {
        val path = Paths.get("config.json")
        val klaxon = Klaxon()
        if (Files.exists(path)) {
            config = Files.newBufferedReader(path).use { klaxon.parse(it)!! }
        } else {
            config = Config()
            Files.newBufferedWriter(path).use { it.write(klaxon.toJsonString(config)) }
        }
    }

    fun port() = config.port

    fun lookup(code: String) = config.codes[code]
}

data class Config(
        val port: Int = 80,
        val codes: Map<String, String> = mapOf("test" to "https://www.google.com")
)