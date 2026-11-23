import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.StringReader
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// ---------------------------------------------------------------------------
// Konfigurasi cabang: SATU file (cabang.properties di root proyek).
// Cabang lain? Edit file itu saja, atau simpan banyak file di cabang/<nama>.properties
// lalu build dengan:  ./gradlew assembleRelease -Pcabang=<nama>
// ---------------------------------------------------------------------------
val cabangFileProvider = providers.gradleProperty("cabang")
  .map { "cabang/$it.properties" }
  .orElse("cabang.properties")
val cabangText = providers
  .fileContents(rootProject.layout.projectDirectory.file(cabangFileProvider))
  .asText
  .getOrElse("")
if (cabangText.isBlank()) {
  throw GradleException("File konfigurasi cabang tidak ditemukan / kosong: ${cabangFileProvider.get()}")
}
val cabangProps = Properties().apply { load(StringReader(cabangText)) }
fun cabang(key: String, default: String = ""): String =
  cabangProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: default
fun cabangWajib(key: String): String =
  cabang(key).ifEmpty { throw GradleException("cabang.properties: '$key' wajib diisi.") }
fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val cNama = cabangWajib("CABANG_NAMA")
val cSlug = cNama.lowercase().replace(Regex("[^a-z0-9]"), "")
val cKode = cabang("CABANG_KODE")
val cAppName = cabang("APP_NAME", "RKM SPI $cNama")
val cLabel = cabang("LABEL", (cNama + if (cKode.isNotEmpty()) " - $cKode" else "").uppercase())
val cSubtitle = cabang("SUBTITLE", "Stock Point Indogrosir $cNama")

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = cabang("APPLICATION_ID", "com.aistudio.rkmspi.$cSlug")
    minSdk = 26
    targetSdk = 36
    versionCode = 8
    versionName = "1.0.7"

    buildConfigField("boolean", "UPDATE_FEATURE_ENABLED", "true")

    // Nilai cabang (dari cabang.properties) -> dibaca lewat AppConfig.kt
    resValue("string", "app_name", cAppName)
    buildConfigField("String", "APP_NAME", q(cAppName))
    buildConfigField("String", "CABANG_NAMA", q(cNama))
    buildConfigField("String", "CABANG_LABEL", q(cLabel))
    buildConfigField("String", "CABANG_SUBTITLE", q(cSubtitle))
    buildConfigField("String", "SERVER_HOST", q(cabangWajib("SERVER_HOST")))
    buildConfigField("String", "APP_PATH", q(cabang("APP_PATH", "/sales-gps-dashboard")))
    buildConfigField("String", "API_KEY", q(cabangWajib("API_KEY")))
    buildConfigField("String", "TIMEZONE", q(cabang("TIMEZONE", "Asia/Jakarta")))
    buildConfigField("int", "CONFIG_VERSION", cabang("CONFIG_VERSION", "1").toInt().toString())
    buildConfigField("double", "MAP_LAT", cabang("MAP_LAT", "-2.5").toDouble().toString())
    buildConfigField("double", "MAP_LNG", cabang("MAP_LNG", "118.0").toDouble().toString())
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  androidResources {
    localeFilters += listOf("id", "en")
  }

  // Buang blob metadata dependensi (khusus Google Play) dari APK: lebih kecil, fungsi aplikasi sama.
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePropertiesFile = rootProject.file("keystore.properties")
      if (!keystorePropertiesFile.exists()) {
        throw GradleException("Build release gagal: 'keystore.properties' tidak ditemukan di ${keystorePropertiesFile.absolutePath}!")
      }
      val keystoreProperties = Properties().apply {
        keystorePropertiesFile.inputStream().use { load(it) }
      }
      val relStoreFilePath = keystoreProperties.getProperty("storeFile")
        ?: throw GradleException("Build release gagal: properti 'storeFile' tidak ditemukan di keystore.properties")
      val kFile = rootProject.file(relStoreFilePath)
      if (!kFile.exists()) {
        throw GradleException("Build release gagal: file keystore tidak ditemukan di ${kFile.absolutePath}!")
      }
      storeFile = kFile
      storePassword = keystoreProperties.getProperty("storePassword")
        ?: throw GradleException("Build release gagal: properti 'storePassword' tidak ditemukan di keystore.properties")
      keyAlias = keystoreProperties.getProperty("keyAlias")
        ?: throw GradleException("Build release gagal: properti 'keyAlias' tidak ditemukan di keystore.properties")
      keyPassword = keystoreProperties.getProperty("keyPassword")
        ?: throw GradleException("Build release gagal: properti 'keyPassword' tidak ditemukan di keystore.properties")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
    resValues = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    resources {
      excludes += listOf(
        "META-INF/AL2.0",
        "META-INF/LGPL2.1",
        "META-INF/ASL2.0",
        "META-INF/LICENSE*",
        "META-INF/NOTICE*",
        "META-INF/DEPENDENCIES",
        "META-INF/INDEX.LIST",
        "META-INF/*.txt",
        "META-INF/*.md",
        "META-INF/*.version",
        "META-INF/*.kotlin_module",
        "META-INF/com/android/build/gradle/app-metadata.properties",
        "META-INF/version-control-info.textproto",
        "**/*.kotlin_metadata",
        "okhttp3/internal/publicsuffix/NOTICE",
        "DebugProbesKt.bin",
        "kotlin-tooling-metadata.json",
        "keystore.properties",
        "*.jks"
      )
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.osmdroid.android)
  implementation(libs.androidx.profileinstaller)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation("com.squareup.okhttp3:mockwebserver:4.10.0")
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}