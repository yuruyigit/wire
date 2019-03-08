package com.squareup.wire.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Test
import java.io.File

class WirePluginTest {
  private lateinit var gradleRunner: GradleRunner
  @Before
  fun setUp() {
    gradleRunner = GradleRunner.create()
        .withPluginClasspath()
        .withArguments("generateProtos")
  }

  @Test
  fun missingPlugin() {
    val fixtureRoot = File("src/test/projects/missing-plugin")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .buildAndFail()

    assertThat(result.task(":generateProtos")).isNull()
    assertThat(result.output).contains(
        """The Wire Gradle plugin requires either the Java, Kotlin or Android plugin to be applied prior to its being applied."""
    )
  }

  @Test
  fun sourcePathDirDoesNotExist() {
    val fixtureRoot = File("src/test/projects/sourcepath-nonexistent-dir")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .buildAndFail()

    assertThat(result.task(":generateProtos")).isNull()
    assertThat(result.output).contains(
        """Invalid path string: "src/main/proto". Path does not exist."""
    )
  }

  @Test
  fun useDefaultSourcePath() {
    val fixtureRoot = File("src/test/projects/default-sourcepath")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .build()

    val task = result.task(":generateProtos")
    assertThat(task).isNotNull
    assertThat(task!!.outcome).isEqualTo(TaskOutcome.NO_SOURCE)
  }

  @Test
  fun sourcePathStringShouldNotBeRegularFile() {
    val fixtureRoot = File("src/test/projects/sourcepath-file")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .buildAndFail()

    assertThat(result.task(":generateProtos")).isNull()
    assertThat(result.output)
        .contains(
            """Invalid path string: "src/main/proto/squareup/geology/period.proto". For individual files, use the closure syntax."""
        )
  }

  @Test
  fun sourcePathStringShouldNotBeUri() {
    val fixtureRoot = File("src/test/projects/sourcepath-uri")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .buildAndFail()

    assertThat(result.task(":generateProtos")).isNull()
    assertThat(result.output)
        .contains(
            """Invalid path string: "http://www.squareup.com". URL dependencies are not allowed."""
        )
  }

  @Test
  fun sourcePathDir() {
    val fixtureRoot = File("src/test/projects/sourcepath-dir")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .build()

    assertThat(result.task(":generateProtos")).isNotNull
    assertThat(result.output)
        .contains("Writing com.squareup.dinosaurs.Dinosaur")
        .contains("Writing com.squareup.geology.Period")
        .contains("src/test/projects/sourcepath-dir/build/generated/src/main/java")
  }

  @Test
  fun sourcePathMavenCoordinates() {
    val fixtureRoot = File("src/test/projects/sourcepath-maven")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .build()

    assertThat(result.task(":generateProtos")).isNotNull
    assertThat(result.output)
        .contains("Writing com.squareup.dinosaurs.Dinosaur")
        .contains("Writing com.squareup.geology.Period")
        .contains("src/test/projects/sourcepath-maven/build/generated/src/main/java")
  }

  @Test
  fun sourceTreeOneSrcDirOneFile() {
    val fixtureRoot = File("src/test/projects/sourcetree-one-srcdir-one-file")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .build()

    assertThat(result.task(":generateProtos")).isNotNull
    assertThat(result.output)
        .contains("Writing com.squareup.geology.Period")
        .contains("src/test/projects/sourcetree-one-srcdir-one-file/build/generated/src/main/java")
  }

  @Test
  fun sourceTreeOneSrcDirMultipleFiles() {
    val fixtureRoot = File("src/test/projects/sourcetree-one-srcdir-many-files")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .build()

    assertThat(result.task(":generateProtos")).isNotNull
    assertThat(result.output)
        .contains("Writing com.squareup.dinosaurs.Dinosaur")
        .contains("Writing com.squareup.geology.Period")
        .contains(
            "src/test/projects/sourcetree-one-srcdir-many-files/build/generated/src/main/java"
        )
  }

  @Test
  fun sourceTreeMultipleSrcDirs() {
    val fixtureRoot = File("src/test/projects/sourcetree-many-srcdirs")
    val result = gradleRunner
        .withProjectDir(fixtureRoot)
        .build()

    assertThat(result.task(":generateProtos")).isNotNull
    assertThat(result.output)
        .contains("Writing com.squareup.dinosaurs.Dinosaur")
        .contains("Writing com.squareup.geology.Period")
        .contains("src/test/projects/sourcetree-many-srcdirs/build/generated/src/main/java")
  }
}