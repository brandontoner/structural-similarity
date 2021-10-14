package com.brandontoner.ssim;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

final class SSIM {
    private static final double k1 = 0.01;
    private static final double k2 = 0.03;
    private static final double L = 255;
    private static final double c1 = (k1 * L) * (k1 * L);
    private static final double c2 = (k2 * L) * (k2 * L);
    @Nonnull private final InputImage img1;
    @Nonnull private final InputImage img2;
    @CheckForNull private volatile Double ssim;

    /**
     * Constructor.
     *
     * @param img1 first image
     * @param img2 second image
     */
    SSIM(@Nonnull InputImage img1, @Nonnull InputImage img2) {
        this.img1 = img1;
        this.img2 = img2;
    }

    /**
     * @return the first image
     */
    @Nonnull
    InputImage one() {
        return img1;
    }

    /**
     * @return the second image
     */
    @Nonnull
    InputImage two() {
        return img2;
    }

    /**
     * @return the SSIM of the two images
     */
    double ssim() {
        if (ssim == null) {
            float average1 = img1.getAverage();
            float average2 = img2.getAverage();
            float[] lumas1 = img1.getLumas();
            float[] lumas2 = img2.getLumas();

            float covariance1 = 0;
            for (int i = 0; i < lumas1.length; ++i) {
                float d1 = lumas1[i] - average1;
                float d2 = lumas2[i] - average2;
                covariance1 += d1 * d2;
            }
            covariance1 /= lumas1.length;
            float covariance = covariance1;

            float var1 = img1.getVariance();
            float var2 = img2.getVariance();
            ssim =
                    (2 * average1 * average2 + c1) * (2 * covariance + c2) / ((average1 * average1 + average2 * average2 + c1) * (var1 + var2 + c2));
        }
        return ssim;
    }

    @Nonnull
    @Override
    public String toString() {
        return "SSIM{" + "img1=" + img1 + ", img2=" + img2 + ", ssim=" + ssim + '}';
    }
}
