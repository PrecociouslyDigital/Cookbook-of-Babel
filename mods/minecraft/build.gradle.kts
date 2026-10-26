plugins {
    java
    alias(libs.plugins.moddev.legacyforge)
}

// Every dependency version is exact (gradle/libs.versions.toml) and every
// artifact checksummed (gradle/verification-metadata.xml); refresh the checksums
// with scripts/pin-dependencies.sh after changing a dependency. The project's own
// configurations aren't locked: locking them stops ModDevGradle from remapping
// mod dependencies to official names, and they then crash at runtime. The plugins
// are locked, leniently, because Minecraft's libraries differ per OS: a module
// locked on macOS may not resolve on Linux, but whatever does resolve must be
// the locked version.
buildscript {
    dependencyLocking {
        lockAllConfigurations()
        lockMode = LockMode.LENIENT
    }
}

val modId = "cookbook_of_babel"
val gameVersion = libs.versions.minecraft.get()

version = "0.1.0"
group = "cookbookofbabel"
base.archivesName = "$modId-$gameVersion"

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
    options.compilerArgs.add("-Xlint:deprecation")
}

val repoRoot: File = rootDir.resolve("../..")
val datasetExport = layout.buildDirectory.file("babel/world.json")

// The only way the mod reads the dataset. Needs `babel` on the PATH (devenv
// provides it). Plain builds don't run it: what it feeds is committed.
val exportDataset by tasks.registering(Exec::class) {
    description = "Exports the dataset with `babel export`."
    inputs.dir(repoRoot.resolve("data"))
    inputs.dir(repoRoot.resolve("babel/src"))
    inputs.dir(repoRoot.resolve("babel/app"))
    outputs.file(datasetExport)
    commandLine("babel", "export", repoRoot.resolve("data").path, "--out", datasetExport.get().asFile.path)
}

val generateDatasetSources by tasks.registering(cookbookofbabel.gradle.GenerateDatasetSources::class) {
    description = "Generates the dataset's forms as a Java enum and its named dishes as a resource."
    export = layout.file(exportDataset.map { datasetExport.get().asFile })
    output = layout.projectDirectory.dir("src/generated/java")
    resources = layout.projectDirectory.dir("src/generated/dataset")
}

tasks.compileJava { mustRunAfter(generateDatasetSources) }
tasks.processResources { mustRunAfter(generateDatasetSources) }

sourceSets.main {
    java.srcDir("src/generated/java")
    resources {
        srcDir("src/generated/resources")
        srcDir("src/generated/dataset")
        exclude(".cache/")
    }
}

legacyForge {
    enable {
        forgeVersion = "$gameVersion-${libs.versions.forge.get()}"
    }

    parchment {
        minecraftVersion = libs.versions.minecraft
        mappingsVersion = libs.versions.parchment
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        configureEach {
            systemProperty("forge.logging.console.level", "info")
        }
        create("client") {
            client()
        }
        create("server") {
            server()
            gameDirectory = file("run/server")
        }
        create("data") {
            data()
            systemProperty("cookbook_of_babel.export", datasetExport.get().asFile.absolutePath)
            systemProperty("cookbook_of_babel.mappings", file("mappings").absolutePath)
            systemProperty("cookbook_of_babel.coverage", file("coverage.md").absolutePath)
            systemProperty("cookbook_of_babel.candidates", layout.buildDirectory.file("babel/candidates.json").get().asFile.absolutePath)
            programArguments.addAll(
                "--mod", modId,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath,
            )
        }
        create("gameTestServer") {
            type = "gameTestServer"
            systemProperty("forge.enabledGameTestNamespaces", modId)
            gameDirectory = file("run/gametest")
        }
    }
}

// Regenerating always starts from a fresh export.
tasks.named("runData") { dependsOn(generateDatasetSources) }

repositories {
    exclusiveContent {
        forRepository { maven("https://maven.createmod.net") }
        filter {
            includeGroup("com.simibubi.create")
            includeGroup("dev.engine-room.flywheel")
            includeGroup("net.createmod.ponder")
        }
    }
    exclusiveContent {
        forRepository { maven("https://maven.ithundxr.dev/mirror") }
        filter { includeGroup("com.tterrag.registrate") }
    }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.blamejared.com") }
        filter { includeGroup("mezz.jei") }
    }
    mavenCentral()
}

// Create, Farmer's Delight and JEI are optional at runtime (see mods.toml), but
// always present in development and datagen runs.
dependencies {
    modImplementation(variantOf(libs.create) { classifier("slim") }) { isTransitive = false }
    modImplementation(libs.ponder)
    modImplementation(libs.registrate)
    modCompileOnly(libs.flywheel.api)
    modRuntimeOnly(libs.flywheel)
    modImplementation(libs.farmersdelight)
    modCompileOnly(libs.jei.common.api)
    modCompileOnly(libs.jei.forge.api)
    modRuntimeOnly(libs.jei.forge)

    testImplementation(libs.jqwik)
    testImplementation(libs.assertj)
}

// The properties test pure planning code, which only needs Minecraft's
// libraries (DataFixerUpper's codecs) on the classpath, not a running game.
sourceSets.test {
    runtimeClasspath += sourceSets.main.get().compileClasspath
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val properties = mapOf("version" to project.version, "minecraft" to gameVersion)
    inputs.properties(properties)
    filesMatching("META-INF/mods.toml") { expand(properties) }
}

// The generated data on its own, for packs that want to replace the mod's
// bundled recipes and tags with their own edits of them.
val datapackZip by tasks.registering(Zip::class) {
    description = "Packs the generated data as a standalone datapack."
    archiveFileName = "$modId-$gameVersion-datapack.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from("src/generated/resources") {
        include("data/**", "ATTRIBUTION.md", "LICENSE-data")
        exclude("data/$modId/structures/**")
    }
    from("src/main/resources/pack.mcmeta")
}
