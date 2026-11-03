plugins {
	`java-library`
}

val appName = providers.gradleProperty("appName").get()
val versionName = providers.gradleProperty("versionName").get()
val versionCode = providers.gradleProperty("versionCode").get().toInt()
val githubRepo = providers.gradleProperty("githubRepo").get()

val generateAppInfo = tasks.register("generateAppInfo") {
	val outputDir = layout.buildDirectory.dir("generated/sources/appinfo")
	inputs.property("appName", appName)
	inputs.property("versionName", versionName)
	inputs.property("versionCode", versionCode)
	inputs.property("githubRepo", githubRepo)
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
