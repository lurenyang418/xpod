import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

val signingProperties = Properties()
val signingPropertiesFile = rootProject.file("keystore.properties")

if (signingPropertiesFile.exists()) {
  signingPropertiesFile.inputStream().use(signingProperties::load)
}

android {
  namespace = "app.xpod"
  compileSdk = 36

  defaultConfig {
    applicationId = "tech.lury.xpod"
    minSdk = 33
    targetSdk = 36
    versionCode = 2
    versionName = "0.1.1"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }

  buildTypes {
    val stableSigningConfig =
        if (signingPropertiesFile.exists()) {
          signingConfigs.create("xpodRelease") {
            storeFile = rootProject.file(requireNotNull(signingProperties.getProperty("storeFile")))
            storePassword = requireNotNull(signingProperties.getProperty("storePassword"))
            keyAlias = requireNotNull(signingProperties.getProperty("keyAlias"))
            keyPassword = requireNotNull(signingProperties.getProperty("keyPassword"))
          }
        } else {
          null
        }
    debug { stableSigningConfig?.let { signingConfig = it } }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = stableSigningConfig ?: signingConfigs.getByName("debug")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
  }

  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a")
      isUniversalApk = false
    }
  }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.datasource.okhttp)
  implementation(libs.androidx.media3.database)
  implementation(libs.androidx.media3.exoplayer.workmanager)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.okhttp)
  implementation(libs.coil.compose)
  debugImplementation(libs.androidx.compose.ui.tooling)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.kxml)
}
