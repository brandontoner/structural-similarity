package com.brandontoner.ssim;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StructuralSimilarity {
    private final double threshold;
    private final Set<Path> deleteFolders;
    private final Set<Path> keepFolders;
    private final DuplicateHandler duplicateHandler;
    private final Set<String> extensions;
    private final Set<File> kept = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private StructuralSimilarity(@Nonnull Builder builder) {
        this.threshold = builder.threshold;
        this.deleteFolders = Set.copyOf(builder.deleteFolders);
        this.keepFolders = Set.copyOf(builder.keepFolders);
        this.duplicateHandler = builder.duplicateHandler;
        this.extensions = Set.copyOf(builder.extensions);
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    public void run() throws IOException {
        Set<Path> deletePaths = deleteFolders.parallelStream()
                                             .flatMap(StructuralSimilarity::walk)
                                             .filter(Files::isRegularFile)
                                             .filter(v -> extensions.contains(getExtension(v)))
                                             .collect(Collectors.toUnmodifiableSet());
        List<InputImage> deleteImages = deletePaths.parallelStream()
                                                   .map(path -> InputImage.load(path, false))
                                                   .flatMap(Collection::parallelStream)
                                                   .toList();

        Set<Path> keepPaths = keepFolders.parallelStream()
                                         .flatMap(StructuralSimilarity::walk)
                                         .filter(Files::isRegularFile)
                                         .filter(v -> extensions.contains(getExtension(v)))
                                         .collect(Collectors.toUnmodifiableSet());
        List<InputImage> keepImages = keepPaths.parallelStream()
                                               .map(path -> InputImage.load(path, true))
                                               .flatMap(Collection::parallelStream)
                                               .toList();


        Logger.log("Computing SSIMs");

        Stream<SSIM> keepAndDelete = keepImages.parallelStream().flatMap(keepImage -> {
            return deleteImages.parallelStream().map(deleteImage -> new SSIM(keepImage, deleteImage));
        });
        Stream<SSIM> deleteAndDelete = IntStream.range(0, deleteImages.size()).parallel().mapToObj(i -> {
            InputImage imgi = deleteImages.get(i);
            return IntStream.range(0, i)
                            .parallel()
                            .mapToObj(j -> new SSIM(imgi, deleteImages.get(j)));
        }).flatMap(Function.identity());

        List<SSIM> pairs = Stream.concat(keepAndDelete, deleteAndDelete)
                                 .filter(ssim -> ssim.ssim() >= threshold)
                                 .sorted(Comparator.comparingDouble(SSIM::ssim).reversed())
                                 .toList();

        System.out.println("keep\tdelete\tssim");

        Map<Path, Integer> keepPerFolder = new HashMap<>();
        for (SSIM pair : pairs) {
            InputImage a = pair.one();
            InputImage b = pair.two();
            double ssim = pair.ssim();
            if (!isRgbClose(a, b)) {
                continue;
            }
            File aFile = a.getFile();
            File bFile = b.getFile();
            if (!(aFile.exists() && bFile.exists())) {
                continue;
            }
            // TODO make this threadsafe
            if (kept.contains(aFile) || kept.contains(bFile)) {
                continue;
            }
            File toKeep;
            File toDelete;
            if (a.isKeep()) {
                toKeep = aFile;
                toDelete = bFile;
            } else if (b.isKeep()) {
                toKeep = bFile;
                toDelete = aFile;
            } else if (a.getArea() < b.getArea()) {
                toKeep = bFile;
                toDelete = aFile;
            } else if (a.getArea() > b.getArea()) {
                toKeep = aFile;
                toDelete = bFile;
            } else if (aFile.length() < bFile.length()) {
                toKeep = bFile;
                toDelete = aFile;
            } else if (aFile.length() > bFile.length()) {
                toKeep = aFile;
                toDelete = bFile;
            } else {
                toKeep = aFile;
                toDelete = bFile;
            }

            System.out.println(toKeep + "\t" + toDelete + "\t" + ssim);
            kept.add(toKeep);
            keepPerFolder.merge(toKeep.getParentFile().toPath(), 1, Integer::sum);
            duplicateHandler.handle(toKeep, toDelete);
        }
        keepPerFolder.entrySet().forEach(System.err::println);
    }

    private static Stream<Path> walk(Path path) {
        try {
            return Files.walk(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Nonnull
    private static String getExtension(@Nonnull Path path) {
        String string = path.toString();
        int index = string.lastIndexOf('.');
        return string.substring(index).toLowerCase();
    }

    private static boolean isRgbClose(@Nonnull InputImage a, @Nonnull InputImage b) {
        double[] aRgb = a.getRgb();
        double[] bRgb = b.getRgb();
        for (int i = 0; i < aRgb.length; i++) {
            if (Math.abs(aRgb[i] - bRgb[i]) > 2) {
                return false;
            }
        }
        return true;
    }

    public static class Builder {
        private double threshold = 1.0;
        private final Set<Path> keepFolders = new HashSet<>();
        private final Set<Path> deleteFolders = new HashSet<>();
        private DuplicateHandler duplicateHandler = DuplicateHandler.noop();
        private final Set<String> extensions = Set.of(".jpg", ".jpeg");

        /** Prevent instantiation. */
        private Builder() {
        }

        /**
         * Sets the SSIM threshold above which images are classified as duplicates. Defaults to {@code 1.0}.
         *
         * @param v new threshold
         * @return build with threshold set
         */
        @Nonnull
        public Builder withThreshold(double v) {
            this.threshold = v;
            return this;
        }

        /**
         * Adds a folder in which images are eligible to be deleted. This folder will be added to the set of previously
         * added folders. The recursive contents of the folder will be used.
         *
         * @param s non-null path
         * @return builder with delete folder added
         */
        @Nonnull
        public Builder withDeleteFolder(@Nonnull String s) {
            return withDeleteFolder(Path.of(s));
        }

        /**
         * Adds a folder in which images are eligible to be deleted. This folder will be added to the set of previously
         * added folders. The recursive contents of the folder will be used.
         *
         * @param path non-null path
         * @return builder with delete folder added
         */
        @Nonnull
        private Builder withDeleteFolder(@Nonnull Path path) {
            this.deleteFolders.add(path.toAbsolutePath());
            return this;
        }

        /**
         * Adds a folder in which images will be used to find duplicates, but are not eligible to be deleted. This
         * folder will be added to the set of previously added folders. The recursive contents of the folder will be
         * used.
         *
         * @param s non-null path
         * @return builder with delete folder added
         */
        @Nonnull
        public Builder withKeepFolder(@Nonnull String s) {
            return withKeepFolder(Path.of(s));
        }

        /**
         * Adds a folder in which images will be used to find duplicates, but are not eligible to be deleted. This
         * folder will be added to the set of previously added folders. The recursive contents of the folder will be
         * used.
         *
         * @param path non-null path
         * @return builder with delete folder added
         */
        @Nonnull
        private Builder withKeepFolder(@Nonnull Path path) {
            this.keepFolders.add(path.toAbsolutePath());
            return this;
        }

        /**
         * Sets the duplicate handler to run for all detected duplicates.
         *
         * @param duplicateHandler duplicate handler to use
         * @return builder with duplicate handler set
         */
        @Nonnull
        public Builder withDuplicateHandler(DuplicateHandler duplicateHandler) {
            this.duplicateHandler = Objects.requireNonNull(duplicateHandler);
            return this;
        }

        /**
         * Builds the Structural Similarity runner.
         *
         * @return Structural Similarity runner.
         */
        @Nonnull
        public StructuralSimilarity build() {
            return new StructuralSimilarity(this);
        }
    }
}
