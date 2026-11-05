plugins {
	`java-library`
}

val appName = providers.gradleProperty("appName").get()
val versionName = providers.gradleProperty("versionName").get()
val versionCode = providers.gradleProperty("versionCode").get().toInt()
val githubRepo = providers.gradleProperty("githubRepo").get()
// The keys the Linux app accepts updates from (Ed25519, hex, one per line). BUILD.sh writes the
// key in ~/.keys/linux here; empty means the Linux app checks updates by their SHA-256 alone.
val linuxUpdateKeyFile = rootProject.file("linux/update-key.pub")
val linuxUpdateKeys = (if (linuxUpdateKeyFile.exists()) linuxUpdateKeyFile.readLines() else emptyList())
	.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
require(linuxUpdateKeys.all { Regex("[0-9a-f]{64}").matches(it) }) {
	"linux/update-key.pub may only hold Ed25519 public keys, one per line, as 64 lowercase hex characters"
}

val generateAppInfo = tasks.register("generateAppInfo") {
	val outputDir = layout.buildDirectory.dir("generated/sources/appinfo")
	inputs.property("appName", appName)
	inputs.property("versionName", versionName)
	inputs.property("versionCode", versionCode)
	inputs.property("githubRepo", githubRepo)
	inputs.property("linuxUpdateKeys", linuxUpdateKeys.joinToString(","))
	outputs.dir(outputDir)
	doLast {
		val file = outputDir.get().file("app/niixfliix/app/AppInfo.java").asFile
		file.parentFile.mkdirs()
		file.writeText(
			"""
			|package app.niixfliix.app;
			|
			|public final class AppInfo {
			|	public static final String APP_NAME = "$appName";
			|	public static final String APP_SLUG = "${appName.lowercase()}";
			|	public static final String VERSION_NAME = "$versionName";
			|	public static final int VERSION_CODE = $versionCode;
			|	public static final String GITHUB_REPO = "$githubRepo";
			|	public static final String LINUX_UPDATE_KEYS = "${linuxUpdateKeys.joinToString(",")}";
			|
			|	private AppInfo() {
			|	}
			|}
			|""".trimMargin()
		)
	}
}

sourceSets {
	main {
		java.srcDir(generateAppInfo)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 17
	options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
	jvmArgs("-Xshare:off")
}

dependencies {
	api(libs.androidx.annotation)
	api(libs.gson)
	api(libs.okhttp)
	api(libs.libtorrent4j)

	testImplementation(libs.junit)
	testImplementation(libs.mockito.core)
}
