package com.brandontoner.ssim;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class StructuralSimilarityJava {
    private static final Set<File> kept = new HashSet<>();
    private static final Map<Path, Integer> KEEP_PER_FOLDER = new HashMap<>();
    private static final Path KEEP_PATH = Paths.get("D:\\Users\\brand\\Pictures\\iCloud Photos\\Photos");
    private static final Set<String> EXTENSIONS = Set.of(".jpg", ".jpeg");
    private static final DuplicateHandler DUPLICATE_HANDLER = DuplicateHandler.noop();

    public static void main(String[] args) throws IOException {
        List<InputImage> inputImages = Files.walk(KEEP_PATH)
                                            .filter(Files::isRegularFile)
                                            .filter(v -> EXTENSIONS.contains(getExtension(v)))
                                            .collect(Collectors.toList())
                                            .parallelStream()
                                            .map(InputImage::load)
                                            .flatMap(Collection::parallelStream)
                                            .collect(Collectors.toList());

        Collections.shuffle(inputImages);

        System.err.println("Computing SSIMs");

        List<SSIM> pairs = new ArrayList<>();
        double threshold = 0.99;
        for (int i = 0; i < inputImages.size(); i++) {
            List<SSIM> row = new ArrayList<>(i);
            for (int j = 0; j < i; j++) {
                row.add(new SSIM(inputImages.get(i), inputImages.get(j)));
            }
            row.parallelStream().forEach(SSIM::ssim);
            for (SSIM ssim : row) {
                if (ssim.ssim() >= threshold) {
                    pairs.add(ssim);
                }
            }
        }

        System.err.println("Sorting SSIMs");

        // Compute all the SSIMs in parallel
        pairs.sort(Comparator.comparingDouble(SSIM::ssim).reversed());
        System.err.println("keep delete ssim");

        for (SSIM pair : pairs) {
            InputImage a = pair.one();
            InputImage b = pair.two();
            double ssim = pair.ssim();
            if (ssim < threshold) {
                break;
            }
            if (!rgbClose(a, b)) {
                continue;
            }
            File aFile = a.getFile();
            File bFile = b.getFile();
            if (!(aFile.exists() && bFile.exists())) {
                continue;
            }
            if (kept.contains(aFile) || kept.contains(bFile)) {
                continue;
            }
            final File toKeep;
            final File toDelete;
            if (a.getArea() < b.getArea()) {
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
            System.err.println(toKeep + " " + toDelete + " " + ssim);
            kept.add(toKeep);
            KEEP_PER_FOLDER.merge(toKeep.getParentFile().toPath(), 1, Integer::sum);
            DUPLICATE_HANDLER.handle(toKeep, toDelete);
        }
        KEEP_PER_FOLDER.entrySet().forEach(System.err::println);
    }

    @Nonnull
    private static String getExtension(@Nonnull Path path) {
        String string = path.toString();
        int index = string.lastIndexOf('.');
        return string.substring(index).toLowerCase();
    }

    private static boolean rgbClose(@Nonnull InputImage a, @Nonnull InputImage b) {
        double[] aRgb = a.getRgb();
        double[] bRgb = b.getRgb();
        for (int i = 0; i < aRgb.length; i++) {
            if (Math.abs(aRgb[i] - bRgb[i]) > 2) {
                return false;
            }
        }
        return true;
    }
}
