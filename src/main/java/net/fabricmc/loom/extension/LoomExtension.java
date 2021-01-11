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

package net.fabricmc.loom.extension;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import javax.annotation.Nullable;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.BasePluginConvention;

import net.fabricmc.loom.ProjectHandler;
import net.fabricmc.loom.dependency.DependencyContainer;
import net.fabricmc.loom.dependency.DependencyEntry;
import net.fabricmc.loom.processors.JarProcessor;
import net.fabricmc.loom.processors.JarProcessorManager;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.providers.MinecraftProvider;
import net.fabricmc.loom.util.LoomDependencyManager;
import net.fabricmc.loom.util.mappings.MojangMappingsDependency;

@SuppressWarnings({"ConstantConditions", "ResultOfMethodCallIgnored"})
public class LoomExtension {
    public final RunDirectory run = new RunDirectory(this);
    public final Project project;

    private final ConfigurableFileCollection unmappedMods;
    private final List<JarProcessor> jarProcessors = new ArrayList<>();
    private final MappingSet[] srcMappingCache = new MappingSet[2];
    private final Mercury[] srcMercuryCache = new Mercury[2];
    private final Set<File> mixinMappings = Collections.synchronizedSet(new HashSet<>());

    private LoomDependencyManager dependencyManager;
    private JarProcessorManager jarProcessorManager;
    private JsonObject installerJson;

    private boolean publish = true;
    private boolean bintray = true;

    public boolean autoGenIDERuns = true;
    public boolean remapMod = true;
    public boolean noSpam = true;
    public boolean shareCaches = true;

    public String refmapName;
    public String loaderLaunchMethod;
    public String customManifest;
    public File accessWidener;
    public List<String> enumWidener = new ArrayList<>();
    public Function<String, Object> intermediaryUrl = (String mcVer) -> String.format("https://maven.fabricmc.net/net/fabricmc/intermediary/%s/intermediary-%s-v2.jar", mcVer, mcVer);

    public String minecraftVersion;
    public String yarnBuild;

    public JavaVersion javaVersion = JavaVersion.VERSION_1_8;

    private static final Map<String, String> repositoryMap = new HashMap<>(Map.ofEntries(
        Map.entry("blamejared", "https://maven.blamejared.com"),
        Map.entry("boundarybreaker", "https://server.bbkr.space/artifactory/libs-release"),
        Map.entry("buildcraft", "https://mod-buildcraft.com/maven"),
        Map.entry("cursemaven", "https://www.cursemaven.com"),
        Map.entry("dblsaiko", "https://maven.dblsaiko.net/"),
        Map.entry("earthcomputer", "https://dl.bintray.com/earthcomputer/mods"),
        Map.entry("grossfabrichackers", "https://raw.githubusercontent.com/GrossFabricHackers/maven/master"),
        Map.entry("halfof2", "https://raw.githubusercontent.com/Devan-Kerman/Devan-Repo/master"),
        Map.entry("jamieswhiteshirt", "https://maven.jamieswhiteshirt.com/libs-release"),
        Map.entry("jitpack", "https://jitpack.io"),
        Map.entry("ladysnake", "https://dl.bintray.com/ladysnake/libs"),
        Map.entry("user11681", "https://dl.bintray.com/user11681/maven"),
        Map.entry("wrenchable", "https://dl.bintray.com/zundrel/wrenchable")
    ));

    public static final DependencyContainer dependencies = new DependencyContainer(
        new DependencyEntry("net.fabricmc.fabric-api:fabric-api:latest.release").key("api"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-api-base:latest.release").key("apibase"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-blockrenderlayer-v1:latest.release").key("apiblockrenderlayer"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-command-api-v1:latest.release").key("apicommand"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-screen-handler-api-v1:latest.release").key("apiscreenhandler"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-events-interaction-v0:latest.release").key("apieventsinteraction"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-key-binding-api-v1:latest.release").key("apikeybindings"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:latest.release").key("apilifecycleevents"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-networking-api-v1:latest.release").key("apinetworking"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-renderer-api-v1:latest.release").key("apirendererapi"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-renderer-indigo:latest.release").key("apirendererindigo"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-resource-loader-v0:latest.release").key("apiresourceloader"),
        new DependencyEntry("net.fabricmc.fabric-api:fabric-tag-extensions-v0:latest.release").key("apitagextensions"),
        new DependencyEntry("curse.maven:aquarius-301299:3132504").key("aquarius").repository("cursemaven"),
        new DependencyEntry("net.devtech:arrp:latest.release").key("arrp").repository("halfof2"),
        new DependencyEntry("com.github.Chainmail-Studios:Astromine:1.8.1").key("astromine").repository("jitpack"),
        new DependencyEntry("me.sargunvohra.mcmods:autoconfig1u:latest.release").key("autoconfig"),
        new DependencyEntry("me.shedaniel.cloth:basic-math:latest.release").key("basicmath"),
        new DependencyEntry("user11681:bason:latest.release").key("bason").repository("user11681"),
        new DependencyEntry("io.github.onyxstudios.Cardinal-Components-API:Cardinal-Components-API:latest.release").key("cardinalcomponents").repository("ladysnake"),
        new DependencyEntry("io.github.onyxstudios.Cardinal-Components-API:cardinal-components-base:latest.release").key("cardinalcomponentsbase").repository("ladysnake"),
        new DependencyEntry("io.github.onyxstudios.Cardinal-Components-API:cardinal-components-entity:latest.release").key("cardinalcomponentsentity").repository("ladysnake"),
        new DependencyEntry("io.github.onyxstudios.Cardinal-Components-API:cardinal-components-item:latest.release").key("cardinalcomponentsitem").repository("ladysnake"),
        new DependencyEntry("user11681:cell:latest.release").key("cell").repository("user11681"),
        new DependencyEntry("curse.maven:charm-318872:3140951").key("charm").repository("cursemaven"),
        new DependencyEntry("me.shedaniel.cloth:config-2:latest.release").key("clothconfig"),
        new DependencyEntry("io.github.cottonmc:cotton-resources:latest.release").key("cottonresources").repository("boundarybreaker"),
        new DependencyEntry("user11681:commonformatting:latest.release").key("commonformatting").repository("user11681"),
        new DependencyEntry("user11681:dynamicentry:latest.release").key("dynamicentry").repository("user11681"),
        new DependencyEntry("com.github.Chocohead:Fabric-ASM:master-SNAPSHOT").key("fabricasm").repository("jitpack"),
        new DependencyEntry("curse.maven:moenchantments-320806:3084973").key("moenchantments").repository("cursemaven"),
        new DependencyEntry("user11681:fabricasmtools:latest.release").key("huntinghamhills").repository("user11681"),
        new DependencyEntry("net.devtech:grossfabrichacks:latest.release").key("grossfabrichacks").repository("grossfabrichackers"),
        new DependencyEntry("user11681:invisiblelivingentities:latest.release").key("invisiblelivingentities").repository("user11681"),
        new DependencyEntry("com.github.javaparser:javaparser-symbol-solver-core:latest.release").key("javaparser"),
        new DependencyEntry("org.jooq:joor-java-8:latest.release").key("joor"),
        new DependencyEntry("org.junit.jupiter:junit-jupiter:latest.release").key("junit"),
        new DependencyEntry("com.github.Yoghurt4C:LilTaterReloaded:fabric-1.16-SNAPSHOT").key("liltaterreloaded").repository("jitpack"),
        new DependencyEntry("user11681:limitless:latest.release").key("limitless").repository("user11681"),
        new DependencyEntry("io.github.prospector:modmenu:latest.release").key("modmenu"),
        new DependencyEntry("net.earthcomputer:multiconnect:latest.release:api").key("multiconnect").repository("earthcomputer"),
        new DependencyEntry("user11681:narratoroff:latest.release").key("narratoroff").repository("user11681"),
        new DependencyEntry("user11681:noauth:latest.release").key("noauth").repository("user11681"),
        new DependencyEntry("user11681:optional:latest.release").key("optional").repository("user11681"),
        new DependencyEntry("user11681:phormat:latest.release").key("phormat").repository("user11681"),
        new DependencyEntry("user11681:projectfabrok:latest.release").key("projectfabrok").repository("user11681"),
        new DependencyEntry("user11681:prone:latest.release").key("prone").repository("user11681"),
        new DependencyEntry("com.jamieswhiteshirt:reach-entity-attributes:latest.release").key("reachentityattributes").repository("jamieswhiteshirt"),
        new DependencyEntry("user11681:reflect:latest.release").key("reflect").repository("user11681"),
        new DependencyEntry("me.shedaniel:RoughlyEnoughItems:latest.release").key("rei"),
        new DependencyEntry("user11681:shortcode:latest.release").key("shortcode").repository("user11681"),
        new DependencyEntry("com.moandjiezana.toml:toml4j:latest.release").key("toml4j"),
        new DependencyEntry("net.gudenau.lib:unsafe:latest.release").key("unsafe").repository("user11681")
    );

    public LoomExtension(Project project) {
        this.project = project;
        this.unmappedMods = project.files();
    }

    private static String sanitize(String key) {
        return key.replace("-", "").toLowerCase(Locale.ROOT);
    }

    public static String repository(String key) {
        return key == null ? null : repositoryMap.get(sanitize(key));
    }

    public static DependencyEntry dependency(String key) {
        return dependencies.entry(sanitize(key));
    }

    public static void repository(String key, String value) {
        repositoryMap.put(key, value);
    }

    public boolean publish() {
        return this.publish;
    }

    public void publish(boolean publish) {
        this.publish = publish;
    }

    public boolean bintray() {
        return this.bintray;
    }

    public void bintray(boolean bintray) {
        this.bintray = bintray;
    }

    public void setJavaVersion(Object version) {
        this.javaVersion = JavaVersion.toVersion(version);
    }

    /**
     * Add a transformation over the mapped mc jar.
     * Adding any jar processor will cause mapped mc jars to be stored per-project so that
     * different transformation can be applied in different projects.
     * This means remapping will need to be done individually per-project, which is slower when developing
     * more than one project using the same minecraft version.
     */
    public void addJarProcessor(JarProcessor processor) {
        jarProcessors.add(processor);
    }

    public MappingSet getOrCreateSrcMappingCache(int id, Supplier<MappingSet> factory) {
        return srcMappingCache[id] != null ? srcMappingCache[id] : (srcMappingCache[id] = factory.get());
    }

    public Mercury getOrCreateSrcMercuryCache(int id, Supplier<Mercury> factory) {
        return srcMercuryCache[id] != null ? srcMercuryCache[id] : (srcMercuryCache[id] = factory.get());
    }

    public Dependency officialMojangMappings() {
        return new MojangMappingsDependency(project, this);
    }

    /**
     * @see ConfigurableFileCollection#from(Object...)
     * @deprecated use {@link #getUnmappedModCollection()}{@code .from()} instead
     */
    @Deprecated
    public void addUnmappedMod(Path file) {
        getUnmappedModCollection().from(file);
    }

    /**
     * @deprecated use {@link #getUnmappedModCollection()} instead
     */
    @Deprecated
    public List<Path> getUnmappedMods() {
        return unmappedMods.getFiles().stream()
            .map(File::toPath)
            .collect(Collectors.toList());
    }

    public ConfigurableFileCollection getUnmappedModCollection() {
        return unmappedMods;
    }

    public void setInstallerJson(JsonObject object) {
        this.installerJson = object;
    }

    public JsonObject getInstallerJson() {
        return installerJson;
    }

    public void accessWidener(Object file) {
        this.accessWidener = project.file(file);
    }

    public void enumWidener(String klass) {
        this.enumWidener.add(klass);
    }

    public File getUserCache() {
        File userCache = new File(project.getGradle().getGradleUserHomeDir(), "caches" + File.separator + "fabric-loom");

        if (!userCache.exists()) {
            userCache.mkdirs();
        }

        return userCache;
    }

    public File getRootProjectPersistentCache() {
        File projectCache = new File(project.getRootProject().file(".gradle"), "loom-cache");

        if (!projectCache.exists()) {
            projectCache.mkdirs();
        }

        return projectCache;
    }

    public File getProjectPersistentCache() {
        File projectCache = new File(project.file(".gradle"), "loom-cache");

        if (!projectCache.exists()) {
            projectCache.mkdirs();
        }

        return projectCache;
    }

    public File getRootProjectBuildCache() {
        File projectCache = new File(project.getRootProject().getBuildDir(), "loom-cache");

        if (!projectCache.exists()) {
            projectCache.mkdirs();
        }

        return projectCache;
    }

    public File getProjectBuildCache() {
        File projectCache = new File(project.getBuildDir(), "loom-cache");

        if (!projectCache.exists()) {
            projectCache.mkdirs();
        }

        return projectCache;
    }

    public File getRemappedModCache() {
        File remappedModCache = new File(getRootProjectPersistentCache(), "remapped_mods");

        if (!remappedModCache.exists()) {
            remappedModCache.mkdir();
        }

        return remappedModCache;
    }

    public File getNestedModCache() {
        File nestedModCache = new File(getRootProjectPersistentCache(), "nested_mods");

        if (!nestedModCache.exists()) {
            nestedModCache.mkdir();
        }

        return nestedModCache;
    }

    public File getNativesJarStore() {
        File natives = new File(getUserCache(), "natives/jars");

        if (!natives.exists()) {
            natives.mkdirs();
        }

        return natives;
    }

    public File getNativesDirectory() {
        Object customNativesDir = project.getProperties().get("fabric.loom.natives.dir");

        if (customNativesDir != null) {
            return new File((String) customNativesDir);
        }

        File natives = new File(getUserCache(), "natives/" + getMinecraftProvider().getMinecraftVersion());

        if (!natives.exists()) {
            natives.mkdirs();
        }

        return natives;
    }

    public boolean hasCustomNatives() {
        return project.getProperties().get("fabric.loom.natives.dir") != null;
    }

    public File getDevLauncherConfig() {
        return new File(getProjectPersistentCache(), "launch.cfg");
    }

    @Nullable
    private static Dependency findDependency(Project p, Collection<Configuration> configs, BiPredicate<String, String> groupNameFilter) {
        for (Configuration config : configs) {
            for (Dependency dependency : config.getDependencies()) {
                String group = dependency.getGroup();
                String name = dependency.getName();

                if (groupNameFilter.test(group, name)) {
                    p.getLogger().debug("Loom findDependency found: " + group + ":" + name + ":" + dependency.getVersion());
                    return dependency;
                }
            }
        }

        return null;
    }

    @Nullable
    private <T> T recurseProjects(Function<Project, T> projectTFunction) {
        Project p = this.project;
        T result;

        while (!ProjectHandler.isRootProject(p)) {
            if ((result = projectTFunction.apply(p)) != null) {
                return result;
            }

            p = p.getRootProject();
        }

        result = projectTFunction.apply(p);
        return result;
    }

    @Nullable
    private Dependency getMixinDependency() {
        return recurseProjects((p) -> {
            List<Configuration> configs = new ArrayList<>();
            // check compile classpath first
            Configuration possibleCompileClasspath = p.getConfigurations().findByName("compileClasspath");

            if (possibleCompileClasspath != null) {
                configs.add(possibleCompileClasspath);
            }

            // failing that, buildscript
            configs.addAll(p.getBuildscript().getConfigurations());

            return findDependency(p, configs, (group, name) -> {
                if (name.equalsIgnoreCase("mixin") && group.equalsIgnoreCase("org.spongepowered")) {
                    return true;
                }

                if (name.equalsIgnoreCase("sponge-mixin") && group.equalsIgnoreCase("net.fabricmc")) {
                    return true;
                }

                return false;
            });
        });
    }

    @Nullable
    public String getMixinJsonVersion() {
        Dependency dependency = this.getMixinDependency();

        if (dependency != null) {
            if (dependency.getGroup().equalsIgnoreCase("net.fabricmc")) {
                if (dependency.getVersion().split("\\.").length >= 4) {
                    return dependency.getVersion().substring(0, dependency.getVersion().lastIndexOf('.')) + "-SNAPSHOT";
                }
            }

            return dependency.getVersion();
        }

        return null;
    }

    public String getLoaderLaunchMethod() {
        return this.loaderLaunchMethod != null ? this.loaderLaunchMethod : "";
    }

    public LoomDependencyManager getDependencyManager() {
        return this.dependencyManager;
    }

    public MinecraftProvider getMinecraftProvider() {
        return this.getDependencyManager().getProvider(MinecraftProvider.class);
    }

    public MinecraftMappedProvider getMinecraftMappedProvider() {
        return this.getMappingsProvider().mappedProvider;
    }

    public MappingsProvider getMappingsProvider() {
        return this.getDependencyManager().getProvider(MappingsProvider.class);
    }

    public void setDependencyManager(LoomDependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    public JarProcessorManager getJarProcessorManager() {
        return this.jarProcessorManager;
    }

    public void setJarProcessorManager(JarProcessorManager jarProcessorManager) {
        this.jarProcessorManager = jarProcessorManager;
    }

    public List<JarProcessor> getJarProcessors() {
        return this.jarProcessors;
    }

    public String getRefmapName() {
        if (refmapName == null || refmapName.isEmpty()) {
            String defaultRefmapName = project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName() + "-refmap.json";
            project.getLogger().info("Could not find refmap definition, will be using default name: " + defaultRefmapName);
            refmapName = defaultRefmapName;
        }

        return refmapName;
    }

    public boolean ideSync() {
        return Boolean.parseBoolean(System.getProperty("idea.sync.active", "false"));
    }

    // Ideally this should use maven, but this is a lot easier
    public Function<String, String> getIntermediaryUrl() {
        // Done like this to work around this possibly not being a java string...
        return s -> intermediaryUrl.apply(s).toString();
    }

    public boolean isRootProject() {
        return ProjectHandler.isRootProject(this.project);
    }

    public LoomExtension getRootExtension() {
        if (this.isRootProject()) {
            return this;
        }

        return this.project.getRootProject().getExtensions().getByType(LoomExtension.class);
    }

    public LoomExtension getSharedGradleExtension() {
        return this.shareCaches ? this.getRootExtension() : this;
    }

    // Creates a new file each time its called, this is then held onto later when remapping the output jar
    // Required as now when using parallel builds the old single file could be written by another sourceset compile task
    public synchronized File getNextMixinMappings() {
        File mixinMapping = new File(getProjectBuildCache(), "mixin-map-" + getMinecraftProvider().getMinecraftVersion() + "-" + getMappingsProvider().mappingsVersion + "." + mixinMappings.size() + ".tiny");
        mixinMappings.add(mixinMapping);
        return mixinMapping;
    }

    public Set<File> getAllMixinMappings() {
        return Collections.unmodifiableSet(mixinMappings);
    }
}
