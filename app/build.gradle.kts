plugins {
	alias(libs.plugins.android.application)
}

val appName = providers.gradleProperty("appName").get()
val githubRepo = providers.gradleProperty("githubRepo").get()
// BUILD.sh exports these. Without them the release build stays unsigned.
val keystore: String? = System.getenv("NIIXFLIIX_KEYSTORE")

android {
	namespace = "app.niixfliix"
	compileSdk = 36

	defaultConfig {
		applicationId = providers.gradleProperty("appId").get()
		minSdk = 26
		targetSdk = 36
		versionCode = providers.gradleProperty("versionCode").get().toInt()
		versionName = providers.gradleProperty("versionName").get()

		resValue("string", "app_name", appName)
		buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
		buildConfigField("String", "APP_SLUG", "\"${appName.lowercase()}\"")
	}

	androidResources {
		localeFilters += listOf("en", "es", "fr", "ja", "ko", "ru", "tr", "zh", "zh-rTW")
	}

	signingConfigs {
		create("release") {
			if (!keystore.isNullOrEmpty()) {
				storeFile = file(keystore)
				storePassword = System.getenv("NIIXFLIIX_KEYSTORE_PASSWORD")
				keyAlias = System.getenv("NIIXFLIIX_KEY_ALIAS")
				keyPassword = System.getenv("NIIXFLIIX_KEYSTORE_PASSWORD")
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			isDebuggable = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			if (!keystore.isNullOrEmpty()) {
				signingConfig = signingConfigs.getByName("release")
			}
		}
		debug {
			applicationIdSuffix = ".debug"
		}
	}

	buildFeatures {
		buildConfig = true
		resValues = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	testOptions {
		unitTests.isReturnDefaultValues = true
	}

	dependenciesInfo {
		includeInApk = false
		includeInBundle = false
	}
}

dependencies {
	implementation(libs.androidx.activity)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.core)
	implementation(libs.androidx.fragment)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.swiperefreshlayout)
	implementation(libs.material)

	implementation(libs.media3.exoplayer)
	implementation(libs.media3.exoplayer.hls)
	implementation(libs.media3.exoplayer.dash)
	implementation(libs.media3.ffmpeg)
	implementation(libs.media3.datasource.okhttp)
	implementation(libs.media3.ui)
	implementation(libs.media3.session)

	implementation(libs.okhttp)
	implementation(libs.gson)
	implementation(libs.mmkv)
	implementation(libs.picasso)

	implementation(libs.libtorrent4j)
	implementation(libs.libtorrent4j.arm)
	implementation(libs.libtorrent4j.arm64)
	implementation(libs.libtorrent4j.x64)

	testImplementation(libs.junit)
	testImplementation(libs.mockito.core)
}
