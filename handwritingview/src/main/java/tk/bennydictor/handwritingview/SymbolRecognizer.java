package tk.bennydictor.handwritingview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SymbolRecognizer {
    private static final int BITMAP_SIZE = 28;
    private static SymbolRecognizer instance;
    private MultiLayerNetwork ann;

    private static CharSequence[] symbols = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
    };

    private SymbolRecognizer(Context context) {
        try {
            ann = ModelSerializer.restoreMultiLayerNetwork(context.getResources().openRawResource(R.raw.ann));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void init(Context context) {
        instance = new SymbolRecognizer(context);
    }

    static SymbolRecognizer get() {
        return instance;
    }


    private boolean pathsIntersect(Path a, Path b) {
        Path c = new Path(a);
        if (!c.op(b, Path.Op.INTERSECT))
            return false;

        RectF bounds = new RectF();
        c.computeBounds(bounds, false);
        return !(bounds.bottom == 0 && bounds.left == 0 && bounds.right == 0 && bounds.top == 0);
    }

    private boolean[][] connectivity(List<Path> paths) {
        boolean[][] ret = new boolean[paths.size()][paths.size()];
        for (int i = 0; i < paths.size(); ++i)
            for (int j = 0; j < i; ++j)
                ret[i][j] = ret[j][i] =
                        pathsIntersect(paths.get(i), paths.get(j));
        return ret;
    }

    private void componentsDfs(int[] components, boolean[][] graph, int v, int c) {
        components[v] = c;
        for (int i = 0; i < components.length; ++i)
            if (graph[v][i] && components[i] == -1)
                componentsDfs(components, graph, i, c);
    }

    private int components(List<Path> paths, int[] components) {
        boolean[][] graph = connectivity(paths);
        Arrays.fill(components, -1);
        int curComponent = 0;

        for (int i = 0; i < paths.size(); ++i)
            if (components[i] == -1)
                componentsDfs(components, graph, i, curComponent++);

        return curComponent;
    }

    private int[] toBitmap(List<Path> paths) {
        float min_x = Float.POSITIVE_INFINITY, max_x = Float.NEGATIVE_INFINITY;
        float min_y = Float.POSITIVE_INFINITY, max_y = Float.NEGATIVE_INFINITY;
        for (Path p : paths) {
            RectF bounds = new RectF();
            p.computeBounds(bounds, false);
            min_x = Math.min(min_x, bounds.left);
            min_y = Math.min(min_y, bounds.top);
            max_x = Math.max(max_x, bounds.right);
            max_y = Math.max(max_y, bounds.bottom);
        }

        float width = Math.max(max_x - min_x, max_y - min_y);
        float mid_x = (min_x + max_x) / 2;
        float mid_y = (min_y + max_y) / 2;

        Matrix scale = new Matrix();
        scale.postTranslate(-mid_x, -mid_y);
        scale.postScale(((float) BITMAP_SIZE) / width, ((float) BITMAP_SIZE) / width);
        scale.postTranslate(BITMAP_SIZE / 2f, BITMAP_SIZE / 2f);
        for (Path p : paths)
            p.transform(scale);

        Bitmap bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(2);
        paint.setDither(false);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);

        for (Path p : paths)
            canvas.drawPath(p, paint);

        int[] pixels = new int[BITMAP_SIZE * BITMAP_SIZE];
        bitmap.getPixels(pixels, 0, BITMAP_SIZE, 0, 0, BITMAP_SIZE, BITMAP_SIZE);

        for (int i = 0; i < BITMAP_SIZE * BITMAP_SIZE; ++i)
            if (pixels[i] == Color.BLACK)
                pixels[i] = 1;
            else
                pixels[i] = 0;

        return pixels;
    }

    private CharSequence recognizeComponent(List<Path> paths) {
        int[] bitmap = toBitmap(paths);
        INDArray input = Nd4j.zeros(1, BITMAP_SIZE * BITMAP_SIZE);
        for (int i = 0; i < BITMAP_SIZE * BITMAP_SIZE; ++i) {
            input.putScalar(0, i, bitmap[i]);
        }

        INDArray output = ann.output(input);
        int max_idx = 0;
        for (int i = 0; i < symbols.length; ++i)
            if (output.getDouble(0, i) > output.getDouble(0, max_idx))
                max_idx = i;

        return symbols[max_idx];
    }

    CharSequence recognize(List<Path> paths) {
        int[] components = new int[paths.size()];
        int componentsCount = components(paths, components);

        CharSequence[] recognized = new CharSequence[componentsCount];

        for (int compId = 0; compId < componentsCount; ++compId) {
            List<Path> component = new ArrayList<>();
            for (int i = 0; i < paths.size(); ++i)
                if (components[i] == compId)
                    component.add(paths.get(i));
            recognized[compId] = recognizeComponent(component);
        }

        StringBuilder ret = new StringBuilder();
        for (CharSequence cs : recognized)
            ret.append(cs);

        return ret.toString();
    }
}
