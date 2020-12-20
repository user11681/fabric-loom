package net.fabricmc.loom.dependency.configuration;

import java.util.Map;

import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import user11681.reflect.Classes;

public class BloatedDependencySet extends DefaultDependencySet {
	public static final long classPointer = Classes.getClassPointer(BloatedDependencySet.class);

	public BloatedDependencySet(Describable displayName, Configuration clientConfiguration, DomainObjectSet<Dependency> backingSet) {
        super(displayName, clientConfiguration, backingSet);
    }

    @Override
    public boolean add(Dependency dependency) {
        if (dependency instanceof ModuleDependency) {
            ((ModuleDependency) dependency).exclude(Map.of("module", "fabric-api"));
        }

        return super.add(dependency);
    }
}
