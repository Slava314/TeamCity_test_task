import java.io.File
import kotlin.io.path.Path

internal val ASTMINER_PATH = Path("./reps/astminer")
internal const val ASTMINER_URL = "https://github.com/JetBrains-Research/astminer.git"

internal val TEAMCITY_PATH = "http://localhost:8111"

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
    val statMap: MutableMap<PropertyType, Int> = HashMap()

    constructor(hash: String, buildId: String, property: List<Property>) : this(hash, buildId) {
        propertyNames.forEach {
            statMap[it] = property.find { prop -> prop.name == it.str }?.value?.toInt()
                ?: throw IllegalStateException("expected $it property")
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("\nBuild: buildId=$buildId, hash=$hash\n")
        statMap.forEach { sb.append("$it\n") }
        return sb.toString()
    }
}