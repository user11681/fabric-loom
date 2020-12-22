package net.fabricmc.loom.extension;

import java.io.File;

public class RunDirectory {
    public final LoomExtension extension;

    public boolean relative = true;
    public boolean shared = true;

    private String path = "run";

    public RunDirectory(LoomExtension extension) {
        this.extension = extension;
    }

    @Override
    public String toString() {
        return this.getFile().toString();
    }

    public void setPath(String path) {
        this.path = path;
    }

    public File getFile() {
        if (this.shared) {
            return new File(this.extension.getUserCache().toString(), "run");
        }

        if (this.relative) {
            return new File(this.extension.project.getRootDir(), this.path);
        }

        return new File(this.path);
    }
}
