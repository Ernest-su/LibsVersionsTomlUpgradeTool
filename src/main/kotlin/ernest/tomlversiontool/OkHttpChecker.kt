import okhttp3.OkHttpClient
import okhttp3.Request
import org.tomlj.Toml
import org.xml.sax.InputSource
import java.io.File
import java.io.FileReader
import java.io.StringReader
import java.net.InetSocketAddress
import java.net.Proxy
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory


class OkHttpChecker {
    // 创建SOCKS5代理
    val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 7891))

    // 创建OkHttpClient，设置代理
    val client = OkHttpClient.Builder()
        .proxy(proxy)
        .build()

    val mavenUrl = "https://repo1.maven.org"
    val googleMavenUrl = "https://maven.google.com"

    val dbf: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
    val xpathFactory: XPathFactory = XPathFactory.newInstance()
    val releasePath = xpathFactory.newXPath().compile("//metadata//versioning//release")!!

    fun getLatestVersion(
        packageName: String
    ): String {
        val (groupId, artifactId) = packageName.split(":")
        println("checking $groupId:$artifactId")
        val path = "${groupId.replace(".", "/")}/$artifactId"

        // Try Maven Central first
        val mavenCentralVersion = getVersionFromRepository("$mavenUrl/maven2/$path/maven-metadata.xml")
        if (mavenCentralVersion != null) {
            return mavenCentralVersion
        }

        // If not found in Maven Central, try Google's Maven repository
        val googleMavenVersion = getVersionFromRepository("$googleMavenUrl/$path/maven-metadata.xml")
        if (googleMavenVersion != null) {
            return googleMavenVersion
        }

        return ""
    }

    private fun getVersionFromRepository(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val xmlString = response.body?.string() ?: return null
                val db = dbf.newDocumentBuilder()
                val document = db.parse(InputSource(StringReader(xmlString)))
                return releasePath.evaluate(document, XPathConstants.STRING).toString()
            }
        } catch (e: Exception) {
            println(e)
        }
        return null
    }

    fun check() {
        val filePath = "./libs.versions.toml"
        val toml = FileReader(filePath).use { file ->
            Toml.parse(file)
        }

        val versions = toml.getTable("versions")!!.toMap()
        val libraries = toml.getTable("libraries")!!

        val libraryEntries = libraries.keySet().associate { key ->
            key to LibraryEntry(
                libraries.getString("$key.module"),
                libraries.getString("$key.version.ref")
            )
        }

        val (currentVersionRefs, missingVersions) = libraryEntries.entries.partition { it.value.versionRef != null }

        val currentVersions = currentVersionRefs.associate { (key, entry) ->
            entry.module!! to versions.getValue(entry.versionRef!!)
        }

        val latestVersions = currentVersions.mapValues { (packageName, _) ->
            getLatestVersion(packageName)
        }


        // Compare current to latest version and print if different
        currentVersions.toSortedMap().forEach { (packageName, currentVersion) ->
            val latestVersion = latestVersions.getValue(packageName)
            if (currentVersion != latestVersion && latestVersion.isNotEmpty()) {
                println("$packageName:$latestVersion <-$currentVersion ")
                updateVersionInToml(filePath, packageName, latestVersion, libraryEntries)
            }
        }
        if (missingVersions.isNotEmpty()) {
            println("\n--- Missing version.ref - Need to check manually ---\n")
            println(missingVersions.map { it.value.module }.sortedBy { it }.joinToString("\n"))
        }
    }

    private fun updateVersionInToml(
        filePath: String,
        packageName: String,
        newVersion: String,
        libraryEntries: Map<String, LibraryEntry>
    ) {
        val entry = libraryEntries.entries.find { it.value.module == packageName }
        if (entry != null) {
            val (key, libraryEntry) = entry
            if (libraryEntry.versionRef != null) {
                updateVersionsTable(filePath, libraryEntry.versionRef, newVersion)
            } else {
                updateLibrariesTable(filePath, key, newVersion)
            }
        }
    }

    private fun updateVersionsTable(filePath: String, versionRef: String, newVersion: String) {
        val content = File(filePath).readText()
        val updatedContent = content.replace(
            Regex("($versionRef\\s*=\\s*\")([^\"]*)(\")"),
            "$1$newVersion$3"
        )
        File(filePath).writeText(updatedContent)
    }

    private fun updateLibrariesTable(filePath: String, libraryKey: String, newVersion: String) {
        val content = File(filePath).readText()
        val updatedContent = content.replace(
            Regex("($libraryKey\\s*=\\s*\\{[^}]*version\\s*=\\s*\")([^\"]*)(\")"),
            "$1$newVersion$3"
        )
        File(filePath).writeText(updatedContent)
    }

}

data class LibraryEntry(val module: String?, val versionRef: String?)

fun main() {
    OkHttpChecker().check()
}