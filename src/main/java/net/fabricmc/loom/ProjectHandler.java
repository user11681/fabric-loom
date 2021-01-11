/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.fabricmc.loom;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jfrog.bintray.gradle.BintrayExtension;
import com.jfrog.bintray.gradle.BintrayPlugin;
import groovy.util.Node;
import net.gudenau.lib.unsafe.Unsafe;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaModule;
import user11681.reflect.Accessor;
import user11681.reflect.Classes;

import net.fabricmc.loom.api.decompilers.LoomDecompiler;
import net.fabricmc.loom.decompilers.cfr.FabricCFRDecompiler;
import net.fabricmc.loom.decompilers.fernflower.FabricFernFlowerDecompiler;
import net.fabricmc.loom.dependency.LoomDependencyFactory;
import net.fabricmc.loom.dependency.configuration.BloatedDependencySet;
import net.fabricmc.loom.dependency.configuration.IntransitiveDependencySet;
import net.fabricmc.loom.extension.LoomExtension;
import net.fabricmc.loom.providers.LaunchProvider;
import net.fabricmc.loom.providers.MappingsCache;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.repository.LoomRepositoryFactory;
import net.fabricmc.loom.task.AbstractLoomTask;
import net.fabricmc.loom.task.CleanEclipseRunsTask;
import net.fabricmc.loom.task.CleanLoomBinaries;
import net.fabricmc.loom.task.CleanLoomMappings;
import net.fabricmc.loom.task.DownloadAssetsTask;
import net.fabricmc.loom.task.GenEclipseRunsTask;
import net.fabricmc.loom.task.GenIdeaProjectTask;
import net.fabricmc.loom.task.GenVsCodeProjectTask;
import net.fabricmc.loom.task.GenerateSourcesTask;
import net.fabricmc.loom.task.MigrateMappingsTask;
import net.fabricmc.loom.task.RemapAllSourcesTask;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.RemapSourcesJarTask;
import net.fabricmc.loom.task.RunClientTask;
import net.fabricmc.loom.task.RunServerTask;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.FabricApiExtension;
import net.fabricmc.loom.util.GroovyXmlUtil;
import net.fabricmc.loom.util.JarRemapper;
import net.fabricmc.loom.util.LoomDependencyManager;
import net.fabricmc.loom.util.NestedJars;
import net.fabricmc.loom.util.RemappedConfigurationEntry;
import net.fabricmc.loom.util.SetupIntelijRunConfigs;
import net.fabricmc.loom.util.SourceRemapper;
import net.fabricmc.loom.util.mixin.JavaApInvoker;
import net.fabricmc.loom.util.mixin.KaptApInvoker;
import net.fabricmc.loom.util.mixin.ScalaApInvoker;

@SuppressWarnings({"ResultOfMethodCallIgnored", "UnstableApiUsage", "ConstantConditions", "unchecked"})
public class ProjectHandler {
    public static String latestMinecraftVersion = null;
    public static Map<String, String> latestYarnBuilds = new HashMap<>();
    public static Project currentProject = null;

    private static HttpClient httpClient = null;

    public final Project project;
    public final Project rootProject;
    public final PluginContainer plugins;
    public final PluginManager pluginManager;
    public final Convention convention;
    public final TaskContainer tasks;
    public final ExtensionContainer extensions;
    public final RepositoryHandler repositories;
    public final DependencyHandler dependencies;
    public final ConfigurationContainer configurations;
    public final ArtifactHandler artifacts;
    public final Logger logger;
    public final Gradle gradle;
    public final ScriptHandler buildScript;
    public final LoomExtension extension;

    private final List<LoomDecompiler> decompilers = new ArrayList<>();

    public ProjectHandler(Project project) {
        this.project = project;
        this.rootProject = project.getRootProject();
        this.plugins = project.getPlugins();
        this.pluginManager = project.getPluginManager();
        this.convention = project.getConvention();
        this.tasks = project.getTasks();
        this.extensions = project.getExtensions();
        this.repositories = project.getRepositories();
        this.dependencies = project.getDependencies();
        this.configurations = project.getConfigurations();
        this.artifacts = project.getArtifacts();
        this.logger = project.getLogger();
        this.gradle = project.getGradle();
        this.buildScript = project.getBuildscript();
        this.extension = this.extensions.create("loom", LoomExtension.class, this.project);
    }

    public static boolean isRootProject(Project project) {
        return project.getRootProject() == project;
    }

    public static File getMappedByproduct(Project project, String suffix) {
        String path = project.getExtensions().getByType(LoomExtension.class).getMappingsProvider().mappedProvider.getMappedJar().getAbsolutePath();

        if (!path.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new RuntimeException("Invalid mapped JAR path: $path");
        }

        return new File(path.substring(0, path.length() - 4) + suffix);
    }

    private static String sendGET(String uri) {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        }

        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(uri)).build();

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Throwable throwable) {
            throw Unsafe.throwException(throwable);
        }
    }

    private void checkMinecraftVersion() {
        if (this.extension.minecraftVersion == null) {
            if (latestMinecraftVersion == null) {
                String body = sendGET("https://meta.fabricmc.net/v2/versions/game");
                Matcher matcher = Pattern.compile("(?<=\").*(?=\")").matcher(body.substring(body.indexOf(":") + 1));

                matcher.find();
                latestMinecraftVersion = matcher.group();
            }

            this.extension.minecraftVersion = latestMinecraftVersion;
        }
    }

    private void checkYarnBuild() {
        if (this.extension.yarnBuild == null) {
            if (latestYarnBuilds.get(extension.minecraftVersion) == null) {
                String body = sendGET("https://meta.fabricmc.net/v2/versions/yarn/" + extension.minecraftVersion);
                Matcher matcher = Pattern.compile("\\d+").matcher(body.substring(body.indexOf("separator")));

                matcher.find();
                latestYarnBuilds.put(extension.minecraftVersion, matcher.group());
            }

            this.extension.yarnBuild = latestYarnBuilds.get(extension.minecraftVersion);
        }
    }

    public void handle() {
        currentProject = this.project;

        this.logger.lifecycle("Fabric Loom: " + ProjectHandler.class.getPackage().getImplementationVersion());

        DownloadUtil.refreshDeps = gradle.getStartParameter().isRefreshDependencies();

        if (DownloadUtil.refreshDeps) {
            MappingsCache.INSTANCE.invalidate();

            this.logger.lifecycle("Refresh dependencies is in use; loom will be significantly slower.");
        }

        this.plugins.apply(JavaLibraryPlugin.class);
        this.plugins.apply(MavenPublishPlugin.class);
        this.plugins.apply(EclipsePlugin.class);
        this.plugins.apply(IdeaPlugin.class);

        Classes.staticCast(Accessor.getObject(this.repositories, "repositoryFactory"), LoomRepositoryFactory.classPointer);
        Classes.staticCast(Accessor.getObject(this.dependencies, "dependencyFactory"), LoomDependencyFactory.classPointer);

//        this.configurations.create("dev");

        this.project.beforeEvaluate(ignored -> this.beforeEvaluate());
        this.project.afterEvaluate(ignored -> this.afterEvaluate());

        this.plugins.apply(BintrayPlugin.class);

        this.extensions.add("minecraft", this.extension);
        this.extensions.create("fabricApi", FabricApiExtension.class, this.project);

        this.repositories.mavenLocal();
        this.repositories.mavenCentral();
        this.repositories.jcenter();

        this.repositories.flatDir((FlatDirectoryArtifactRepository repository) -> repository.dir(this.extension.getRootProjectBuildCache()));
        this.repositories.flatDir((FlatDirectoryArtifactRepository repository) -> repository.dir(this.extension.getRemappedModCache()));

        this.repositories.maven((MavenArtifactRepository repository) -> repository.setUrl("https://libraries.minecraft.net/"));
        this.repositories.maven((MavenArtifactRepository repository) -> repository.setUrl("https://maven.fabricmc.net/"));

        this.configureConfigurations();
        this.configureIDEs();
        this.configureCompile();
        this.registerTasks();

        this.decompilers.add(new FabricFernFlowerDecompiler(project));
        this.decompilers.add(new FabricCFRDecompiler(project));
    }

    private void registerTasks() {
        this.tasks.register("cleanLoomBinaries", CleanLoomBinaries.class, (CleanLoomBinaries task) -> task.setDescription("Removes binary jars created by Loom."));
        this.tasks.register("cleanLoomMappings", CleanLoomMappings.class, (CleanLoomMappings task) -> task.setDescription("Removes mappings downloaded by Loom."));

        this.tasks.register("cleanLoom").configure((Task task) -> {
            task.setGroup("fabric");
            task.setDescription("Runs all Loom cleanup tasks.");
            task.dependsOn(tasks.getByName("cleanLoomBinaries"));
            task.dependsOn(tasks.getByName("cleanLoomMappings"));
        });

        this.tasks.register("migrateMappings", MigrateMappingsTask.class, (MigrateMappingsTask task) -> {
            task.setDescription("Migrates mappings to a new version.");
            task.getOutputs().upToDateWhen(ignored -> false);
        });

        this.tasks.register("remapJar", RemapJarTask.class, (RemapJarTask task) -> {
            task.setDescription("Remaps the built project jar to intermediary mappings.");
            task.setGroup("fabric");
            task.doLast((Task remapTask) -> remapTask.getInputs().getFiles().forEach(File::delete));
        });

        this.tasks.register("downloadAssets", DownloadAssetsTask.class, (DownloadAssetsTask task) -> task.setDescription("Downloads required assets for Fabric."));

        this.tasks.register("genIdeaWorkspace", GenIdeaProjectTask.class, (GenIdeaProjectTask task) -> {
            task.setDescription("Generates an IntelliJ IDEA workspace from this project.");
            task.dependsOn("idea", "downloadAssets");
            task.setGroup("ide");
        });

        this.tasks.register("genEclipseRuns", GenEclipseRunsTask.class, (GenEclipseRunsTask task) -> {
            task.setDescription("Generates Eclipse run configurations for this project.");
            task.dependsOn("downloadAssets");
            task.setDescription("ide");
        });

        this.tasks.register("cleanEclipseRuns", CleanEclipseRunsTask.class, (CleanEclipseRunsTask task) -> {
            task.setDescription("Removes Eclipse run configurations for this project.");
            task.setDescription("ide");
        });

        this.tasks.register("vscode", GenVsCodeProjectTask.class, (GenVsCodeProjectTask task) -> {
            task.setDescription("Generates VSCode launch configurations.");
            task.dependsOn("downloadAssets");
            task.setGroup("ide");
        });

        this.tasks.register("remapSourcesJar", RemapSourcesJarTask.class, (RemapSourcesJarTask task) -> task.setDescription("Remaps the project sources jar to intermediary names."));

        this.tasks.register("runClient", RunClientTask.class, (RunClientTask task) -> {
            task.setDescription("Starts a development version of the Minecraft client.");
            task.dependsOn("downloadAssets");
            task.setGroup("fabric");
        });

        this.tasks.register("runServer", RunServerTask.class, (RunServerTask task) -> {
            task.setDescription("Starts a development version of the Minecraft server.");
            task.setGroup("fabric");
        });
    }

    private void configureConfigurations() {
        Configuration modCompileClasspathConfig = this.configurations.maybeCreate(Constants.Configurations.MOD_COMPILE_CLASSPATH).setTransitive(true);
        Configuration modCompileClasspathMappedConfig = this.configurations.maybeCreate(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED).setTransitive(false);
        Configuration minecraftNamedCompileConfig = this.configurations.maybeCreate(Constants.Configurations.MINECRAFT_NAMED_COMPILE).setTransitive(false);
        Configuration minecraftNamedRuntimeConfig = this.configurations.maybeCreate(Constants.Configurations.MINECRAFT_NAMED_RUNTIME).setTransitive(false);
        Configuration minecraftDependenciesConfig = this.configurations.maybeCreate(Constants.Configurations.MINECRAFT_DEPENDENCIES).setTransitive(false);
        Configuration includeConfig = this.configurations.maybeCreate(Constants.Configurations.INCLUDE).setTransitive(false);
        Configuration mappingsFinal = this.configurations.maybeCreate(Constants.Configurations.MAPPINGS_FINAL);

        this.configurations.maybeCreate(Constants.Configurations.MINECRAFT).setTransitive(false);
        this.configurations.maybeCreate(Constants.Configurations.MAPPINGS);

        for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
            Configuration compileModsConfig = this.configurations.maybeCreate(entry.getSourceConfiguration()).setTransitive(true);
            Configuration compileModsMappedConfig = this.configurations.maybeCreate(entry.getRemappedConfiguration()).setTransitive(false);

            this.configurations.getByName(entry.getTargetConfiguration(configurations)).extendsFrom(compileModsMappedConfig);

            if (entry.isOnModCompileClasspath()) {
                modCompileClasspathConfig.extendsFrom(compileModsConfig);
                modCompileClasspathMappedConfig.extendsFrom(compileModsMappedConfig);
            }
        }

        Configuration intransitiveInclude = this.configurations.create("intransitiveInclude");
        Configuration intransitive = this.configurations.create("intransitive").extendsFrom(intransitiveInclude);
        Configuration bloatedInclude = this.configurations.create("bloatedInclude");
        Configuration bloated = this.configurations.create("bloated").extendsFrom(bloatedInclude);
        Configuration modInclude = this.configurations.create("modInclude").extendsFrom(bloatedInclude, intransitiveInclude);
        Configuration mod = this.configurations.create("mod").extendsFrom(modInclude, bloated, intransitive);
        Configuration apiInclude = this.configurations.create("apiInclude");

        Classes.staticCast(Accessor.getObject(bloated, "dependencies"), BloatedDependencySet.classPointer);
        Classes.staticCast(Accessor.getObject(bloatedInclude, "dependencies"), BloatedDependencySet.classPointer);
        Classes.staticCast(Accessor.getObject(intransitive, "dependencies"), IntransitiveDependencySet.classPointer);
        Classes.staticCast(Accessor.getObject(intransitiveInclude, "dependencies"), IntransitiveDependencySet.classPointer);

        this.configurations.getByName("compileClasspath").extendsFrom(minecraftNamedCompileConfig);
        this.configurations.getByName("runtimeClasspath").extendsFrom(minecraftNamedRuntimeConfig);
        this.configurations.getByName("testCompileClasspath").extendsFrom(minecraftNamedCompileConfig);
        this.configurations.getByName("testRuntimeClasspath").extendsFrom(minecraftNamedRuntimeConfig);

        minecraftNamedCompileConfig.extendsFrom(minecraftDependenciesConfig);
        minecraftNamedRuntimeConfig.extendsFrom(minecraftDependenciesConfig);

        this.configurations.getByName("compile").extendsFrom(mappingsFinal);
        this.configurations.getByName("api").extendsFrom(apiInclude);
        this.configurations.getByName("modApi").extendsFrom(mod);

        includeConfig.extendsFrom(apiInclude, modInclude);
    }

    /**
     * Add Minecraft dependencies to IDE dependencies.
     */
    private void configureIDEs() {
        // IDEA
        IdeaModule module = ((IdeaModel) extensions.getByName("idea")).getModule();
        module.getExcludeDirs().addAll(project.files(".gradle", "build", ".idea", "out").getFiles());
        module.setDownloadJavadoc(true);
        module.setDownloadSources(true);
        module.setInheritOutputDirs(true);
    }

    /**
     * Add Minecraft dependencies to compile time.
     */
    private void configureCompile() {
        SourceSet main = this.convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Javadoc javadoc = (Javadoc) tasks.getByName(JavaPlugin.JAVADOC_TASK_NAME);

        javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));

        if (this.pluginManager.hasPlugin("org.jetbrains.kotlin.kapt")) {
            // If loom is applied after kapt, then kapt will use the AP arguments too early for loom to pass the arguments we need for mixin.
            throw new IllegalArgumentException("loom must be applied BEFORE kapt.");
        }
    }

    private void beforeEvaluate() {
        currentProject = this.project;
    }

    private void afterEvaluate() {
        this.checkMinecraftVersion();
        this.checkYarnBuild();

        this.dependencies.add("minecraft", "com.mojang:minecraft:" + this.extension.minecraftVersion);
        this.dependencies.add("mappings", String.format("net.fabricmc:yarn:%s+build.%s:v2", this.extension.minecraftVersion, this.extension.yarnBuild));
        this.dependencies.add("modApi", "net.fabricmc:fabric-loader:+");
        this.dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter:+");

        if (this.extension.noSpam) {
            this.dependencies.add("modApi", "narratoroff");
            this.dependencies.add("modApi", "noauth");
        }

        for (JavaCompile task : this.tasks.withType(JavaCompile.class)) {
            task.getOptions().setEncoding("UTF-8");

            task.setSourceCompatibility(this.extension.javaVersion.toString());
            task.setTargetCompatibility(task.getSourceCompatibility());
        }

        this.extensions.getByType(JavaPluginExtension.class).withSourcesJar();

        for (Jar task : this.tasks.withType(Jar.class)) {
//            task.getArchiveClassifier().set("dev");

            task.from("LICENSE");
        }

        ProcessResources processResources = (ProcessResources) this.tasks.getByName("processResources");
        processResources.getInputs().property("version", this.project.getVersion());
        processResources.filesMatching("fabric.mod.json", (FileCopyDetails details) -> details.expand(new HashMap<>(Map.of("version", project.getVersion()))));

//        File devJar = project.file(String.format("%s/libs/%s-%s-dev.jar", this.project.getBuildDir(), this.project.getName(), this.project.getVersion()));

//        this.artifacts.add("dev", devJar, (ConfigurablePublishArtifact artifact) -> artifact.builtBy(tasks.getByName("jar")).setType("jar"));

//        if (devJar.exists()) {
//            RemapJarTask task = (RemapJarTask) tasks.getByName("remapJar");
//
//            task.getInput().set(devJar);
//            task.getArchiveFileName().set(String.format("%s-%s.jar", project.getName(), project.getVersion()));
//        }

        if (this.extension.publish()) {
            PublishingExtension publishing = this.extensions.getByType(PublishingExtension.class);

            publishing.getPublications().create("maven", MavenPublication.class, (MavenPublication publication) -> {
                publication.setGroupId((String) this.project.getGroup());
                publication.setArtifactId(this.project.getName());
                publication.setVersion((String) this.project.getVersion());

                Task remapJar = this.tasks.getByName("remapJar");

                publication.artifact(remapJar).builtBy(remapJar);
                publication.artifact(this.tasks.getByName("sourcesJar")).builtBy(this.tasks.getByName("remapSourcesJar"));
            });

            publishing.getRepositories().mavenLocal();

            SourceSet testSet = this.convention.getPlugin(JavaPluginConvention.class).getSourceSets().getByName("test");

            this.tasks.create("runTestClient", RunClientTask.class).classpath(testSet.getRuntimeClasspath());
            this.tasks.create("runTestServer", RunServerTask.class).classpath(testSet.getRuntimeClasspath());

            if (this.extension.bintray()) {
                BintrayExtension bintray = this.extensions.getByType(BintrayExtension.class);
                bintray.setUser(System.getenv("BINTRAY_USER"));
                bintray.setKey(System.getenv("BINTRAY_API_KEY"));
                bintray.setPublications("maven");
                bintray.setPublish(true);

                BintrayExtension.PackageConfig pkg = bintray.getPkg();
                pkg.setRepo("maven");
                pkg.setName(project.getName());
                pkg.setLicenses("LGPL-3.0");

                BintrayExtension.VersionConfig version = pkg.getVersion();
                version.setName(String.valueOf(project.getVersion()));
                version.setReleased(new Date().toString());
            }
        }

        for (LoomDecompiler decompiler : this.decompilers) {
            String taskName = decompiler instanceof FabricFernFlowerDecompiler ? "genSources" : "genSourcesWith" + decompiler.name();

            // decompiler will be passed to the constructor of GenerateSourcesTask
            this.tasks.register(taskName, GenerateSourcesTask.class, decompiler);
        }

        this.configureMaven();
    }

    private void configureMaven() {
        for (RemappedConfigurationEntry entry : Constants.MOD_COMPILE_ENTRIES) {
            if (!entry.hasMavenScope()) {
                continue;
            }

            Configuration compileModsConfig = this.configurations.getByName(entry.getSourceConfiguration());

            // add modsCompile to maven-publish
            for (Publication publication : this.extensions.getByType(PublishingExtension.class).getPublications()) {
                if (publication instanceof MavenPublication) {
                    ((MavenPublication) publication).getPom().withXml((XmlProvider xml) -> {
                        Node dependencies = GroovyXmlUtil.getOrCreateNode(xml.asNode(), "dependencies");
                        Set<String> foundArtifacts = new HashSet<>();

                        for (Node node : (List<Node>) dependencies.children()) {
                            if (node.name().equals("dependency")) {
                                Optional<Node> groupId = GroovyXmlUtil.getNode(node, "groupId");
                                Optional<Node> artifactId = GroovyXmlUtil.getNode(node, "artifactId");

                                if (groupId.isPresent() && artifactId.isPresent()) {
                                    foundArtifacts.add(groupId.get().text() + ":" + artifactId.get().text());
                                }
                            }
                        }

                        for (Dependency dependency : compileModsConfig.getAllDependencies()) {
                            if (foundArtifacts.contains(dependency.getGroup() + ":" + dependency.getName())) {
                                continue;
                            }

                            Node depNode = dependencies.appendNode("dependency");
                            depNode.appendNode("groupId", dependency.getGroup());
                            depNode.appendNode("artifactId", dependency.getName());
                            depNode.appendNode("version", dependency.getVersion());
                            depNode.appendNode("scope", entry.getMavenScope());

                            if (dependency instanceof ModuleDependency) {
                                Set<ExcludeRule> exclusions = ((ModuleDependency) dependency).getExcludeRules();

                                if (!exclusions.isEmpty()) {
                                    Node exclusionsNode = depNode.appendNode("exclusions");

                                    for (ExcludeRule rule : exclusions) {
                                        Node exclusionNode = exclusionsNode.appendNode("exclusion");
                                        exclusionNode.appendNode("groupId", rule.getGroup() == null ? "*" : rule.getGroup());
                                        exclusionNode.appendNode("artifactId", rule.getModule() == null ? "*" : rule.getModule());
                                    }
                                }
                            }
                        }
                    });
                }
            }
        }

        LoomDependencyManager dependencyManager = new LoomDependencyManager();
        this.extension.setDependencyManager(dependencyManager);

        dependencyManager.addProvider(new MinecraftProvider(project));
        dependencyManager.addProvider(new MappingsProvider(project));
        dependencyManager.addProvider(new LaunchProvider(project));
        dependencyManager.handleDependencies(project);

        this.tasks.getByName("idea").finalizedBy(this.tasks.getByName("genIdeaWorkspace"));
        this.tasks.getByName("eclipse").finalizedBy(this.tasks.getByName("genEclipseRuns"));
        this.tasks.getByName("cleanEclipse").finalizedBy(this.tasks.getByName("cleanEclipseRuns"));

        if (this.extension.autoGenIDERuns) {
            SetupIntelijRunConfigs.setup(project);
        }

        Jar jarTask = (Jar) this.tasks.getByName("jar");

        // Enables the default mod remapper
        if (this.extension.remapMod) {
            RemapJarTask remapJarTask = (RemapJarTask) this.tasks.getByName("remapJar");

            if (!remapJarTask.getInput().isPresent()) {
                jarTask.getArchiveClassifier().set("dev");

                remapJarTask.getArchiveClassifier().set("");
                remapJarTask.getInput().set(jarTask.getArchiveFile());
            }

            this.extension.getUnmappedModCollection().from(jarTask);

            remapJarTask.getAddNestedDependencies().set(true);
            remapJarTask.getRemapAccessWidener().set(true);

            this.project.getArtifacts().add("archives", remapJarTask);

            remapJarTask.dependsOn(jarTask);

            this.tasks.getByName("build").dependsOn(remapJarTask);

            Map<Project, Set<Task>> taskMap = this.project.getAllTasks(true);

            for (Set<Task> tasks : taskMap.values()) {
                for (Task task : tasks) {
                    if (task instanceof RemapJarTask && ((RemapJarTask) task).getAddNestedDependencies().getOrElse(false)) {
                        //Run all the sub project remap jars tasks before the root projects jar, this is to allow us to include projects
                        for (RemapJarTask remapTask : NestedJars.getRequiredTasks(this.project)) {
                            task.dependsOn(remapTask);
                        }
                    }
                }
            }

            SourceRemapper remapper = null;
            Task parentTask = tasks.getByName("build");

            if (this.extension.shareCaches) {
                if (this.extension.isRootProject()) {
                    SourceRemapper sourceRemapper = new SourceRemapper(this.rootProject, false);
                    JarRemapper jarRemapper = new JarRemapper();

                    remapJarTask.jarRemapper = jarRemapper;

                    this.rootProject.getTasks().register("remapAllSources", RemapAllSourcesTask.class, (RemapAllSourcesTask task) -> {
                        task.sourceRemapper = sourceRemapper;
                        task.doLast(ignored -> sourceRemapper.remapAll());
                    });

                    parentTask = this.rootProject.getTasks().getByName("remapAllSources");
                    this.rootProject.getTasks().register("remapAllJars", AbstractLoomTask.class, (AbstractLoomTask task) -> task.doLast(ignored -> {
                        try {
                            jarRemapper.remap();
                        } catch (IOException exception) {
                            System.out.println("Failed to remap jars");

                            throw Unsafe.throwException(exception);
                        }
                    }));
                } else {
                    parentTask = this.rootProject.getTasks().getByName("remapAllSources");
                    remapper = ((RemapAllSourcesTask) parentTask).sourceRemapper;
                    remapJarTask.jarRemapper = ((RemapJarTask) this.rootProject.getTasks().getByName("remapJar")).jarRemapper;

                    this.tasks.getByName("build").dependsOn(parentTask);
                    this.tasks.getByName("build").dependsOn(this.rootProject.getTasks().getByName("remapAllJars"));

                    this.rootProject.getTasks().getByName("remapAllJars").dependsOn(this.tasks.getByName("remapJar"));
                }
            }

            try {
                Jar sourcesTask = (Jar) this.tasks.getByName("sourcesJar");
                RemapSourcesJarTask remapSourcesJarTask = (RemapSourcesJarTask) this.tasks.findByName("remapSourcesJar");

                remapSourcesJarTask.setInput(sourcesTask.getArchiveFile());
                remapSourcesJarTask.setOutput(sourcesTask.getArchiveFile());
                remapSourcesJarTask.doLast(ignored -> this.artifacts.add("archives", remapSourcesJarTask.getOutput()));
                remapSourcesJarTask.dependsOn(tasks.getByName("sourcesJar"));

                if (this.extension.shareCaches) {
                    remapSourcesJarTask.setSourceRemapper(remapper);
                }

                parentTask.dependsOn(remapSourcesJarTask);
            } catch (UnknownTaskException ignored) {}
        } else {
            this.extension.getUnmappedModCollection().from(jarTask);
        }

        // Disable some things used by log4j via the mixin AP that prevent it from being garbage collected
        System.setProperty("log4j2.disable.jmx", "true");
        System.setProperty("log4j.shutdownHookEnabled", "false");

        this.logger.info("Configuring compiler arguments for Java");

        new JavaApInvoker(this.project).configureMixin();

        if (this.pluginManager.hasPlugin("scala")) {
            this.logger.info("Configuring compiler arguments for Scala");

            new ScalaApInvoker(this.project).configureMixin();
        }

        if (this.pluginManager.hasPlugin("org.jetbrains.kotlin.kapt")) {
            this.logger.info("Configuring compiler arguments for Kapt plugin");

            new KaptApInvoker(this.project).configureMixin();
        }
    }
}