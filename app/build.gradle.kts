import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    
    applicationId = "com.aistudio.ispbilling.control"
    minSdk = 24
    targetSdk = 35
    val envVersionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
      ?: project.findProperty("versionName")?.toString()?.takeIf { it.isNotBlank() }
    val envVersionCode = System.getenv("VERSION_CODE")?.takeIf { it.isNotBlank() }?.toIntOrNull()
      ?: project.findProperty("versionCode")?.toString()?.toIntOrNull()

    versionCode = envVersionCode ?: 24
    versionName = envVersionName ?: "1.0.24"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val fullRepoEnv = System.getenv("GITHUB_REPOSITORY") ?: ""
    val defaultOwner = if (fullRepoEnv.contains("/")) fullRepoEnv.split("/")[0] else (System.getenv("GITHUB_OWNER") ?: "isp-app")
    val defaultRepo = if (fullRepoEnv.contains("/")) fullRepoEnv.split("/")[1] else (System.getenv("GITHUB_REPO") ?: "isp-billing-app")

    buildConfigField("String", "GITHUB_OWNER", "\"$defaultOwner\"")
    buildConfigField("String", "GITHUB_REPO", "\"$defaultRepo\"")

    val googleApiKey = (System.getenv("GOOGLE_API_KEY")?.takeIf { it.isNotBlank() }
      ?: providers.gradleProperty("GOOGLE_API_KEY").orNull?.takeIf { it.isNotBlank() }
      ?: project.findProperty("GOOGLE_API_KEY")?.toString()?.takeIf { it.isNotBlank() }
      ?: System.getProperty("GOOGLE_API_KEY")?.takeIf { it.isNotBlank() }
      ?: "").trim()
    
    val safeApiKey = if (googleApiKey.isEmpty()) "\"\"" else "\"${googleApiKey.replace("\"", "\\\"")}\""
    buildConfigField("String", "GOOGLE_API_KEY", safeApiKey)
  }

  signingConfigs {
    create("release") {
      val envKeystorePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
      val storePasswordEnv = (System.getenv("STORE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() })
      val keyAliasEnv = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
      val keyPasswordEnv = (System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: storePasswordEnv)

      val ksFile = when {
        !envKeystorePath.isNullOrBlank() && file(envKeystorePath).exists() -> file(envKeystorePath)
        file("${rootDir}/release.keystore").exists() -> file("${rootDir}/release.keystore")
        file("${rootDir}/internet-billing-release.jks").exists() -> file("${rootDir}/internet-billing-release.jks")
        else -> null
      }

      if (ksFile != null && !storePasswordEnv.isNullOrEmpty()) {
        storeFile = ksFile
        storePassword = storePasswordEnv
        keyAlias = if (!keyAliasEnv.isNullOrEmpty()) keyAliasEnv else "upload"
        keyPassword = keyPasswordEnv
        enableV1Signing = true
        enableV2Signing = true
      }
    }
  }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      
      val releaseConfig = signingConfigs.getByName("release")
      if (releaseConfig.storeFile != null && releaseConfig.storeFile!!.exists() && !releaseConfig.storePassword.isNullOrEmpty()) {
        signingConfig = releaseConfig
      } else {
        signingConfig = signingConfigs.getByName("debug")
      }
    }
    debug {
      val debugKs = file("${rootDir}/debug.keystore")
      if (debugKs.exists()) {
        signingConfig = signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
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
  implementation(platform(libs.firebase.bom))
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
  implementation(libs.androidx.core.splashscreen)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)
  // implementation(libs.firebase.analytics)
  implementation(libs.firebase.messaging)
  implementation(libs.androidx.work.runtime.ktx)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
