package net.fabricmc.loom.dependency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DependencyContainer {
    public final List<DependencyEntry> entries;

    public DependencyContainer(DependencyEntry... entries) {
        this.entries = new ArrayList<>(Arrays.asList(entries));
    }

    public DependencyEntry entry(String key) {
        for (DependencyEntry entry : this.entries) {
            if (entry.artifact.equals(key) || entry.key().contains(key)) {
                return entry;
            }
        }

        return null;
    }

    public void entry(DependencyEntry entry) {
        this.entries.add(entry);
    }
}
