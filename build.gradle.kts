import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.nexus)
    id("maven-publish")
    id("signing")
    application
}

val projectGroup = "com.caesarealabs"
val projectId = "searchit"
val projectVersion = "0.6.2"
val githubUrl = "https://github.com/Caesarea-Labs/Searchit"
val projectLicense = "The MIT License"

group = projectGroup
version = projectVersion

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.strikt)
    implementation(libs.result)
    implementation(libs.serialization)
    implementation(libs.fuzzywuzzy)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
    }
}

fun getSecretsDir() = System.getenv("CAESAREA_SECRETS")
    ?: error("Missing CAESAREA_SECRETS environment variables. Make sure to set the environment variable to the directory containing secrets.")

fun getSecretsFile(path: String): Path {
    val file = Paths.get(getSecretsDir(), path)
    if (!file.exists()) error("Missing secrets file $file. Make sure to create the file with the secrets.")
    return file
}

fun getSecretProperty(path: String, key: String): String {
    val file = getSecretsFile("${path}.properties")
    val properties = Properties()
    file.inputStream().use { properties.load(it) }
    val value = properties[key] ?: error("Missing secret property $key in file $file. Make sure to set a value to the key.")
    return value as String
}

fun getSecretFileContents(path: String): String {
    return String(getSecretsFile(path).readBytes())
}

afterEvaluate {
    tasks.create("uploadLibrary") {
        group = "upload"
        dependsOn(tasks["publishToSonatype"], tasks["closeAndReleaseSonatypeStagingRepository"])
    }
}

afterEvaluate {
    publishing {
        publications {
            register("release", MavenPublication::class) {
                // The coordinates of the library, being set from variables that
                // we'll set up later
                groupId = projectGroup
                artifactId = projectId
                version = projectVersion

                from(components["java"])

                // Mostly self-explanatory metadata
                pom {
                    name = projectId
                    description = "Search Utility"
                    url = githubUrl
                    licenses {
                        license {
                            name = projectLicense
                        }
                    }
                    developers {
                        developer {
                            id = "natan"
                            name = "Natan Lifshitz"
                            email = "natan.lifshitz@caesarealabs.com"
                        }
                    }

                    scm {
                        url = githubUrl
                    }
                }
            }
        }
    }
}
// Secrets are set in github actions
nexusPublishing {
    this@nexusPublishing.repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            stagingProfileId = System.getenv("SONATYPE_STAGING_PROFILE_ID")
            username = System.getenv("OSSRH_USERNAME")
            password = System.getenv("OSSRH_PASSWORD")
        }
    }
}
signing {
    useInMemoryPgpKeys(
        System.getenv("GPG_KEY_ID"),
        System.getenv("GPG_SECRET_KEY"),
        System.getenv("GPG_KEY_PASSWORD")
    )
    sign(publishing.publications)
}

