plugins {
	java
	application
	kotlin("jvm") version "1.4.0-rc"
}

group = "com.mivik"
version = "1.0-SNAPSHOT"

application {
	mainClassName = "com.mivik.kamet.CLIMainKt"
}

repositories {
	mavenCentral()
	maven("https://jitpack.io")
}

dependencies {
	implementation(kotlin("stdlib-jdk8"))
	implementation("com.github.KiotLand.kiot-lexer:kiot-lexer:1.0.6.1")
	implementation("org.bytedeco:llvm-platform:10.0.0-1.5.3")
	implementation("com.xenomachina:kotlin-argparser:2.0.7")
	testImplementation(kotlin("test-junit"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
	}
}