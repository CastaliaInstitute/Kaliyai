import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/** Read root `.env` and return GEMINI_API_KEY (no newlines; quotes optional). */
fun readGeminiApiKeyFromEnv(envFile: File): String {
    if (!envFile.exists()) return ""
    return envFile.readLines()
        .asSequence()
        .map { it.substringBefore("#").trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val k = line.substring(0, i).trim()
            if (k != "GEMINI_API_KEY") return@mapNotNull null
            var v = line.substring(i + 1).trim()
            if (v.length >= 2) {
                val q0 = v.first()
                if ((q0 == '"' || q0 == '\'') && v.last() == q0) {
                    v = v.substring(1, v.length - 1)
                }
            }
            v
        }
        .firstOrNull() ?: ""
}

/**
 * Value safe to embed in a Java string literal for BuildConfig, e.g. "foo\"bar".
 */
fun escapeForJavaStringLiteral(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "")

/** Resolve sibling repo `castalia.institute/.env` (see README). */
fun castaliaInstituteEnvFile(gradleRoot: File): File =
    gradleRoot.parentFile.resolve("../castalia.institute/.env").normalize()

val geminiKeyFromRootEnv: String = run {
    val gradleRoot = rootProject.layout.projectDirectory.asFile
    val local = readGeminiApiKeyFromEnv(File(gradleRoot, ".env"))
    if (local.isNotEmpty()) {
        local
    } else {
        readGeminiApiKeyFromEnv(castaliaInstituteEnvFile(gradleRoot))
    }
}
val buildConfigGeminiValue: String = if (geminiKeyFromRootEnv.isEmpty()) {
    "\"\""
} else {
    "\"${escapeForJavaStringLiteral(geminiKeyFromRootEnv)}\""
}

android {
    namespace = "com.kali.nethunter.mcpchat"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kali.nethunter.mcpchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "BAKED_GEMINI_API_KEY", buildConfigGeminiValue)
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.webkit:webkit:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.5.0")
}

tasks.withType<Test>().configureEach {
    // Propagate to the test JVM (IDE/CI may not do this for child processes)
    val k = System.getenv("GEMINI_API_KEY")
    if (k != null) {
        environment("GEMINI_API_KEY", k)
    }
    val live = System.getenv("KALIYAI_LIVE_GEMINI_EVAL")
    if (live != null) {
        environment("KALIYAI_LIVE_GEMINI_EVAL", live)
    }
}
