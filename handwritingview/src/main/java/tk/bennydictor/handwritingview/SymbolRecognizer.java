package tk.bennydictor.handwritingview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.util.Log;

import org.apache.commons.lang3.text.StrBuilder;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Scanner;

public class SymbolRecognizer {
    private static final int BITMAP_SIZE = 50;
    private static SymbolRecognizer instance;
    private MultiLayerNetwork ann;

    private static List<CharSequence> symbols;

    private SymbolRecognizer(Context context) {
        try {
            ann = ModelSerializer.restoreMultiLayerNetwork(context.getResources().openRawResource(R.raw.ann));
            symbols = new ArrayList<>();
            Scanner scanner = new Scanner(context.getResources().openRawResource(R.raw.ann_charmap));
            while (scanner.hasNextLine()) {
                symbols.add(scanner.nextLine());
            }
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

    private void bitmapDfs(int[] bitmap, int i, int j) {
        if (0 <= i && i < BITMAP_SIZE && 0 <= j && j <= BITMAP_SIZE && bitmap[i * 50 + j] == 1) {
            bitmap[i * BITMAP_SIZE + j] = 0;
            bitmapDfs(bitmap, i - 1, j);
            bitmapDfs(bitmap, i + 1, j);
            bitmapDfs(bitmap, i, j - 1);
            bitmapDfs(bitmap, i, j + 1);
        }
    }

    private boolean pathsIntersect(Path a, Path b) {
        ArrayList<Path> list = new ArrayList<>();
        list.add(a);
        list.add(b);
        int[] bitmap = toBitmap(list);
        int count = 0;
        for (int i = 0; i < BITMAP_SIZE * BITMAP_SIZE; ++i) {
            if (bitmap[i] == 1) {
                ++count;
                bitmapDfs(bitmap, i / 50, i % 50);
            }
        }
        return count == 1;
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

        List<Path> scaled = new ArrayList<>();
        for (Path p : paths) {
            Path s = new Path(p);
            s.transform(scale);
            scaled.add(s);
        }

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

        for (Path p : scaled)
            canvas.drawPath(p, paint);

        int[] pixels = new int[BITMAP_SIZE * BITMAP_SIZE];
        bitmap.getPixels(pixels, 0, BITMAP_SIZE, 0, 0, BITMAP_SIZE, BITMAP_SIZE);

        for (int i = 0; i < BITMAP_SIZE * BITMAP_SIZE; ++i) {
            if (pixels[i] == Color.BLACK)
                pixels[i] = 1;
            else
                pixels[i] = 0;
        }

        return pixels;
    }

    private CharSequence recognizeComponent(PathSet ps) {
        if (ps.width / ps.height > 5)
            return "-";
        int[] bitmap = toBitmap(ps.paths);
        INDArray input = Nd4j.zeros(1, BITMAP_SIZE * BITMAP_SIZE);
        for (int i = 0; i < BITMAP_SIZE * BITMAP_SIZE; ++i) {
            input.putScalar(0, i, bitmap[i]);
        }

        INDArray output = ann.output(input);
        int max_idx = 0;
        for (int i = 0; i < symbols.size(); ++i)
            if (output.getDouble(0, i) > output.getDouble(0, max_idx))
                max_idx = i;

        return symbols.get(max_idx);
    }

    private CharSequence arrangeExpr(List<PathSet> symbols) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < symbols.size();) {
            PathSet ps = symbols.get(i);
            if (ps.text.equals("-")) {
                List<PathSet> above = new ArrayList<>();
                List<PathSet> below = new ArrayList<>();
                int j;
                for (j = i + 1; j < symbols.size() && ps.minx < symbols.get(j).midx && symbols.get(j).midx < ps.maxx; ++j) {
                    if (symbols.get(j).midy < ps.midy)
                        above.add(symbols.get(j));
                    else
                        below.add(symbols.get(j));
                }
                if (above.isEmpty() || below.isEmpty()) {
                    ret.append("-");
                    ++i;
                } else {
                    CharSequence above_seq = arrangeExpr(above);
                    CharSequence below_seq = arrangeExpr(below);
                    ret.append("\\frac{").append(above_seq).append("}{").append(below_seq).append("}");
                    i = j;
                }
            } else if (ps.text.equals("\\sqrt{}")) {
                List<PathSet> in = new ArrayList<>();
                int j;
                for (j = i + 1; j < symbols.size() && symbols.get(j).midx < ps.maxx; ++j)
                    in.add(symbols.get(j));
                CharSequence in_seq = arrangeExpr(in);
                ret.append("\\sqrt{").append(in_seq).append("}");
                i = j;
            } else {
                List<PathSet> sub = new ArrayList<>();
                List<PathSet> sup = new ArrayList<>();
                int j;
                for (j = i + 1; j < symbols.size(); ++j) {
                    if (symbols.get(j).height * 1.5 < ps.height) {
                        if (symbols.get(j).maxy < ps.midy - ps.height / 4) {
                            sup.add(symbols.get(j));
                        } else if (symbols.get(j).miny > ps.midy + ps.height / 4) {
                            sub.add(symbols.get(j));
                        } else
                            break;
                    } else
                        break;
                }
                CharSequence sub_seq = arrangeExpr(sub);
                CharSequence sup_seq = arrangeExpr(sup);
                ret.append(ps.text);
                if (sub.size() > 0) {
                    ret.append("_{").append(sub_seq).append("}");
                }
                if (sup.size() > 0) {
                    ret.append("^{").append(sup_seq).append("}");
                }
                i = j;
            }
        }
        return ret.toString();
    }

    CharSequence recognize(List<Path> paths) {
        int[] components = new int[paths.size()];
        int componentsCount = components(paths, components);

        List<PathSet> recognized = new ArrayList<>();

        for (int compId = 0; compId < componentsCount; ++compId) {
            PathSet cur = new PathSet();
            for (int i = 0; i < paths.size(); ++i)
                if (components[i] == compId)
                    cur.paths.add(paths.get(i));
            cur.setCoords();
            cur.text = recognizeComponent(cur);
            recognized.add(cur);
        }

        Collections.sort(recognized, new Comparator<PathSet>() {
            @Override
            public int compare(PathSet p1, PathSet p2) {
                return Float.compare(p1.minx, p2.minx);
            }
        });

        return arrangeExpr(recognized);
    }
}
