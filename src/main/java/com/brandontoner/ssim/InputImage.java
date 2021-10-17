package com.brandontoner.ssim;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class InputImage {
    private static final int IMAGE_SIZE = 128;
    @Nonnull
    private final File file;
    @Nonnull
    private final float[] lumas;
    private final float average;
    private final float variance;
    private final int area;
    @Nonnull
    private final double[] rgb;
    private final float[] lumasMinusAverage;

    /**
     * Constructor.
     *
     * @param file     image file
     * @param lumas    lumas of the scaled image
     * @param average  average luma of the scaled image
     * @param area     area of the original image
     * @param variance variance of the lumas of the scaled image
     * @param rgb
     */
    private InputImage(@Nonnull File file,
                       @Nonnull float[] lumas,
                       float average,
                       int area,
                       float variance,
                       double[] rgb) {
        this.file = file;
        this.lumas = lumas;
        this.average = average;
        this.area = area;
        this.variance = variance;
        this.rgb = rgb;
        this.lumasMinusAverage = new float[lumas.length];
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
     * @param rgbArray
     * @return array of lumas
     */
    @Nonnull
    private static float[] getLumas(@Nonnull int[] rgbArray) {
        float[] lumas = new float[rgbArray.length];
        for (int i = 0; i < rgbArray.length; i++) {
            int rgb = rgbArray[i];
            int r = (0x00ff0000 & rgb) >>> 16;
            int g = (0x0000ff00 & rgb) >>> 8;
            int b = (0x000000ff & rgb);
            lumas[i] = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        }
        return lumas;
    }

    @Nonnull
    private static double[] getAverageRGB(@Nonnull int[] rgbArray) {
        double[] out = new double[3];
        for (int i = 0; i < rgbArray.length; i++) {
            int rgb = rgbArray[i];
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
     * @return input image
     */
    @Nonnull
    static List<InputImage> load(@Nonnull Path path) {
        try {
            Logger.log("Loading " + path);
            File file = path.toFile();
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                return List.of();
            }
            List<InputImage> images = new ArrayList<>();
            int width = img.getWidth();
            int height = img.getHeight();
            int[] rgb = img.getRGB(0, 0, width, height, null, 0, width);
            int type = img.getType();
            images.add(getInputImage(file, img));
            images.add(getInputImage(file, rotate90cw(width, height, rgb, type)));
            images.add(getInputImage(file, rotate180(width, height, rgb, type)));
            images.add(getInputImage(file, rotate270cw(width, height, rgb, type)));
            return List.copyOf(images);
        } catch (IOException e) {
            new UncheckedIOException(path + " " + e.getMessage(), e).printStackTrace();
            return List.of();
        }
    }

    @Nonnull
    private static InputImage getInputImage(File file, BufferedImage rotated) {
        BufferedImage scaled = scaleImage(rotated);
        int[] rgbArray = scaled.getRGB(0, 0, scaled.getWidth(), scaled.getHeight(), null, 0, scaled.getWidth());
        double[] rgb = getAverageRGB(rgbArray);
        float[] lumas = getLumas(rgbArray);
        float average = average(lumas);
        return new InputImage(file,
                              lumas,
                              average,
                              rotated.getWidth() * rotated.getHeight(),
                              variance(lumas, average),
                              rgb);
    }

    @Nonnull
    private static BufferedImage rotate(@Nonnull BufferedImage img, @Nonnull String orientation) {
        int width = img.getWidth();
        int height = img.getHeight();
        int[] rgb = img.getRGB(0, 0, width, height, null, 0, width);
        int type = img.getType();
        switch (orientation) {
            case "Top, left side (Horizontal / normal)":
            case "Unknown (0)":
                return img;
            case "Right side, top (Rotate 90 CW)":
                return rotate90cw(width, height, rgb, type);
            case "Bottom, right side (Rotate 180)":
                return rotate180(width, height, rgb, type);
            case "Left side, bottom (Rotate 270 CW)":
                return rotate270cw(width, height, rgb, type);
            default:
                throw new IllegalArgumentException(orientation);
        }
    }

    @Nonnull
    private static BufferedImage rotate90cw(int width, int height, int[] rgbIn, int type) {
        int[] rgbOut = new int[rgbIn.length];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                rgbOut[(x + 1) * height - 1 - y] = rgbIn[y * width + x];
            }
        }
        BufferedImage newImage = new BufferedImage(height, width, type);
        newImage.setRGB(0, 0, height, width, rgbOut, 0, height);
        return newImage;
    }

    @Nonnull
    private static BufferedImage rotate180(int width, int height, int[] rgbIn, int type) {
        int[] rgbOut = new int[rgbIn.length];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                rgbOut[(height - y) * width - 1 - x] = rgbIn[y * width + x];
            }
        }
        BufferedImage newImage = new BufferedImage(width, height, type);
        newImage.setRGB(0, 0, width, height, rgbOut, 0, width);
        return newImage;
    }

    @Nonnull
    private static BufferedImage rotate270cw(int width, int height, int[] rgbIn, int type) {
        int[] rgbOut = new int[rgbIn.length];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                rgbOut[(width - 1 - x) * height + y] = rgbIn[y * width + x];
            }
        }
        BufferedImage newImage = new BufferedImage(height, width, type);
        newImage.setRGB(0, 0, height, width, rgbOut, 0, height);
        return newImage;
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
     * @return lumas of the scaled image
     */
    @Nonnull
    float[] getLumas() {
        return lumas;
    }

    /**
     * @return lumas of the scaled image
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

    double[] getRgb() {
        return rgb;
    }

    @Nonnull
    @Override
    public String toString() {
        return "InputImage{" + "file=" + file + '}';
    }
}
