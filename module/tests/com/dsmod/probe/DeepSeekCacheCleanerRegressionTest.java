package com.dsmod.probe;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

public final class DeepSeekCacheCleanerRegressionTest {
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("deekseep-cache-cleaner-test").toFile();
        try {
            long now = System.currentTimeMillis();
            File oldCoil = file(root, "coil3_disk_cache/old.bin", 100, now - 10L * 86400000L);
            File freshCoil = file(root, "coil3_disk_cache/fresh.bin", 200, now - 86400000L);
            File oldImage = file(root, "image_cache/standard/old.img", 300,
                    now - 31L * 86400000L);
            File captured = file(root, "captured/editor-image.png", 400,
                    now - 31L * 86400000L);
            File unrelated = file(root, "network/old.tmp", 500, now - 31L * 86400000L);

            DeepSeekCacheCleaner.Result result = DeepSeekCacheCleaner.clean(root, 7, now);
            require(!oldCoil.exists(), "old Coil cache was retained");
            require(!oldImage.exists(), "old DeepSeek image cache was retained");
            require(freshCoil.isFile(), "fresh cache was deleted");
            require(captured.isFile(), "editor captured image was deleted");
            require(unrelated.isFile(), "unverified cache directory was modified");
            require(result.files == 2, "unexpected deleted file count " + result.files);
            require(result.bytes == 400L, "unexpected deleted bytes " + result.bytes);
            System.out.println("DeepSeek cache cleaner regression passed");
        } finally {
            deleteTree(root);
        }
    }

    private static File file(File root, String relative, int size, long modified)
            throws Exception {
        File file = new File(root, relative);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("cannot create " + parent);
        }
        FileOutputStream output = new FileOutputStream(file);
        output.write(new byte[size]);
        output.close();
        if (!file.setLastModified(modified)) throw new IllegalStateException("setLastModified");
        return file;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
