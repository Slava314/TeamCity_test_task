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
import kotlin.math.ceil


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

suspend fun checkStatus(res: HttpResponse) {
    if (res.status.value != 200) {
        println(res.status)
        val resFile = File("tmp.json")
        val responseBody: ByteArray = res.body()
        resFile.writeBytes(responseBody)
        println(resFile.path)
        throw IllegalStateException()
    }
}

suspend fun main(args: Array<String>) {

    val directory = ASTMINER_PATH.toFile()
    directory.deleteRecursively()
    val git = Git.cloneRepository()
        .setURI(ASTMINER_URL)
        .setDirectory(directory)
        .call()

    val suf = "44"
    val projectName = "ast$suf"
    val projectId = "id_$projectName"
    val vcsRootName = "local_git_$suf"
    val vcsRootId = "id_$vcsRootName"
    val buildTypeName = "main_build$suf"
    val buildTypeId = "id_$buildTypeName"
    val vcsRootEntryId = "id_vcsRootEntry$suf"

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    post(
        client,
        "$TEAMCITY_PATH/app/rest/projects",
        NewProjectDescription(projectName, projectId)
    )

    post(
        client,
        "$TEAMCITY_PATH/app/rest/vcs-roots",
        VcsRoot(
            vcsRootId, vcsRootName, "jetbrains.git", Project(projectId),
            Properties(
                listOf(
                    Property("authMethod", "ANONYMOUS"),
                    Property("branch", "refs/heads/master"),
                    Property("url", "/Users/aku314/IdeaProjects/astminer")
                )
            )
        )
    )

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

    val vcsRootInstance: VcsRootInstance = get(
        client,
        "$TEAMCITY_PATH/app/rest/vcs-root-instances/vcsRoot:(id:($vcsRootId))"
    ).body()

    val allCommits = git.log().call()
    val builds = mutableListOf<BuildStatistics>()
    val lastCommit = mutableListOf<BuildId>()

    val batchSize = 3
    var count = 0
    for (commit in allCommits) {
        val hash: String = commit.name

        post(
            client,
            "$TEAMCITY_PATH/app/rest/projects/id:$projectId/buildTypes",
            NewBuildTypeDescription(
                "id:$buildTypeId",
                "${buildTypeId}_$hash",
                "${buildTypeName}_$hash",
                true
            )
        )

        val build: BuildId = post(
            client,
            "$TEAMCITY_PATH/app/rest/buildQueue",
            Build(
                BuildType("${buildTypeName}_$hash"),
                Comment(hash),
                Revisions(
                    listOf(
                        Revision(
                            hash,
                            "refs/heads/master",
                            VcsRootInstance(vcsRootInstance.id, vcsRootInstance.vcsRootId, vcsRootInstance.name)
                        )
                    )
                )
            )
        ).body()

        build.hash = hash
        lastCommit.add(build)
        count++

        if (count % batchSize == 0 || count == NUMBER_OF_COMMITS) {
            builds.addAll(lastCommit.map {
                while (true) {
                    val response: BuildId = get(
                        client,
                        "$TEAMCITY_PATH/app/rest/builds/id:${it.id}"
                    ).body()
                    if (response.state == "finished") {
                        break
                    }
                    withContext(Dispatchers.IO) {
                        sleep(5000)
                    }
                }
                val response: BuildStat = get(
                    client,
                    "$TEAMCITY_PATH/app/rest/builds/id:${it.id}/statistics"
                ).body()

                delete(
                    client,
                    "$TEAMCITY_PATH/app/rest/buildTypes/id:${it.buildTypeId}"
                )

                BuildStatistics(it.hash.toString(), it.id, response.property)
            })
            lastCommit.clear()
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

    val success = builds.count { it.statMap[PropertyType.SUCCESS_RATE] == 1 }

    sb.append("Number of success builds: $success\n")
    if (success != NUMBER_OF_COMMITS) {
        sb.append("Failed builds:\n")
        builds.filter { it.statMap[PropertyType.SUCCESS_RATE] != 1 }
            .forEach { sb.append("id=${it.buildId}, commit hash=${it.hash}\n") }
    }


    statisticsFile.writeText(sb.toString())

}