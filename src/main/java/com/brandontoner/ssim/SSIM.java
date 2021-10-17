package com.brandontoner.ssim;

import javax.annotation.Nonnull;

final class SSIM {
    private static final double k1 = 0.01;
    private static final double k2 = 0.03;
    private static final double L = 255;
    private static final double c1 = (k1 * L) * (k1 * L);
    private static final double c2 = (k2 * L) * (k2 * L);
    @Nonnull private final InputImage img1;
    @Nonnull private final InputImage img2;
    private final double ssim;

    /**
     * Constructor.
     *
     * @param img1 first image
     * @param img2 second image
     */
    SSIM(@Nonnull InputImage img1, @Nonnull InputImage img2) {
        this.img1 = img1;
        this.img2 = img2;

        float average1 = this.img1.getAverage();
        float average2 = this.img2.getAverage();
        float[] lumas1 = this.img1.getLumasMinusAverage();
        float[] lumas2 = this.img2.getLumasMinusAverage();

        float covariance = 0;
        int length = lumas1.length;
        for (int i = 0; i < length; ++i) {
            covariance += lumas1[i] * lumas2[i];
        }
        covariance /= length;

        float var1 = this.img1.getVariance();
        float var2 = this.img2.getVariance();
        ssim =
                (2 * average1 * average2 + c1) * (2 * covariance + c2) / ((average1 * average1 + average2 * average2 + c1) * (var1 + var2 + c2));
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
        return ssim;
    }

    @Nonnull
    @Override
    public String toString() {
        return "SSIM{" + "img1=" + img1 + ", img2=" + img2 + ", ssim=" + ssim + '}';
    }
}
