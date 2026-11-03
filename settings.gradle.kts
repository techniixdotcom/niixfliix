pluginManagement {
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "niixfliix"

include(":core")
include(":desktop")
if (providers.gradleProperty("linuxOnly").orNull != "true") {
	include(":app")
}
