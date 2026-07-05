plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

repositories {
    maven("https://repo.runelite.net")
    mavenCentral()
}

dependencies {
    implementation("net.runelite:cache:1.12.23")
    implementation("com.google.code.gson:gson:2.8.5")
    implementation("org.slf4j:slf4j-simple:1.7.25")
}

application {
    mainClass = "net.runelite.sprites.ItemSpriteDumper"
}

tasks.named<JavaExec>("run") {
    jvmArgs("-Xmx2048m")
}
