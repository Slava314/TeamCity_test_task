import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String? = null,
    val parentProjectId: String? = null,
    val href: String? = null,
    val webUrl: String? = null
)


@Serializable
data class NewProjectDescription(
    val name: String,
    val id: String
)

@Serializable
data class Property(
    val name: String,
    val value: String
)

@Serializable
data class Properties(
    val property: List<Property>
)

@Serializable
data class VcsRoot(
    val id: String,
    val name: String? = null,
    val vcsName: String? = null,
    val project: Project? = null,
    val properties: Properties? = null
)

@Serializable
data class VcsRootEntry(
    val id: String,
    @SerialName("vcs-root")
    val vcsRoot: VcsRoot
)

@Serializable
data class VcsRootEntries(
    @SerialName("vcs-root-entry")
    val vcsRootEntry: List<VcsRootEntry>
)

@Serializable
data class Steps(
    val step: List<Step>
)

@Serializable
data class Step(
    val name: String,
    val type: String,
    val properties: Properties
)

@Serializable
data class BuildType(
    val id: String,
    val name: String? = null,
    val project: Project? = null,
    @SerialName("vcs-root-entries")
    val vcsRootEntries: VcsRootEntries? = null,
    val steps: Steps? = null
)

@Serializable
data class Comment(
    val text: String
)

@Serializable
data class Revisions(
    val revision: List<Revision>
)

@Serializable
data class Revision(
    val version: String,
    val vcsBranchName: String,
    @SerialName("vcs-root-instance")
    val vcsRootInstance: VcsRootInstance
)

@Serializable
data class VcsRootInstance(
    val id: String,
    @SerialName("vcs-root-id")
    val vcsRootId: String,
    val name: String
)

@Serializable
data class Build(
    val buildType: BuildType,
    val comment: Comment,
    val revisions: Revisions
)

@Serializable
data class BuildId(
    val id: String,
    val state: String,
    val buildTypeId: String,
    var hash: String? = null
)

@Serializable
data class BuildStat(
    val property: List<Property>
)

@Serializable
data class NewBuildTypeDescription(
    val sourceBuildTypeLocator: String,
    val name: String,
    val id: String,
    val copyAllAssociatedSettings: Boolean
)
