package com.brandontoner.ssim;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

final class InputImage {
    private static final int IMAGE_SIZE = 128;
    @Nonnull
    private final File file;
    private final float average;
    private final float variance;
    private final int area;
    @Nonnull
    private final double[] rgb;
    @Nonnull
    private final float[] lumasMinusAverage;
    private final boolean isKeep;

    /**
     * Constructor.
     *
     * @param file     image file
     * @param lumas    lumas of the scaled image
     * @param average  average luma of the scaled image
     * @param area     area of the original image
     * @param variance variance of the lumas of the scaled image
     * @param rgb
     * @param isKeep
     */
    private InputImage(@Nonnull File file,
                       @Nonnull float[] lumas,
                       float average,
                       int area,
                       float variance,
                       @Nonnull double[] rgb,
                       boolean isKeep) {
        this.file = file;
        this.average = average;
        this.area = area;
        this.variance = variance;
        this.rgb = rgb;
        this.lumasMinusAverage = new float[lumas.length];
        this.isKeep = isKeep;
        for (int i = 0; i < lumas.length; i++) {
            lumasMinusAverage[i] = lumas[i] - average;
        }
    }

    /**
     * Computes the average of an array of floats.
     *
     * @param floats array of floats
     * @return average
     */
    private static float average(@Nonnull float[] floats) {
        float v = 0;
        for (float luma : floats) {
            v += luma;
        }
        return v / floats.length;
    }

    /**
     * Gets the luma values for an image.
     *
     * @param width          image width
     * @param height         image height
     * @param rgbArray       rgb array
     * @param rotateFunction function used to rotate the image
     * @return array of lumas
     */
    @Nonnull
    private static float[] getLumas(int width, int height, @Nonnull int[] rgbArray, @Nonnull RotateFunction rotateFunction) {
        float[] lumas = new float[rgbArray.length];
        int i = 0;
        for (int x = 0; x < width; ++x) {
            for (int y = 0; y < height; ++y) {
                int rgb = rgbArray[rotateFunction.index(width, height, x, y)];
                int r = (0x00ff0000 & rgb) >>> 16;
                int g = (0x0000ff00 & rgb) >>> 8;
                int b = (0x000000ff & rgb);
                lumas[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                i++;
            }
        }
        return lumas;
    }

    @Nonnull
    private static double[] getAverageRGB(@Nonnull int[] rgbArray) {
        double[] out = new double[3];
        for (int rgb : rgbArray) {
            int r = (0x00ff0000 & rgb) >>> 16;
            int g = (0x0000ff00 & rgb) >>> 8;
            int b = (0x000000ff & rgb);
            out[0] += r;
            out[1] += g;
            out[2] += b;
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= rgbArray.length;
        }
        return out;
    }

    /**
     * Scales an image to {@value IMAGE_SIZE}x{@value IMAGE_SIZE} pixels.
     *
     * @param image image to scale
     * @return scaled image
     */
    @Nonnull
    private static BufferedImage scaleImage(@Nonnull BufferedImage image) {
        BufferedImage output = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = output.getGraphics();
        g.drawImage(image, 0, 0, IMAGE_SIZE, IMAGE_SIZE, null);
        g.dispose();
        return output;
    }

    /**
     * Loads an input image from a file.
     *
     * @param path input image path
     * @param isKeep
     * @return input image
     */
    @Nonnull
    static List<InputImage> load(@Nonnull Path path, boolean isKeep) {
        try {
            Logger.log("Loading " + path);
            File file = path.toFile();
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                return List.of();
            }
            BufferedImage scaled = scaleImage(img);
            int width = scaled.getWidth();
            int height = scaled.getHeight();
            int[] rgb = scaled.getRGB(0, 0, width, height, null, 0, width);

            // Rotate the image to find the canonical orientation. Doesn't matter what it is, as long as all duplicates
            // would have the same canonical orientation.
            float[] lumas = null;
            for (RotateFunction function : RotateFunction.FUNCTIONS) {
                float[] lumas1 = getLumas(width, height, rgb, function);
                if (lumas == null || compare(lumas1, lumas) < 0) {
                    lumas = lumas1;
                }
            }

            float average = average(lumas);
            return List.of(new InputImage(file,
                                          lumas,
                                          average,
                                          img.getWidth() * img.getHeight(),
                                          variance(lumas, average),
                                          getAverageRGB(rgb),
                                          isKeep));
        } catch (IOException e) {
            new UncheckedIOException(path + " " + e.getMessage(), e).printStackTrace();
            return List.of();
        }
    }

    private static int compare(@Nonnull float[] a, float[] b) {
        float aSum = 0;
        float bSum = 0;
        for (int i = 0; i < a.length && i < 4; i++) {
            aSum += a[i];
            bSum += b[i];
        }
        return Float.compare(aSum, bSum);
    }

    /**
     * Computes the variance of an array.
     *
     * @param floats  array
     * @param average average of the array
     * @return variance of the array
     */
    private static float variance(@Nonnull float[] floats, float average) {
        float variance = 0;
        for (float luma : floats) {
            float d1 = luma - average;
            variance += d1 * d1;
        }
        return variance / floats.length;
    }

    /**
     * @return number of pixels in the input image
     */
    int getArea() {
        return area;
    }

    /**
     * @return average of the lumas of the scaled image
     */
    float getAverage() {
        return average;
    }

    /**
     * @return image file
     */
    @Nonnull
    File getFile() {
        return file;
    }

    /**
     * @return an array of {@code lumas[i] - getAverage()}
     */
    @Nonnull
    float[] getLumasMinusAverage() {
        return lumasMinusAverage;
    }

    /**
     * @return variance of the lumas of the scaled image
     */
    float getVariance() {
        return variance;
    }

    @Nonnull
    double[] getRgb() {
        return rgb;
    }

    @Nonnull
    @Override
    public String toString() {
        return "InputImage{" + "file=" + file + '}';
    }

    boolean isKeep() {
        return isKeep;
    }
}
