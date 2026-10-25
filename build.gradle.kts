plugins {
    id("java")
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "org.example.Main"
}

tasks.named<JavaExec>("run") {
    val envFile = rootProject.file(".env")
    if (envFile.isFile) {
        envFile.readLines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotEmpty() && !line.startsWith("#") && line.contains('=')) {
                val (key, rawValue) = line.split('=', limit = 2)
                val value = rawValue.trim().removeSurrounding("\"").removeSurrounding("'")
                if (key.isNotBlank() && System.getenv(key.trim()).isNullOrBlank()) {
                    environment(key.trim(), value)
                }
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    from(projectDir) {
        include("index.html", "add-words.html", "styles.css", "script.js", "prompt-history.js", "assets/**")
        into("public")
    }
}

tasks.test {
    useJUnitPlatform()
}
