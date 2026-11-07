import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// Release signing plumbing (see docs/RELEASE.md for the full scheme).
//
// The release keystore is NEVER committed. Credentials are read from, in order
// of precedence:
//   1. Environment variables: ANDROID_KEYSTORE_PATH, ANDROID_KEY_ALIAS,
//      ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_PASSWORD (used by release CI
//      when repo secrets are configured).
//   2. Root-level `keystore.properties` (gitignored, machine-local):
//        storeFile=/abs/or/relative/path/to/release.jks
//        storePassword=...
//        keyAlias=...
//        keyPassword=...
//
// When none of these are present the release build still succeeds and simply
// produces an UNSIGNED APK, so local `assembleRelease` and CI keep working for
// every developer. Missing signing is a distribution blocker (Play upload),
// never a build blocker.
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStoreFilePath = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")

val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

// Room DatabaseVerifier embeds SQLite JDBC, which extracts a native lib into a temp dir that
// must be readable/writable/executable. Windows C:\WINDOWS\TEMP\ often fails that check.
// org.sqlite.tmpdir must be a JVM system property (not a Gradle project property).
val sqliteTmpDir = rootProject.layout.projectDirectory.dir(".tmp/sqlite").asFile
sqliteTmpDir.mkdirs()
System.setProperty("org.sqlite.tmpdir", sqliteTmpDir.absolutePath)
tasks.configureEach {
    if (this is JavaForkOptions) {
        systemProperty("org.sqlite.tmpdir", sqliteTmpDir.absolutePath)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

composeCompiler {
    includeSourceInformation = false
    includeTraceMarkers = false
}

configurations.matching { it.name == "composeMappingProducerClasspath" }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.jetbrains.kotlin:compose-group-mapping")).using(module("androidx.annotation:annotation:1.7.0"))
    }
}

tasks.matching { it.name.contains("ComposeMapping") }.configureEach {
    enabled = false
}

android {
    namespace = "com.montecarlo.ledger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.montecarlo.ledger"
        minSdk = 26
        targetSdk = 36

        // Versioning scheme (documented in docs/RELEASE.md):
        //   versionName = MAJOR.MINOR.PATCH (human-facing, SemVer-style)
        //   versionCode = strictly increasing integer, bumped on EVERY Play upload
        //                 (never reused, never decreased — Play rejects re-use).
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                // rootProject.file() resolves relative paths against the repo
                // root and passes absolute paths through unchanged.
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Never log or print credential material.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Sign only when a keystore was actually provided. Otherwise the
            // release build intentionally falls through to
            // app-release-unsigned.apk so CI and local builds never break.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Surface the signing mode once per configuration so builds are never
    // silently signed or silently unsigned.
    logger.lifecycle(
        if (hasReleaseSigning) {
            "Release signing: ENABLED (keystore '${releaseStoreFilePath}', alias '${releaseKeyAlias}')"
        } else {
            "Release signing: DISABLED — no keystore.properties / ANDROID_KEYSTORE_* env; " +
                "release output will be UNSIGNED (app-release-unsigned.apk)."
        }
    )

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // Room's MigrationTestHelper reads exported schema JSON from assets.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    lint {
        // local.properties is machine-local and gitignored; PropertyEscape is not a product defect.
        disable += "PropertyEscape"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.exifinterface:exifinterface:1.3.6")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Shared DesignSystem library
    implementation("com.workspace:design")

    // Glance Home Screen Widget
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Material Icons Extended
    implementation("androidx.compose.material:material-icons-extended")

    // Haze — backdrop blur for glassmorphism (API 21+)
    implementation("dev.chrisbanes.haze:haze:1.5.1")

    // Room
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.12.2")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.03.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
