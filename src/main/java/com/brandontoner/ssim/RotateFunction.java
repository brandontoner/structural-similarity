package com.brandontoner.ssim;

import java.util.List;

interface RotateFunction {
    /** Does not rotate an image. */
    RotateFunction ROTATE_0 = (width, height, x, y) -> {
        return y * width + x;
    };
    /** Rotates an image 90 degrees clockwise. */
    RotateFunction ROTATE_90_CW = (width, height, x, y) -> {
        int newX = height - 1 - y;
        int newY = x;
        return newY * height + newX;
    };
    /** Rotates an image 180 degrees. */
    RotateFunction ROTATE_180 = (width, height, x, y) -> {
        int newX = width - x - 1;
        int newY = height - y - 1;
        return newY * width + newX;
    };
    /** Rotates an image 270 degrees clockwise. */
    RotateFunction ROTATE_270_CW = (width, height, x, y) -> {
        int newX = y;
        int newY = width - 1 - x;
        return newY * height + newX;
    };
    /** Rotation functions. */
    List<RotateFunction> FUNCTIONS = List.of(ROTATE_0, ROTATE_90_CW, ROTATE_180, ROTATE_270_CW);

    /**
     * Gets the index into an RGB array.
     *
     * @param width  unrotated image width
     * @param height unrotated image height
     * @param x      unrotated image x
     * @param y      unrotated image y
     * @return index into RGB array
     */
    int index(int width, int height, int x, int y);
}
