import java.io.File
import java.util.*
import kotlin.io.path.Path

internal val ASTMINER_PATH = Path("reps/astminer").toAbsolutePath()
internal const val ASTMINER_URL = "https://github.com/JetBrains-Research/astminer.git"

internal const val TEAMCITY_PATH = "http://localhost:8111"

internal const val SECRETS_PATH = "./src/main/resources/secrets/"

internal const val JSON_FORMAT = "application/json"

internal const val NUMBER_OF_COMMITS = 6


fun getBearerAuthSecret(): String {
    return File(SECRETS_PATH + "bearer_auth.txt").readText()
}

enum class PropertyType(val str: String) {
    ARTIFACT_SIZE("ArtifactsSize"),
    BUILD_DURATION("BuildDuration"),
    BUILD_TEST_STATUS("BuildTestStatus"),
    IGNORED_TEST_COUNT("IgnoredTestCount"),
    PASSED_TEST_COUNT("PassedTestCount"),
    SUCCESS_RATE("SuccessRate"),
    TOTAL_TEST_COUNT("TotalTestCount")
}


val propertyNames: List<PropertyType> = listOf(
    PropertyType.ARTIFACT_SIZE,
    PropertyType.BUILD_DURATION,
    PropertyType.BUILD_TEST_STATUS,
    PropertyType.IGNORED_TEST_COUNT,
    PropertyType.PASSED_TEST_COUNT,
    PropertyType.SUCCESS_RATE,
    PropertyType.TOTAL_TEST_COUNT
)

class BuildStatistics(val hash: String, val buildId: String) {
    private val statMap: MutableMap<PropertyType, Int> = EnumMap(PropertyType::class.java)

    constructor(hash: String, buildId: String, property: List<Property>) : this(hash, buildId) {
        propertyNames.forEach {
            statMap[it] = property.find { prop -> prop.name == it.str }?.value?.toInt() ?: -1
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("\nBuild: buildId=$buildId, hash=$hash\n")
        statMap.forEach { sb.append("$it\n") }
        return sb.toString()
    }

    fun getBuildDuration(): Int {
        return statMap[PropertyType.BUILD_DURATION]!!
    }

    fun getBuildTestStatus(): Int {
        return statMap[PropertyType.BUILD_TEST_STATUS]!!
    }

    fun getArtifactSize(): Int {
        return statMap[PropertyType.ARTIFACT_SIZE]!!
    }

    fun getIgnoredTestCount(): Int {
        return statMap[PropertyType.IGNORED_TEST_COUNT]!!
    }

    fun getPassedTestCount(): Int {
        return statMap[PropertyType.PASSED_TEST_COUNT]!!
    }

    fun getTotalTestCount(): Int {
        return statMap[PropertyType.TOTAL_TEST_COUNT]!!
    }

    fun getSuccessRate(): Int {
        return statMap[PropertyType.SUCCESS_RATE]!!
    }
}