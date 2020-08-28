import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

plugins {
	java
	application
	kotlin("jvm") version "1.4.0"
}

group = "com.mivik"
version = "0.1.0"

object Config {
	const val llvmVersion = "10.0.0"
	const val javacppVersion = "1.5.3"
}

application {
	mainClassName = "com.mivik.kamet.CLIMainKt"
}

repositories {
	mavenCentral()
	maven("https://jitpack.io")
	maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
	implementation(kotlin("stdlib-jdk8"))
	implementation("com.github.KiotLand.kiot-lexer:kiot-lexer:1.0.6.2")
	implementation("org.bytedeco:llvm-platform:${Config.llvmVersion}-${Config.javacppVersion}")
	implementation("com.xenomachina:kotlin-argparser:2.0.7")
	testImplementation(kotlin("test-junit"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
	kotlinOptions {
		jvmTarget = "1.8"
		freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
	}
}

abstract class PlatformDistTask : DefaultTask() {
	@get:Incremental
	@get:PathSensitive(PathSensitivity.NAME_ONLY)
	@get:InputDirectory
	val inputDir: File
		get() = File("${project.buildDir}/install/${project.name}")

	@get:OutputDirectory
	val outputDir: File = File("${project.buildDir}/distributions/${project.name}")

	@TaskAction
	fun execute(inputChanges: InputChanges) {
		outputDir.mkdirs()
		val distDir = File(project.buildDir, "install/${project.name}")
		val binDir = File(distDir, "bin")
		val libDir = File(distDir, "lib")
		val javacppPrefix = "javacpp-${Config.javacppVersion}-"
		val llvmPrefix = "llvm-${Config.llvmVersion}-${Config.javacppVersion}-"
		fun platformOf(name: String) =
			when {
				name.startsWith(javacppPrefix) -> name.substring(javacppPrefix.length, name.lastIndexOf('.'))
				name.startsWith(llvmPrefix) -> name.substring(llvmPrefix.length, name.lastIndexOf('.'))
				else -> null
			}


		fun splitLine(content: String, pattern: String, fileType: String): Pair<String, String> {
			val beginIndex =
				(content.indexOf(pattern).takeIf { it != -1 } ?: error("Corrupted $fileType file")) + pattern.length
			val endIndex = content.indexOf('\n', beginIndex)
			return Pair(
				content.substring(0, beginIndex),
				content.substring(endIndex)
			)
		}
		val (shellBeginPart, shellEndPart) =
			splitLine(File(binDir, project.name).readText(), "\nCLASSPATH=", "shell")
		val (batBeginPart, batEndPart) =
			splitLine(File(binDir, "${project.name}.bat").readText(), "set CLASSPATH=", "bat")

		val libFiles = (libDir.listFiles() ?: error("Could not found javacpp libs in distribution"))
		val platforms = libFiles.mapNotNull { platformOf(it.name) }.toSet().toList()
		for (platform in platforms) {
			println("Distributing for $platform")
			val outputFile = File(outputDir, "${project.name}-${project.version}-$platform.zip")
			ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
				zip.putNextEntry(ZipEntry("lib/"))
				val shellClassPath = StringBuilder()
				val batClassPath = StringBuilder()
				for (lib in libFiles)
					if (platformOf(lib.name).let {
							it == null || it == platform
						}) {
						shellClassPath.append("\$APP_HOME/lib/${lib.name}:")
						batClassPath.append("%APP_HOME%\\lib\\${lib.name};")
						zip.putNextEntry(ZipEntry("lib/${lib.name}"))
						lib.inputStream().copyTo(zip)
						zip.closeEntry()
					}
				if (shellClassPath.isNotEmpty()) shellClassPath.deleteCharAt(shellClassPath.lastIndex)
				if (batClassPath.isNotEmpty()) batClassPath.deleteCharAt(batClassPath.lastIndex)
				zip.putNextEntry(ZipEntry("bin/"))
				zip.putNextEntry(ZipEntry("bin/${project.name}"))
				zip.write(shellBeginPart.toByteArray())
				zip.write(shellClassPath.toString().toByteArray())
				zip.write(shellEndPart.toByteArray())
				zip.closeEntry()
				zip.putNextEntry(ZipEntry("bin/${project.name}.bat"))
				zip.write(batBeginPart.toByteArray())
				zip.write(batClassPath.toString().toByteArray())
				zip.write(batEndPart.toByteArray())
				zip.closeEntry()
			}
		}
	}
}

tasks.register<PlatformDistTask>("platformDist") {
	group = "distribution"
	dependsOn += tasks["installDist"]
}
