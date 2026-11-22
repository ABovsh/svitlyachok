package ua.abovsh.svitlo;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class GeometryEvidence {
    private GeometryEvidence() {}

    static boolean enabled() {
        return System.getenv("SVITLO_GEOMETRY_EVIDENCE") != null;
    }

    static void saveView(String relativePath, View view) {
        if (!enabled()) return;
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(image));
            save(relativePath, image);
        } finally {
            image.recycle();
        }
    }

    static void save(String relativePath, Bitmap image) {
        if (!enabled()) return;
        Path path = Path.of(System.getenv("SVITLO_GEOMETRY_EVIDENCE")).resolve(relativePath);
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                assertTrue("PNG encoding " + path, image.compress(Bitmap.CompressFormat.PNG, 100, out));
            }
        } catch (IOException error) {
            throw new AssertionError("Cannot save " + path, error);
        }
    }
}
