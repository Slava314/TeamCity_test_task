import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import java.io.File
import java.lang.Thread.sleep
import kotlin.jvm.Throws

/**
 * Processes post request to TeamCity server
 * @param client to process request
 * @param url for request
 * @param body serializable class for json body of request
 * @return HttpResponse
 */
suspend fun post(client: HttpClient, url: String, body: Any): HttpResponse {
    val res = client.post(url) {
        headers {
            append(HttpHeaders.Authorization, "Bearer " + getBearerAuthSecret())
            append(HttpHeaders.ContentType, JSON_FORMAT)
        }
        setBody(body)
    }
    checkStatus(res)
    return res
}

/**
 * Processes get request to TeamCity server
 * @param client to process request
 * @param url for request
 * @return HttpResponse
 */
suspend fun get(client: HttpClient, url: String): HttpResponse {
    val res = client.get(url) {
        headers {
            append(HttpHeaders.Authorization, "Bearer " + getBearerAuthSecret())
            append(HttpHeaders.Accept, JSON_FORMAT)
        }
    }
    checkStatus(res)
    return res
}

/**
 * Processes delete request to TeamCity server
 * @param client to process request
 * @param url for request
 * @return HttpResponse
 */
suspend fun delete(client: HttpClient, url: String): HttpResponse {
    val res = client.delete(url) {
        headers {
            append(HttpHeaders.Authorization, "Bearer " + getBearerAuthSecret())
            append(HttpHeaders.Accept, JSON_FORMAT)
        }
    }
    checkStatus(res)
    return res
}

/**
 * Check the status of HttpResponse
 * @param res HttpResponse
 */
@Throws(IllegalStateException::class)
suspend fun checkStatus(res: HttpResponse) {
    if (299 < res.status.value || res.status.value < 200) {
        println(res.status)
        val resFile = File("error.json")
        val responseBody: ByteArray = res.body()
        resFile.writeBytes(responseBody)
        println(resFile.path)
        throw IllegalStateException()
    }
}

suspend fun main() {
    val directory = ASTMINER_PATH.toFile()
    directory.deleteRecursively()
    val git = Git.cloneRepository()
        .setURI(ASTMINER_URL)
        .setDirectory(directory)
        .call()

    val suf = List(4) {
        (('a'..'z') + ('A'..'Z') + ('0'..'9')).random()
    }.joinToString("")
    val projectName = "astminer_$suf"
    println("Project name: $projectName")
    val projectId = "id_$projectName"
    val vcsRootName = "${projectName}_local_git"
    val vcsRootId = "id_$vcsRootName"
    val buildTypeName = "${projectName}_main_build"
    val buildTypeId = "id_$buildTypeName"
    val vcsRootEntryId = "id_${projectName}_vcs_root_entry"

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    // make new project
    post(
        client,
        "$TEAMCITY_PATH/app/rest/projects",
        NewProjectDescription(projectName, projectId)
    )
    // add vcs-root from cloned git project
    post(
        client,
        "$TEAMCITY_PATH/app/rest/vcs-roots",
        VcsRoot(
            vcsRootId, vcsRootName, "jetbrains.git", Project(projectId),
            Properties(
                listOf(
                    Property("authMethod", "ANONYMOUS"),
                    Property("branch", "refs/heads/master"),
                    Property("url", ASTMINER_PATH.toString())
                )
            )
        )
    )

    // add default buildType with one gradle step
    post(
        client,
        "$TEAMCITY_PATH/app/rest/buildTypes",
        BuildType(
            buildTypeId,
            buildTypeName,
            Project(projectId),
            VcsRootEntries(
                listOf(
                    VcsRootEntry(vcsRootEntryId, VcsRoot(vcsRootId))
                )
            ),
            Steps(
                listOf(
                    Step(
                        "gradle",
                        "gradle-runner",
                        Properties(
                            listOf(
                                Property("teamcity.step.mode", "default"),
                                Property("ui.gradleRunner.gradle.tasks.names", "clean build"),
                                Property("ui.gradleRunner.gradle.wrapper.useWrapper", "true"),
                                Property("target.jdk.home", "%env.JDK_11%")
                            )
                        )
                    )
                )
            )
        )
    )

    // get vcsRootInstance
    val vcsRootInstance: VcsRootInstance = get(
        client,
        "$TEAMCITY_PATH/app/rest/vcs-root-instances/vcsRoot:(id:($vcsRootId))"
    ).body()

    val allCommits = git.log().call()
    val builds = mutableListOf<BuildStatistics>()
    val lastCommit = mutableListOf<BuildId>()


    var count = 0
    for (commit in allCommits) {
        val hash: String = commit.name

        // copy default build to start with commit
        post(
            client,
            "$TEAMCITY_PATH/app/rest/projects/id:$projectId/buildTypes",
            NewBuildTypeDescription(
                "id:$buildTypeId",
                "${buildTypeName}_$hash",
                "${buildTypeId}_$hash",
                true
            )
        )

        // push new build in queue
        val build: BuildId = post(
            client,
            "$TEAMCITY_PATH/app/rest/buildQueue",
            Build(
                BuildType("${buildTypeId}_$hash"),
                Comment(hash),
                Revisions(
                    listOf(
                        Revision(
                            hash,
                            "refs/heads/master",
                            vcsRootInstance
                        )
                    )
                )
            )
        ).body()

        build.hash = hash
        lastCommit.add(build)
        count++

        if (count % BATCH_SIZE == 0 || count == NUMBER_OF_COMMITS) {
            builds.addAll(lastCommit.map {
                while (true) {
                    // get build state
                    val response: BuildId = get(
                        client,
                        "$TEAMCITY_PATH/app/rest/builds/id:${it.id}"
                    ).body()
                    if (response.state == "finished") {
                        sleep(5)
                        break
                    }
                    withContext(Dispatchers.IO) {
                        sleep(5000)
                    }
                }
                var buildStat: BuildStatistics
                while (true) {
                    // get finished build statistics
                    val response: BuildStat = get(
                        client,
                        "$TEAMCITY_PATH/app/rest/builds/id:${it.id}/statistics"
                    ).body()

                    buildStat = BuildStatistics(it.hash.toString(), it.id, response.property)
                    if (buildStat.getArtifactSize() != -1) {
                        break
                    }
                    sleep(5)
                }
                // delete build
                delete(
                    client,
                    "$TEAMCITY_PATH/app/rest/buildTypes/id:${it.buildTypeId}"
                )

                buildStat
            })
            lastCommit.clear()
            println("$count builds finished")
            if (count == NUMBER_OF_COMMITS) {
                break
            }
        }
    }
    client.close()

    val buildFile = File("./builds.txt")
    buildFile.writeText(builds.toString())

    val statisticsFile = File("./statistics.txt")

    val sb = StringBuilder()
    val successBuilds = builds.filter { it.getSuccessRate() != -1 }
    val success = successBuilds.size
    val successTest = successBuilds.count { it.getSuccessRate() == 1 }

    sb.appendLine("Successful builds: $success/$NUMBER_OF_COMMITS, ${success / NUMBER_OF_COMMITS.toDouble() * 100}%")
    if (success != NUMBER_OF_COMMITS) {
        sb.appendLine("Failed builds:")
        builds.filter { it.getSuccessRate() != 1 }
            .forEach { sb.appendLine("id=${it.buildId}, commit hash=${it.hash}") }
    }
    sb.appendLine("Builds with no failed tests: $successTest/$NUMBER_OF_COMMITS, ${successTest / NUMBER_OF_COMMITS.toDouble() * 100}%")
    sb.appendLine()
    val buildWithMaxTime = successBuilds.maxBy { it.getBuildDuration() }
    sb.appendLine(
        "Build with max build time: " +
                "id=${buildWithMaxTime.buildId}, " +
                "hash=${buildWithMaxTime.hash}, " +
                "time=${buildWithMaxTime.getBuildDuration() / 1000} s"
    )

    sb.appendLine("Average build time: ${successBuilds.map { it.getBuildDuration() }.average() / 1000} s")
    sb.appendLine()
    val buildWithMaxArtifactSize = successBuilds.maxBy { it.getArtifactSize() }
    sb.appendLine(
        "Build with max artifact size: " +
                "id=${buildWithMaxArtifactSize.buildId}, " +
                "hash=${buildWithMaxArtifactSize.hash}, " +
                "size=${buildWithMaxArtifactSize.getArtifactSize()}"
    )

    sb.appendLine(
        "Average artifact size: ${
            successBuilds.filter { it.getArtifactSize() != -1 }.map { it.getArtifactSize() }.average()
        }"
    )
    sb.appendLine()
    sb.appendLine("Tests statistics:")

    successBuilds.filter { it.getSuccessRate() != -1 }.forEach {
        sb.appendLine("id=${it.buildId}, hash=${it.hash}")
        val total = it.getTotalTestCount()
        val passed = it.getPassedTestCount()
        val ignored = it.getIgnoredTestCount()
        val failed = total - passed - ignored
        sb.appendLine("    total: $total")
        sb.appendLine("    passed tests: $passed, ${passed / total.toDouble() * 100}%")
        sb.appendLine("    ignored tests: $ignored, ${ignored / total.toDouble() * 100}%")
        sb.appendLine("    failed tests: $failed, ${failed / total.toDouble() * 100}%")
    }
    sb.appendLine()

    statisticsFile.writeText(sb.toString())
}