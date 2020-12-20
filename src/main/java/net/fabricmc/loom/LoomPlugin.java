package net.fabricmc.loom;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

class LoomPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        new ProjectHandler(project).handle();
    }
}
