plugins {
	java
}

val appName = providers.gradleProperty("appName").get()
val slug = appName.lowercase()

val torrentNative: Configuration = configurations.create("torrentNative")

sourceSets {
	create("tools")
}

dependencies {
	implementation(project(":core"))

	torrentNative(libs.libtorrent4j.linux)
	runtimeOnly(libs.twelvemonkeys.imageio.webp)
	runtimeOnly(libs.twelvemonkeys.imageio.jpeg)

	"toolsImplementation"(libs.gson)

	testImplementation(libs.junit)
}

val jdk = JavaLanguageVersion.of(25)
java {
	toolchain {
		languageVersion = jdk
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-restricted", "-Xlint:-serial", "-Xlint:-processing"))
}

tasks.withType<JavaExec>().configureEach {
	javaLauncher = javaToolchains.launcherFor { languageVersion = jdk }
}

val exportedResources = layout.buildDirectory.dir("generated/resources/export")
val exportResources = tasks.register<JavaExec>("exportResources") {
	description = "Exports the Android strings and icons for the Linux app"
	val androidRes = rootProject.file("app/src/main/res")
	val desktopStrings = file("src/main/strings")
	val desktopIcons = file("src/main/icons")
	inputs.dir(androidRes)
	inputs.dir(desktopStrings)
	inputs.dir(desktopIcons)
	outputs.dir(exportedResources)
	classpath = sourceSets["tools"].runtimeClasspath
	mainClass = "app.niixfliix.tools.ResourceExport"
	argumentProviders.add(CommandLineArgumentProvider {
		listOf(androidRes.path, desktopStrings.path, desktopIcons.path, exportedResources.get().asFile.path)
	})
	doFirst {
		exportedResources.get().asFile.deleteRecursively()
	}
}

sourceSets.main {
	resources.srcDir(files(exportedResources).builtBy(exportResources))
}

tasks.jar {
	archiveBaseName = slug
	manifest {
		attributes("Main-Class" to "app.niixfliix.desktop.Main")
	}
}

tasks.test {
	useJUnit()
}

val extractTorrentNative = tasks.register<Sync>("extractTorrentNative") {
	from({ torrentNative.map { zipTree(it) } }) {
		include("lib/x86_64/libtorrent4j.so")
		eachFile { path = name }
		includeEmptyDirs = false
	}
	into(layout.buildDirectory.dir("native"))
}

val javaModulesFile = layout.buildDirectory.file("java-modules/java-modules.txt")
val jdepsOutput = layout.buildDirectory.file("java-modules/jdeps.txt")
val alwaysModules = listOf("java.base", "java.desktop", "java.logging", "java.sql", "jdk.charsets",
	"jdk.localedata", "jdk.unsupported", "jdk.zipfs")
val jdeps = javaToolchains.launcherFor { languageVersion = jdk }
	.map { it.metadata.installationPath.file("bin/jdeps").asFile.path }
val javaModules = tasks.register<Exec>("javaModules") {
	description = "Lists the Java modules the Linux app needs"
	val appJar = tasks.jar.flatMap { it.archiveFile }
	val libraries: FileCollection = configurations.runtimeClasspath.get()
	inputs.files(appJar, libraries)
	outputs.file(javaModulesFile)
	val stdout = jdepsOutput
	val result = javaModulesFile
	val always = alwaysModules
	val jdepsPath = jdeps
	val release = jdk.asInt().toString()
	isIgnoreExitValue = true
	argumentProviders.add(CommandLineArgumentProvider {
		val classPath = libraries.files.joinToString(File.pathSeparator) { it.path }
		listOf("--print-module-deps", "--ignore-missing-deps", "--multi-release", release,
			"--class-path", classPath, appJar.get().asFile.path) + libraries.files.map { it.path }
	})
	doFirst {
		executable = jdepsPath.get()
		stdout.get().asFile.parentFile.mkdirs()
		standardOutput = stdout.get().asFile.outputStream()
	}
	doLast {
		standardOutput.close()
		val found = stdout.get().asFile.readLines().map { it.trim() }
			.lastOrNull { Regex("[a-z][a-z0-9.]*(,[a-z][a-z0-9.]*)*").matches(it) }
		val ok = executionResult.get().exitValue == 0 && found != null
		if (!ok) {
			logger.warn("jdeps could not list the Java modules the app uses: the Flatpak gets all of Java SE")
		}
		val modules = (if (ok) found!!.split(",") else listOf("java.se")) + always
		result.get().asFile.writeText(modules.toSortedSet().joinToString(",") + "\n")
	}
}

tasks.register<Sync>("linuxDist") {
	description = "Puts the Linux app together in build/linux"
	into(layout.buildDirectory.dir("linux/$slug"))
	into("lib/app") {
		from(tasks.jar)
		from(configurations.runtimeClasspath)
	}
	into("lib/native") {
		from(extractTorrentNative)
	}
	into("lib") {
		from(javaModules.map { it.outputs.files })
	}
	into("share") {
		from(exportedResources.map { it.file("app/niixfliix/desktop/app-icon.svg") })
	}
	into("share/fonts") {
		from(rootProject.file("app/src/main/res/font")) { include("*.ttf") }
		from(rootProject.file("linux/JetBrainsMono-OFL.txt"))
	}
	dependsOn(exportResources)
}

val crashDir = File(
	System.getenv("XDG_DATA_HOME")?.takeIf { it.startsWith("/") } ?: (System.getProperty("user.home") + "/.local/share"),
	"$slug/crash",
)
tasks.register<JavaExec>("run") {
	description = "Runs the Linux app from the build"
	dependsOn(tasks.jar, extractTorrentNative)
	classpath = files(tasks.jar) + configurations.runtimeClasspath.get()
	mainClass = "app.niixfliix.desktop.Main"
	doFirst {
		crashDir.mkdirs()
	}
	jvmArgumentProviders.add(CommandLineArgumentProvider {
		listOf(
			"--enable-native-access=ALL-UNNAMED",
			"-Djava.awt.headless=true",
			"-XX:ErrorFile=" + File(crashDir, "hs_err_pid%p.log").path,
			"-Dlibtorrent4j.jni.path=" + layout.buildDirectory.file("native/libtorrent4j.so").get().asFile.path,
		)
	})
}
