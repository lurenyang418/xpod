import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.hilt) apply false
  id("com.diffplug.spotless") version "7.2.1"
}

configure<SpotlessExtension> {
  kotlin {
    target("app/src/*/java/**/*.kt", "app/src/*/kotlin/**/*.kt")
    ktfmt()
  }

  kotlinGradle {
    target("*.gradle.kts", "**/*.gradle.kts")
    ktfmt()
  }

  format("misc") {
    target("**/*.md", ".gitignore", ".gitattributes")
    trimTrailingWhitespace()
    endWithNewline()
  }
}
