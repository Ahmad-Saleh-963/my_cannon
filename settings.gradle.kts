pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                // Do not change the username
                username = "mapbox"
                
                // Read from local.properties, gradle.properties or environment variable
                val localProperties = java.util.Properties().apply {
                    val localFile = File(rootDir, "local.properties")
                    if (localFile.exists()) {
                        localFile.inputStream().use { load(it) }
                    }
                }
                
                val token = localProperties.getProperty("MAPBOX_DOWNLOADS_TOKEN")
                    ?: providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull
                    ?: providers.environmentVariable("MAPBOX_DOWNLOADS_TOKEN").getOrElse("")
                
                password = token
            }
        }
    }
}

rootProject.name = "my_cannon"
include(":app")
