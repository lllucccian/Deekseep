package com.dsmod.probe;

import java.util.Arrays;

public final class ImageCutoutRegressionTest {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF101010;
    private static final int RED = 0xFFE03030;
    private static final int PALE = 0xFFF0F0F0;
    private static final int BLUE = 0xFF3977DF;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static int alpha(byte value) {
        return value & 0xFF;
    }

    private static void testEdgeConnectedRemoval() {
        int width = 11;
        int height = 11;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, WHITE);
        for (int y = 3; y <= 7; y++) {
            for (int x = 3; x <= 7; x++) pixels[y * width + x] = BLACK;
        }
        byte[] mask = ImageCutoutUi.detectBackgroundMask(pixels, width, height);
        check(alpha(mask[0]) == 0, "border background should be removed");
        check(alpha(mask[5 * width + 5]) == 255,
                "center foreground should be retained");
    }

    private static void testEnclosedMatchingColorIsKept() {
        int width = 13;
        int height = 13;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, WHITE);
        for (int y = 2; y <= 10; y++) {
            for (int x = 2; x <= 10; x++) pixels[y * width + x] = BLACK;
        }
        pixels[6 * width + 6] = WHITE;
        byte[] mask = ImageCutoutUi.detectBackgroundMask(pixels, width, height);
        check(alpha(mask[6 * width + 6]) == 255,
                "a matching color enclosed by foreground must not be removed");
    }

    private static void testDistinctForegroundTouchingEdgeSurvives() {
        int width = 15;
        int height = 15;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, WHITE);
        for (int y = 0; y < 8; y++) {
            pixels[y * width + 7] = RED;
            if (y > 1) {
                pixels[y * width + 6] = RED;
                pixels[y * width + 8] = RED;
            }
        }
        byte[] mask = ImageCutoutUi.detectBackgroundMask(pixels, width, height);
        check(alpha(mask[7]) > 200,
                "distinct foreground touching one edge should not become a seed");
        check(alpha(mask[0]) == 0,
                "dominant border background should still be removed");
    }

    private static void testLargeForegroundTouchingEdgeIsNotPaletteBackground() {
        int width = 21;
        int height = 21;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, WHITE);
        for (int y = 3; y <= 17; y++) {
            for (int x = 0; x <= 5; x++) {
                pixels[y * width + x] = RED;
            }
        }
        byte[] mask = ImageCutoutUi.detectBackgroundMask(pixels, width, height);
        check(alpha(mask[10 * width]) > 220,
                "a frequent foreground color touching an edge must not enter the background palette");
        check(alpha(mask[width - 1]) == 0,
                "the actual dominant border background should still be removed");
    }

    private static void testNarrowOutlineGapDoesNotDrainSubjectColors() {
        int width = 23;
        int height = 23;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, WHITE);
        for (int x = 5; x <= 17; x++) {
            pixels[5 * width + x] = BLACK;
            pixels[17 * width + x] = BLACK;
        }
        for (int y = 5; y <= 17; y++) {
            pixels[y * width + 5] = BLACK;
            pixels[y * width + 17] = BLACK;
        }
        // A one-pixel opening models anti-aliased or incomplete outlines in imported stickers.
        pixels[5 * width + 11] = WHITE;
        for (int y = 6; y < 17; y++) {
            for (int x = 6; x < 17; x++) {
                pixels[y * width + x] = PALE;
            }
        }
        for (int y = 9; y <= 14; y++) {
            for (int x = 9; x <= 14; x++) {
                pixels[y * width + x] = BLUE;
            }
        }
        byte[] mask = ImageCutoutUi.detectBackgroundMask(pixels, width, height);
        check(alpha(mask[6 * width + 11]) > 220,
                "a pale subject must not be drained through a narrow outline gap");
        check(alpha(mask[11 * width + 11]) == 255,
                "interior subject colors must remain fully opaque");
        check(alpha(mask[0]) == 0,
                "outside white background should be removed");
    }

    private static void testSpatialPlanesFillTheRevealedHole() {
        int width = 17;
        int height = 17;
        int[] pixels = new int[width * height];
        byte[] keepMask = new byte[pixels.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int blue = 150 + y * 3;
                pixels[y * width + x] =
                        0xFF000000 | (90 << 16) | (125 << 8) | blue;
            }
        }
        for (int y = 6; y <= 10; y++) {
            for (int x = 6; x <= 10; x++) {
                int index = y * width + x;
                pixels[index] = RED;
                keepMask[index] = (byte) 0xFF;
            }
        }

        int[] middle = SpatialLayerCache.buildMidgroundPixels(
                pixels, keepMask);
        int[] back = SpatialLayerCache.buildBackplatePixels(
                pixels, keepMask, width, height);
        int center = 8 * width + 8;
        check((middle[0] >>> 24) == 0
                        && (middle[center] >>> 24) == 255,
                "midground plane should contain only the cut-out subject");
        check(back[center] != RED
                        && ((back[center] >>> 16) & 0xFF) < 120
                        && (back[center] & 0xFF) > 150,
                "backplate must reconstruct background beneath the subject instead of a hole");
        check(back[0] == pixels[0],
                "known background pixels must remain byte-for-byte unchanged");
    }

    public static void main(String[] args) {
        testEdgeConnectedRemoval();
        testEnclosedMatchingColorIsKept();
        testDistinctForegroundTouchingEdgeSurvives();
        testLargeForegroundTouchingEdgeIsNotPaletteBackground();
        testNarrowOutlineGapDoesNotDrainSubjectColors();
        testSpatialPlanesFillTheRevealedHole();
        System.out.println("Image cutout regression tests passed");
    }
}
