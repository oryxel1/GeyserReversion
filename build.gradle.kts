import java.io.ByteArrayOutputStream

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
    id("com.modrinth.minotaur") version "2.+"
}

group = "oxy.reversion"
version = "2.0"

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    compileOnly("org.geysermc.geyser:core:2.9.5-SNAPSHOT") {
        exclude(group = "com.google.code.gson", module = "gson")
    }

    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    implementation("net.lenni0451.classtransform:core:1.14.1")
    implementation("net.lenni0451:Reflect:1.5.0")

    implementation("org.yaml:snakeyaml:2.2")

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")

    implementation(files("libs/ouranos-1.0-all.jar"))
}

tasks.shadowJar {
    archiveFileName = "geyserreversion.jar"

    dependencies {
        include(dependency(files("libs/ouranos-1.0-all.jar")))
        include(dependency("com.fasterxml.jackson.core:jackson-databind:2.17.0"))
        include(dependency("com.fasterxml.jackson.core:jackson-annotations:2.17.0"))
        include(dependency("com.fasterxml.jackson.core:jackson-core:2.17.0"))
        include(dependency("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0"))
        include(dependency("org.yaml:snakeyaml:2.2"))

        include(dependency("net.lenni0451.classtransform:core:1.14.1"))
        include(dependency("net.lenni0451:Reflect:1.5.0"))

        include(dependency("org.ow2.asm:asm:9.8"))
        include(dependency("org.ow2.asm:asm-analysis:9.8"))
        include(dependency("org.ow2.asm:asm-commons:9.8"))
        include(dependency("org.ow2.asm:asm-tree:9.8"))
    }

    relocate("com.fasterxml.jackson", "oxy.reversion.shaded.jackson")
    relocate("org.yaml.snakeyaml", "oxy.reversion.shaded.yaml")
    relocate("org.objectweb.asm", "oxy.reversion.shaded.asm")
    relocate("net.lenni0451.reflect", "oxy.reversion.shaded.reflect")
    relocate("net.lenni0451.classtransform", "oxy.reversion.shaded.classtransform")
}

modrinth {
    token = System.getenv("MODRINTH_TOKEN")
    versionName.set("Build " + System.getenv("BUILD_NUMBER"))
    versionNumber.set(System.getenv("BUILD_NUMBER"))
    projectId = "geyserreversion"
    versionType = "release"
    uploadFile.set(tasks.getByPath("shadowJar"))

    var releaseNotes = rootProject.file("release_notes.md")
    changelog.set(releaseNotes.exists().let {
        if (it) releaseNotes.readText() else ""
    })

    gameVersions = listOf("1.21.11");
    loaders = listOf("geyser")
}