/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire.gradle

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.squareup.wire.gradle.WireExtension.JavaTarget
import com.squareup.wire.schema.Target
import org.gradle.api.DomainObjectSet
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.file.FileOrUriNotationConverter
import org.gradle.api.internal.file.SourceDirectorySetFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.net.URI
import javax.inject.Inject
import com.android.build.gradle.BasePlugin as AndroidBasePlugin

class WirePlugin @Inject constructor(
  private val sourceDirectorySetFactory: SourceDirectorySetFactory
) : Plugin<Project> {
  private var kotlin = false
  private var android = false
  private var java = false

  override fun apply(project: Project) {
    val logger = project.logger

    val extension = project.extensions.create(
        "wire", WireExtension::class.java, project, sourceDirectorySetFactory
    )

    project.plugins.all {
      logger.debug("plugin: $it")
      when (it) {
        is AndroidBasePlugin<*> -> {
          android = true
          logger.debug("has android")
        }
        is JavaBasePlugin -> {
          java = true
          logger.debug("has java")
        }
        is KotlinBasePluginWrapper -> {
          kotlin = true
          logger.debug("has kotlin")
        }
      }
    }

    if (!android && !kotlin && !java) {
      throw IllegalArgumentException(
          "The Wire Gradle plugin requires either the Java, Kotlin or Android plugin to be applied prior to its being applied."
      )
    }

    project.afterEvaluate { project ->
      val ssc = project.property("sourceSets") as SourceSetContainer
      if (logger.isDebugEnabled) {
        ssc.forEach {
          logger.debug("source set: ${it.name}")
        }
      }

      when {
        android -> applyAndroid(project, extension)
        kotlin || java -> applyJvm(project, extension)
        else -> throw IllegalStateException("Impossible")
      }
    }
  }

  private fun applyAndroid(
    project: Project,
    extension: WireExtension
  ) {
    val variants: DomainObjectSet<out BaseVariant> = when {
      project.plugins.hasPlugin("com.android.application") -> {
        project.extensions.getByType(AppExtension::class.java)
            .applicationVariants
      }
      project.plugins.hasPlugin("com.android.library") -> {
        project.extensions.getByType(LibraryExtension::class.java)
            .libraryVariants
      }
      else -> {
        throw IllegalStateException("Unknown Android plugin in project '${project.path}'")
      }
    }

    applyAndroid(project, extension, variants)
  }

  private fun applyAndroid(
    project: Project,
    extension: WireExtension,
    variants: DomainObjectSet<out BaseVariant>
  ) {
    variants.all { variant ->
      project.logger.debug("variant: ${variant.name}")

      val sourcePaths = extension.sourcePaths ?: project.files("src/main/proto")
      val protoPaths = extension.protoPaths ?: sourcePaths

      val targets = mutableListOf<Target>()
      val defaultBuildDirectory = "${project.buildDir}/generated/source/wire"
      val outDirs = mutableListOf<String>()

      extension.javaTarget?.let { target ->
        val javaOut = target.outDirectory ?: defaultBuildDirectory
        outDirs += javaOut
        targets += Target.JavaTarget(
            elements = target.elements ?: listOf("*"),
            outDirectory = javaOut,
            android = target.android,
            androidAnnotations = target.androidAnnotations,
            compact = target.compact
        )
      }
      extension.kotlinTarget?.let { target ->
        val kotlinOut = target.outDirectory ?: defaultBuildDirectory
        outDirs += kotlinOut
        targets += Target.KotlinTarget(
            elements = target.elements ?: listOf("*"),
            outDirectory = kotlinOut,
            android = target.android,
            javaInterop = target.javaInterop
        )
      }

      val taskName = "generate${variant.name.capitalize()}Protos"
      val taskProvider = project.tasks.register(taskName, WireTask::class.java) {
        //it.sourceFolders = sourceSets.files
        it.source(sourcePaths)
//        it.sourcePaths = sourceSets
//        it.protoPaths = protoPaths
        it.roots = extension.roots?.asList() ?: emptyList()
        it.prunes = extension.prunes?.asList() ?: emptyList()
        it.rules = extension.rules
        it.targets = targets
        it.group = "wire"
        it.description = "Generate Wire protocol buffer implementation for .proto files"
      }
      // TODO Use task configuration avoidance once released. https://issuetracker.google.com/issues/117343589
      val map = outDirs.map(::File)
      variant.registerJavaGeneratingTask(taskProvider.get(), map)
    }
  }

  private fun applyJvm(
    project: Project,
    extension: WireExtension
  ) {
    val sourceConfiguration = project.configurations.create("wireSourceDependencies")

    val sourcePaths = if (!extension.sourcePaths.isEmpty() || !extension.sourceTrees.isEmpty()) {
      mergeDependencyPaths(project, extension.sourcePaths, extension.sourceTrees)
    } else {
      mergeDependencyPaths(project, setOf("src/main/proto"))
    }
    sourcePaths.forEach {
      sourceConfiguration.dependencies.add(project.dependencies.create(it))
    }

    val protoConfiguration = project.configurations.create("wireProtoDependencies")

    if (!extension.protoPaths.isEmpty() || !extension.protoTrees.isEmpty()) {
      val allPaths = mergeDependencyPaths(project, extension.protoPaths, extension.protoTrees)
      allPaths.forEach { path ->
        protoConfiguration.dependencies.add(project.dependencies.create(path))
      }
    } else {
      protoConfiguration.dependencies.addAll(sourceConfiguration.dependencies)
    }

    // all this point, all source and proto file references should be set up for Gradle to resolve.

    val targets = mutableListOf<Target>()
    val defaultBuildDirectory = "${project.buildDir}/generated/src/main/java"
    val javaOutDirs = mutableListOf<String>()
    val kotlinOutDirs = mutableListOf<String>()

    val kotlinTarget = extension.kotlinTarget
    val javaTarget = extension.javaTarget ?: if (kotlinTarget != null) null else JavaTarget()

    javaTarget?.let { target ->
      val javaOut = target.outDirectory ?: defaultBuildDirectory
      javaOutDirs += javaOut
      targets += Target.JavaTarget(
          elements = target.elements ?: listOf("*"),
          outDirectory = javaOut,
          android = target.android,
          androidAnnotations = target.androidAnnotations,
          compact = target.compact
      )
    }
    kotlinTarget?.let { target ->
      val kotlinOut = target.outDirectory ?: defaultBuildDirectory
      kotlinOutDirs += kotlinOut
      targets += Target.KotlinTarget(
          elements = target.elements ?: listOf("*"),
          outDirectory = kotlinOut,
          android = target.android,
          javaInterop = target.javaInterop
      )
    }

    val wireTask = project.tasks.register("generateProtos", WireTask::class.java) { task ->
      task.source(sourceConfiguration)
      task.sourceConfiguration = sourceConfiguration
      task.protoConfiguration = protoConfiguration
      task.roots = extension.roots?.asList() ?: emptyList()
      task.prunes = extension.prunes?.asList() ?: emptyList()
      task.rules = extension.rules
      task.targets = targets
      task.group = "wire"
      task.description = "Generate Wire protocol buffer implementation for .proto files"
    }

    extension.javaTarget?.let {
      val compileTask = project.tasks.named("compileJava") as TaskProvider<JavaCompile>
      compileTask.configure {
        it.source(javaOutDirs)
        it.dependsOn(wireTask)
      }
    }

    extension.kotlinTarget?.let {
      val compileTask = project.tasks.named("compileKotlin") as TaskProvider<KotlinCompile>
      compileTask.configure {
        it.source(kotlinOutDirs)
        it.dependsOn(wireTask)
      }
    }
  }

  private fun mergeDependencyPaths(
    project: Project,
    dependencyPaths: Set<String>,
    dependencyTrees: Set<SourceDirectorySet> = emptySet()
  ): List<Any> {
    val allPaths = mutableListOf<Any>()
    dependencyTrees.forEach {
      allPaths += it as Any
    }

    dependencyPaths.forEach { path ->
      val parser = FileOrUriNotationConverter.parser()
      val converted = parser.parseNotation(path)

      if (converted is File) {
        val file =
          if (!converted.isAbsolute) File(project.projectDir, converted.path) else converted
        if (!file.exists()) {
          throw IllegalArgumentException(
              "Invalid path string: \"$path\". Path does not exist."
          )
        }
        if (file.isDirectory) {
          allPaths += project.files(path) as Any
        } else {
          throw IllegalArgumentException(
              "Invalid path string: \"$path\". For individual files, use the closure syntax."
          )
        }
      } else if (converted is URI && isURL(converted)) {
        throw IllegalArgumentException(
            "Invalid path string: \"$path\". URL dependencies are not allowed."
        )
      } else {
        // assume it's a possible external dependency and let Gradle sort it out later...
        allPaths += path
      }
    }

    return allPaths
  }

  private fun isURL(uri: URI) =
    try {
      uri.toURL()
      true
    } catch (e: Exception) {
      false
    }
}