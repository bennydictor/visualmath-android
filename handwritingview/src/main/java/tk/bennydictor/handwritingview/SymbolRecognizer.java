package tk.bennydictor.handwritingview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Locale;

public class SymbolRecognizer {
    private static SymbolRecognizer symbolRecognizer;
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
        symbolRecognizer = new SymbolRecognizer(context);
    }

    static SymbolRecognizer getSymbolRecognizer() {
        return symbolRecognizer;
    }


    CharSequence recognize(Symbol symbol) {
        float min_x = Float.POSITIVE_INFINITY, max_x = Float.NEGATIVE_INFINITY;
        float min_y = Float.POSITIVE_INFINITY, max_y = Float.NEGATIVE_INFINITY;
        for (Path p : symbol.paths) {
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
        scale.postScale(28f / width, 28f / width);
        scale.postTranslate(14, 14);
        for (Path p : symbol.paths)
            p.transform(scale);

        Bitmap bitmap = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        Rect bounds = new Rect();
        canvas.getClipBounds(bounds);

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(2);
        paint.setDither(false);
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);

        for (Path p : symbol.paths)
            canvas.drawPath(p, paint);
        int[] pixels = new int[28 * 28];
        bitmap.getPixels(pixels, 0, 28, 0, 0, 28,28);

        INDArray input = Nd4j.zeros(1, 28 * 28);
        for (int i = 0; i < 28 * 28; ++i) {
            input.putScalar(0, i, pixels[i] == Color.BLACK ? 1 : 0);
        }

        INDArray output = ann.output(input);
        int max_idx = 0;
        for (int i = 0; i < 10; ++i)
            if (output.getDouble(0, i) > output.getDouble(0, max_idx))
                max_idx = i;
        return symbols[max_idx];
    }
}
