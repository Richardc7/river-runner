plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}
val exploitVersion = rootProject.file("EXPLOIT_VERSION").readText().trim()
val exploitVersionCode = rootProject.file("EXPLOIT_VERSION_CODE").readText().trim().toInt()
val versionNameOverride = System.getenv("VERSION_NAME")

android {
    namespace = "com.riverrunner"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.riverrunner.exploit"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: exploitVersionCode
        versionName = versionNameOverride ?: "v$exploitVersion"
    }
    signingConfigs {
        create("release") {
            val storePath = System.getenv("RR_KEYSTORE_PATH") ?: project.providers.gradleProperty("rr.storeFile").orNull
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = System.getenv("RR_STORE_PASSWORD") ?: project.providers.gradleProperty("rr.storePassword").orNull
                keyAlias = System.getenv("RR_KEY_ALIAS") ?: project.providers.gradleProperty("rr.keyAlias").orNull
                keyPassword = System.getenv("RR_KEY_PASSWORD") ?: project.providers.gradleProperty("rr.keyPassword").orNull
            }
        }
    }
    buildTypes {
        debug {
            if (versionNameOverride == null) versionNameSuffix = "-exploit-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxHeapSize = "3g"
            }
        }
    }
}
abstract class CopyChangelog : DefaultTask() {
    @get:InputFile abstract val changelog: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @TaskAction fun copy() {
        val out = outputDir.get().asFile; out.mkdirs()
        changelog.get().asFile.copyTo(File(out, "changelog.md"), overwrite = true)
    }
}
val copyChangelog = tasks.register<CopyChangelog>("copyChangelog") {
    changelog.set(rootProject.layout.projectDirectory.file("EXPLOIT_CHANGELOG.md"))
}
androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(copyChangelog, CopyChangelog::outputDir) }
}
dependencies {
    implementation(project(":engine"))
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
