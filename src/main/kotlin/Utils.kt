import java.io.File
import java.util.*
import kotlin.io.path.Path

internal val ASTMINER_PATH = Path("reps/astminer").toAbsolutePath()
internal const val ASTMINER_URL = "https://github.com/JetBrains-Research/astminer.git"

internal const val TEAMCITY_PATH = "http://localhost:8111"

internal const val SECRETS_PATH = "./src/main/resources/secrets/"

internal const val JSON_FORMAT = "application/json"

internal const val NUMBER_OF_COMMITS = 100
internal const val BATCH_SIZE = 10

/**
 * Read Auth token from secret.
 * @return Auth token for Teamcity.
 */
fun getBearerAuthSecret(): String {
    return File(SECRETS_PATH + "bearer_auth.txt").readText()
}

/**
 * Properties of Build Statistic
 * @property str the name of property.
 */
enum class PropertyType(val str: String) {
    ARTIFACT_SIZE("ArtifactsSize"),
    BUILD_DURATION("BuildDuration"),
    BUILD_TEST_STATUS("BuildTestStatus"),
    IGNORED_TEST_COUNT("IgnoredTestCount"),
    PASSED_TEST_COUNT("PassedTestCount"),
    SUCCESS_RATE("SuccessRate"),
    TOTAL_TEST_COUNT("TotalTestCount")
}


/**
 * Class contains build statistics
 * @property hash hash of with which this build was run
 * @property buildId id of build
 * @property statMap map from property name to value
 */
class BuildStatistics(val hash: String, val buildId: String) {
    private val statMap: MutableMap<PropertyType, Int> = EnumMap(PropertyType::class.java)

    constructor(hash: String, buildId: String, property: List<Property>) : this(hash, buildId) {
        PropertyType.values().forEach {
            statMap[it] = property.find { prop -> prop.name == it.str }?.value?.toInt() ?: -1
        }
    }

    /**
     * @return pretty string view of build
     */
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("\nBuild: buildId=$buildId, hash=$hash")
        statMap.forEach { sb.appendLine("$it") }
        return sb.toString()
    }

    /**
     * @return value of BUILD_DURATION property
     */
    fun getBuildDuration(): Int {
        return statMap[PropertyType.BUILD_DURATION]!!
    }

    /**
     * @return value of BUILD_TEST_STATUS property
     */
    fun getBuildTestStatus(): Int {
        return statMap[PropertyType.BUILD_TEST_STATUS]!!
    }

    /**
     * @return value of ARTIFACT_SIZE property
     */
    fun getArtifactSize(): Int {
        return statMap[PropertyType.ARTIFACT_SIZE]!!
    }

    /**
     * @return value of IGNORED_TEST_COUNT property
     */
    fun getIgnoredTestCount(): Int {
        return statMap[PropertyType.IGNORED_TEST_COUNT]!!
    }

    /**
     * @return value of PASSED_TEST_COUNT property
     */
    fun getPassedTestCount(): Int {
        return statMap[PropertyType.PASSED_TEST_COUNT]!!
    }

    /**
     * @return value of TOTAL_TEST_COUNT property
     */
    fun getTotalTestCount(): Int {
        return statMap[PropertyType.TOTAL_TEST_COUNT]!!
    }

    /**
     * @return value of SUCCESS_RATE property
     */
    fun getSuccessRate(): Int {
        return statMap[PropertyType.SUCCESS_RATE]!!
    }
}