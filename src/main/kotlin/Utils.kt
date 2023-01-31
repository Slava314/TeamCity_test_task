import java.io.File
import kotlin.io.path.Path

internal val ASTMINER_PATH = Path("./reps/astminer")
internal const val ASTMINER_URL = "https://github.com/JetBrains-Research/astminer.git"

internal val TEAMCITY_PATH = "http://localhost:8111"

internal const val SECRETS_PATH = "./src/main/resources/secrets/"

internal const val JSON_FORMAT = "application/json"

fun getBearerAuthSecret(): String {
    return File(SECRETS_PATH + "bearer_auth.txt").readText()
}