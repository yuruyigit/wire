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
package com.squareup.wire.gradle;

import groovy.lang.Closure;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.util.ConfigureUtil;

public class WireExtension {
  private final ObjectFactory objectFactory;
  private final SourceDirectorySetFactory sourceDirectorySetFactory;
  private final Set<String> sourcePaths;
  private final Set<String> protoPaths;
  private final Set<SourceDirectorySet> sourceTrees;
  private final Set<SourceDirectorySet> protoTrees;
  private String[] roots;
  private String[] prunes;
  private File rules;
  private JavaTarget javaTarget;
  private KotlinTarget kotlinTarget;

  public WireExtension(Project project, SourceDirectorySetFactory sourceDirectorySetFactory) {
    this.objectFactory = project.getObjects();
    this.sourceDirectorySetFactory = sourceDirectorySetFactory;

    sourcePaths = new LinkedHashSet<>();
    protoPaths = new LinkedHashSet<>();
    sourceTrees = new LinkedHashSet<>();
    protoTrees = new LinkedHashSet<>();
  }

  @InputFiles
  public Set<String> getSourcePaths() {
    return sourcePaths;
  }

  @InputFiles
  public Set<SourceDirectorySet> getSourceTrees() {
    return sourceTrees;
  }

  /**
   * Source paths for local jars and directories, as well as remote binary dependencies
   */
  public void sourcePath(String... sourcePaths) {
    this.sourcePaths.addAll(Arrays.asList(sourcePaths));
  }

  /**
   * Source paths for local file trees, backed by a {@link org.gradle.api.file.SourceDirectorySet}
   * Must provide at least a {@link org.gradle.api.file.SourceDirectorySet#srcDir(Object)}
   */
  public void sourcePath(Closure<SourceDirectorySet> closure) {
    SourceDirectorySet sourceTree =
        sourceDirectorySetFactory.create("source-tree", "Source path tree");
    sourceTree.getFilter().include("**/*.proto");
    ConfigureUtil.configure(closure, sourceTree);
    sourceTrees.add(sourceTree);
  }

  @InputFiles
  @Optional
  public Set<String> getProtoPaths() {
    return protoPaths;
  }

  @InputFiles
  public Set<SourceDirectorySet> getProtoTrees() {
    return protoTrees;
  }

  public void protoPath(String... protoPaths) {
    this.protoPaths.addAll(Arrays.asList(protoPaths));
  }

  public void protoPath(Closure<SourceDirectorySet> closure) {
    SourceDirectorySet protoTree =
        sourceDirectorySetFactory.create("proto-tree", "Proto path tree");
    protoTree.getFilter().include("**/*.proto");
    ConfigureUtil.configure(closure, protoTree);
    protoTrees.add(protoTree);
  }

  @Input
  @Optional
  public String[] getRoots() {
    return roots;
  }

  public void setRoots(String[] roots) {
    this.roots = roots;
  }

  @Input
  @Optional
  public String[] getPrunes() {
    return prunes;
  }

  public void setPrunes(String[] prunes) {
    this.prunes = prunes;
  }

  @Input
  @Optional
  public File getRules() {
    return rules;
  }

  public void setRules(File rules) {
    this.rules = rules;
  }

  @Input
  @Optional
  public JavaTarget getJavaTarget() {
    return javaTarget;
  }

  public void java(Action<JavaTarget> action) {
    javaTarget = objectFactory.newInstance(JavaTarget.class);
    action.execute(javaTarget);
  }

  @Input
  @Optional
  public KotlinTarget getKotlinTarget() {
    return kotlinTarget;
  }

  public void kotlin(Action<KotlinTarget> action) {
    kotlinTarget = objectFactory.newInstance(KotlinTarget.class);
    action.execute(kotlinTarget);
  }

  static class JavaTarget {
    @SuppressWarnings("RedundantModifier")
    @Inject public JavaTarget() {
    }

    public List<String> elements;
    public String outDirectory;
    public boolean android;
    public boolean androidAnnotations;
    public boolean compact;
  }

  static class KotlinTarget {
    @SuppressWarnings("RedundantModifier")
    @Inject public KotlinTarget() {
    }

    public String outDirectory;
    public List<String> elements;
    public boolean android;
    public boolean javaInterop;
  }
}
