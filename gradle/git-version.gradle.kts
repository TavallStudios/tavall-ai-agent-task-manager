fun git(vararg arguments: String): String =
    providers.exec {
        commandLine("git", *arguments)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()

val tagPrefix = extra["versionTagPrefix"] as String
val fallbackVersion = extra["fallbackVersion"] as String
val pattern = Regex(Regex.escape(tagPrefix) + "-v([0-9]+)\\.([0-9]+)\\.([0-9]+)")
val exact = git("tag", "--points-at", "HEAD").lineSequence()
    .firstNotNullOfOrNull { pattern.matchEntire(it) }
val latest = git(
    "for-each-ref",
    "--merged=HEAD",
    "--sort=-creatordate",
    "--format=%(refname:short)",
    "refs/tags",
).lineSequence().firstNotNullOfOrNull { pattern.matchEntire(it) }
val numeric = when {
    exact != null -> exact.groupValues.drop(1).joinToString(".")
    latest != null -> latest.groupValues.drop(1).map(String::toInt)
        .let { "${it[0]}.${it[1]}.${it[2] + 1}" }
    else -> fallbackVersion
}
val branch = providers.environmentVariable("GITHUB_HEAD_REF").orNull?.takeIf(String::isNotBlank)
    ?: providers.environmentVariable("GITHUB_REF_NAME").orNull?.takeIf(String::isNotBlank)
    ?: git("branch", "--show-current").ifBlank { "detached" }
val qualifier = branch.replace('/', '_').replace(Regex("[^A-Za-z0-9_.-]"), "_")
extra["gitVersion"] = if (exact != null) numeric else "$numeric-$qualifier-SNAPSHOT"
