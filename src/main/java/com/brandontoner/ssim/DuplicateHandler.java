package com.brandontoner.ssim;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public interface DuplicateHandler {
    void handle(@Nonnull File toKeep, @Nonnull File toDelete) throws IOException;

    static DuplicateHandler noop() {
        return (toKeep, toDelete) -> {
        };
    }

    static DuplicateHandler rename() {
        return (toKeep, toDelete) -> {
            int i = 1;
            File output;
            String name = toKeep.getName();
            int idx = name.lastIndexOf('.');
            String noExt = name.substring(0, idx);
            String ext = name.substring(idx);
            do {
                String child = "%s delete (%d)%s".formatted(noExt, i, ext);
                output = new File(toKeep.getParent(), child);
                ++i;
            } while (output.exists());
            System.err.println("Moving " + toDelete.getAbsolutePath() + " to " + output.getAbsolutePath());
            Files.move(toDelete.toPath(), output.toPath());
        };
    }

    static DuplicateHandler delete() {
        return (toKeep, toDelete) -> {
            System.err.println("Deleting " + toDelete.getAbsolutePath());
            toDelete.delete();
        };
    }
}
