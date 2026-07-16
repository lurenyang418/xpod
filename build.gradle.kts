import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.androidx.room) apply false
  id("com.diffplug.spotless") version "8.8.0"
}

configure<SpotlessExtension> {
  kotlin {
    target("app/src/*/java/**/*.kt", "app/src/*/kotlin/**/*.kt")
    ktfmt("0.63")
  }

  kotlinGradle {
    target("*.gradle.kts", "**/*.gradle.kts")
    ktfmt("0.63")
  }

  format("misc") {
    target("**/*.md", ".gitignore", ".gitattributes")
    trimTrailingWhitespace()
    endWithNewline()
  }
}
