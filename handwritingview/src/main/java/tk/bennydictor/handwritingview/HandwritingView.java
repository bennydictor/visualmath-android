package tk.bennydictor.handwritingview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class HandwritingView extends View {
    public static final int COLOR = Color.BLACK;
    public static final int BG_COLOR = Color.WHITE;
    public static final int STROKE_WIDTH = 20;
    public static final int COUNTDOWN = 1000;

    private Paint paint;
    private PathSet curPathSet;
    private CountDownTimer timer;
    private OnNewTextListener listener;

    public HandwritingView(Context context) {
        this(context, null);
    }

    public HandwritingView(Context context, AttributeSet attrs) {
        super(context, attrs);

        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(STROKE_WIDTH);
        paint.setColor(COLOR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);

        curPathSet = null;
        final HandwritingView self = this;
        timer = new CountDownTimer(COUNTDOWN, COUNTDOWN) {
            @Override
            public void onTick(long l) {}

            @Override
            public void onFinish() {
                self.onTimeout();
            }
        };
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(BG_COLOR);
        if (curPathSet != null) {
            for (Path p : curPathSet.paths) {
                canvas.drawPath(p, paint);
            }
        }
    }

    public void onTimeout() {
        CharSequence text = SymbolRecognizer.get().recognize(curPathSet.paths);
        listener.onNewText(text);
        curPathSet = null;
        invalidate();
    }

    public void setOnNewTextListener(OnNewTextListener onNewTextListener) {
        listener = onNewTextListener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (curPathSet == null) {
                    curPathSet = new PathSet();
                }
                curPathSet.newPath();
                curPathSet.moveTo(x, y);
                timer.cancel();
                break;
            case MotionEvent.ACTION_MOVE:
                curPathSet.quadTo(x, y);
                break;
            case MotionEvent.ACTION_UP:
                curPathSet.finish();
                timer.start();
                break;
        }
        invalidate();
        return true;
    }

    public interface OnNewTextListener {
        void onNewText(CharSequence text);
    }
}
