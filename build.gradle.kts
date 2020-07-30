plugins {
	java
	kotlin("jvm") version "1.3.72"
}

group = "com.mivik"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()
	maven("https://jitpack.io")
}

dependencies {
	implementation(kotlin("stdlib-jdk8"))
	implementation("com.github.KiotLand.kiot-lexer:kiot-lexer:1.0.5.9")
	testImplementation(kotlin("test-junit"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
	kotlinOptions.jvmTarget = "1.8"
}