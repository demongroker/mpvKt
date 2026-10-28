import com.android.build.api.variant.FilterConfiguration
import io.gitlab.arturbosch.detekt.Detekt
import org.apache.commons.io.output.ByteArrayOutputStream

plugins {
  alias(libs.plugins.ksp)
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.room)
  alias(libs.plugins.detekt)
  alias(libs.plugins.about.libraries)
  alias(libs.plugins.kotlinx.serialization)
}

android {
  namespace = "live.mehiz.mpvkt"
  compileSdk = 36

  defaultConfig {
    applicationId = "app.vela.player"
    minSdk = 21
    targetSdk = 36
    versionCode = 350
    versionName = "0.1.22"

    vectorDrawables {
      useSupportLibrary = true
    }

    buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")
    buildConfigField("String", "GIT_TAG", "\"${getGitTag()}\"")
    buildConfigField("int", "GIT_COUNT", getCommitCount())
  }
  signingConfigs {
    create("release") {
      val ks = System.getenv("MPVKT_KEYSTORE").orEmpty()
      if (ks.isNotEmpty() && file(ks).exists()) {
        storeFile = file(ks)
        storePassword = System.getenv("MPVKT_STORE_PASSWORD")
        keyAlias = System.getenv("MPVKT_KEY_ALIAS")
        keyPassword = System.getenv("MPVKT_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    named("release") {
      val relSigning = signingConfigs.getByName("release")
      if (relSigning.storeFile != null) {
        signingConfig = relSigning
      }
      isMinifyEnabled = true
      isShrinkResources = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
    create("preview") {
      initWith(getByName("release"))

      signingConfig = signingConfigs["debug"]
      applicationIdSuffix = ".preview"
      versionNameSuffix = "-${getCommitCount()}"
    }
    named("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-${getCommitCount()}"
    }
  }
  // Single universal APK on purpose: per-ABI splits hand out different versionCodes,
  // and an app that installed a split cannot later "upgrade" to the universal build.
  splits {
    abi {
      isEnable = false
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86_64")
      isUniversalApk = true
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
    viewBinding = true
    buildConfig = true
  }
  composeCompiler {
    includeSourceInformation = true
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
  val abiCodes = mapOf(
    "armeabi-v7a" to 1,
    "arm64-v8a" to 2,
    "x86" to 3,
    "x86_64" to 4,
  )
  androidComponents {
    onVariants { variant ->
      variant.outputs.forEach { output ->
        val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
        output.versionCode.set((output.versionCode.orNull ?: 0) * 10 + (abiCodes[abi] ?: 0))
      }
    }
  }
  androidResources {
    generateLocaleConfig = true
  }
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xwhen-guards", "-Xcontext-parameters")
  }
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3.android)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.bundles.compose.navigation3)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.compose.constraintlayout)
  implementation(libs.androidx.material3.icons.extended)
  implementation(libs.androidx.compose.animation.graphics)
  implementation(libs.material)
  implementation(libs.androidx.preferences.ktx)
  implementation(libs.androidx.documentfile)
  implementation(libs.mediasession)
  implementation(libs.saveable)

  implementation(libs.mpv.lib)

  implementation(platform(libs.koin.bom))
  implementation(libs.bundles.koin)

  implementation(libs.seeker)
  implementation(libs.compose.prefs)
  implementation(libs.bundles.about.libs)
  implementation(libs.simple.icons)

  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.room.ktx)

  implementation(libs.detekt.gradle.plugin)
  detektPlugins(libs.detekt.rules.compose)
  detektPlugins(libs.detekt.formatter)

  implementation(libs.kotlinx.immutable.collections)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.truetype.parser)
  implementation(libs.fsaf)
}

detekt {
  parallel = true
  allRules = false
  buildUponDefaultConfig = true
  config.setFrom("$rootDir/config/detekt/detekt.yml")
}

tasks.withType<Detekt>().configureEach {
  setSource(files(project.projectDir))
  exclude("**/build/**")
  reports {
    html.required.set(true)
    md.required.set(true)
  }
}

fun getCommitCount(): String = runCommand("git rev-list --count HEAD")
fun getCommitSha(): String = runCommand("git rev-parse --short HEAD")

/** Nearest tag, e.g. v0.1.11-vela11 — used to compare the installed build with a release. */
fun getGitTag(): String = runCommand("git describe --tags --abbrev=0").trim()
fun runCommand(command: String): String {
  val stdOut = ByteArrayOutputStream()
  exec {
    commandLine = command.split(' ')
    standardOutput = stdOut
    isIgnoreExitValue = true
  }
  return String(stdOut.toByteArray()).trim()
}

aboutLibraries {
  excludeFields = arrayOf("generated")
}
