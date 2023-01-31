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


suspend fun post(client: HttpClient, url: String, body: Any): HttpResponse {
    return client.post(url) {
        headers {
            append(HttpHeaders.Authorization, "Bearer " + getBearerAuthSecret())
            append(HttpHeaders.ContentType, JSON_FORMAT)
        }
        setBody(body)
    }
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

    val file = ASTMINER_PATH.toFile()
    file.deleteRecursively()
    val git = Git.cloneRepository()
        .setURI(ASTMINER_URL)
        .setDirectory(file)
        .call()

    val suf = "25"
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
    var res: HttpResponse = post(
        client,
        "$TEAMCITY_PATH/app/rest/projects",
        NewProjectDescription(projectName, projectId)
    )
    checkStatus(res)

    res = post(
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
    checkStatus(res)

    res = post(
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
    checkStatus(res)

    val vcsRootInstance: VcsRootInstance =
        client.get("$TEAMCITY_PATH/app/rest/vcs-root-instances/vcsRoot:(id:($vcsRootId))") {
            headers {
                append(HttpHeaders.Authorization, "Bearer " + getBearerAuthSecret())
                append(HttpHeaders.Accept, JSON_FORMAT)
            }
        }.body()

    val lst = git.log().call().take(2).map { commit ->
        val hash = commit.name
        println(commit.name)
        res = post(
            client,
            "$TEAMCITY_PATH/app/rest/projects/id:$projectId/buildTypes",
            NewBuildTypeDescription(
                "id:$buildTypeId",
                "$buildTypeId$hash",
                "$buildTypeName$hash",
                true
            )
        )

        val build: BuildId = post(
            client,
            "$TEAMCITY_PATH/app/rest/buildQueue",
            Build(
                BuildType("$buildTypeName$hash"),
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


        build
    }

    val maxId = lst.maxBy { it.id }

    var state = ""

    while (state != "finished") {
        val response: BuildId = client.get("$TEAMCITY_PATH/app/rest/builds/id:${maxId.id}") {
            headers {
                append(HttpHeaders.Authorization, "Bearer " + getBearerAuthSecret())
                append(HttpHeaders.Accept, JSON_FORMAT)
            }
        }.body()
        withContext(Dispatchers.IO) {
            sleep(20000)
        }
        state = response.state
    }

    val ans = lst.map {
        val response: BuildStat = client.get("$TEAMCITY_PATH/app/rest/builds/id:${it.id}/statistics") {
            headers {
                append(HttpHeaders.Authorization, "Bearer " + getBearerAuthSecret())
                append(HttpHeaders.Accept, JSON_FORMAT)
            }
        }.body()

        client.delete("$TEAMCITY_PATH/app/rest/buildTypes/id:${it.buildTypeId}") {
            headers {
                append(HttpHeaders.Authorization, "Bearer " + getBearerAuthSecret())
                append(HttpHeaders.ContentType, JSON_FORMAT)
            }
        }

        response
    }
    print(ans)


    client.close()
}