package ua.abovsh.svitlo;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowNativeCanvas;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;
import org.robolectric.util.reflector.Reflector;

/** Records the geometry sent to Skia, while retaining the real bitmap drawing path. */
@Implements(value = Canvas.class, isInAndroidSdk = false, callNativeMethodsByDefault = true)
public class CanvasGeometry extends ShadowNativeCanvas {
    record Text(String value, RectF ink, float baseline, int color, float strokeWidth, int order) {}
    interface BulbStroke { void draw(Canvas canvas); }
    record Shape(RectF bounds, int color, int order) {}

    static final class Frame {
        int order;
        final int width;
        final int height;
        final List<Text> text = new ArrayList<>();
        final List<Shape> rectangles = new ArrayList<>();
        final List<Shape> lines = new ArrayList<>();
        final List<BulbStroke> bulb = new ArrayList<>();

        Frame(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final ThreadLocal<List<Frame>> recording = new ThreadLocal<>();

    @RealObject private Canvas canvas;
    private Frame frame;

    static void start() {
        recording.set(new ArrayList<>());
    }

    static List<Frame> stop() {
        List<Frame> frames = recording.get();
        recording.remove();
        return frames;
    }

    private Frame frame() {
        if (frame == null) {
            frame = new Frame(canvas.getWidth(), canvas.getHeight());
            recording.get().add(frame);
        }
        return frame;
    }

    private static boolean drawingBulb() {
        return StackWalker.getInstance().walk(frames -> frames
                .anyMatch(call -> call.getClassName().equals(BulbView.class.getName())));
    }

    @ForType(Canvas.class)
    interface RealCanvas {
        @Direct void drawText(String value, float x, float y, Paint paint);
        @Direct void drawRoundRect(RectF r, float rx, float ry, Paint paint);
        @Direct void drawPath(Path path, Paint paint);
        @Direct void drawRect(float left, float top, float right, float bottom, Paint paint);
        @Direct void drawLine(float x, float y, float toX, float toY, Paint paint);
    }

    @Implementation
    protected void drawText(String value, float x, float y, Paint paint) {
        if (recording.get() != null && !value.isBlank()) {
            Rect glyph = new Rect();
            paint.getTextBounds(value, 0, value.length(), glyph);
            float advance = paint.measureText(value);
            float origin = switch (paint.getTextAlign()) {
                case CENTER -> x - advance / 2;
                case RIGHT -> x - advance;
                default -> x;
            };
            RectF ink = new RectF(glyph);
            ink.offset(origin, y);
            frame().text.add(new Text(value, ink, y, paint.getColor(),
                    paint.getStyle() == Paint.Style.FILL ? 0 : paint.getStrokeWidth(), frame().order++));
        }
        Reflector.reflector(RealCanvas.class, canvas).drawText(value, x, y, paint);
    }

    @Implementation
    protected void drawRoundRect(RectF r, float rx, float ry, Paint paint) {
        if (recording.get() != null) {
            frame().rectangles.add(new Shape(new RectF(r), paint.getColor(), frame().order++));
            if (drawingBulb()) {
                RectF copy = new RectF(r);
                Paint ink = new Paint(paint);
                frame().bulb.add(target -> target.drawRoundRect(copy, rx, ry, ink));
            }
        }
        Reflector.reflector(RealCanvas.class, canvas).drawRoundRect(r, rx, ry, paint);
    }

    @Implementation
    protected void drawPath(Path path, Paint paint) {
        if (recording.get() != null && drawingBulb()) {
            Path copy = new Path(path);
            Paint ink = new Paint(paint);
            frame().bulb.add(target -> target.drawPath(copy, ink));
        }
        Reflector.reflector(RealCanvas.class, canvas).drawPath(path, paint);
    }

    @Implementation
    protected void drawRect(float left, float top, float right, float bottom, Paint paint) {
        if (recording.get() != null) {
            frame().rectangles.add(new Shape(new RectF(left, top, right, bottom), paint.getColor(), frame().order++));
        }
        Reflector.reflector(RealCanvas.class, canvas).drawRect(left, top, right, bottom, paint);
    }

    @Implementation
    protected void drawLine(float x, float y, float toX, float toY, Paint paint) {
        if (recording.get() != null) {
            float half = paint.getStrokeWidth() / 2;
            RectF bounds = new RectF(Math.min(x, toX) - half, Math.min(y, toY) - half,
                    Math.max(x, toX) + half, Math.max(y, toY) + half);
            frame().lines.add(new Shape(bounds, paint.getColor(), frame().order++));
            if (drawingBulb()) {
                Paint ink = new Paint(paint);
                frame().bulb.add(target -> target.drawLine(x, y, toX, toY, ink));
            }
        }
        Reflector.reflector(RealCanvas.class, canvas).drawLine(x, y, toX, toY, paint);
    }
}
