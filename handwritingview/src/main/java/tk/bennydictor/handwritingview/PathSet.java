package tk.bennydictor.handwritingview;

import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;

class PathSet {
    ArrayList<Path> paths;
    private float lastX, lastY;
    CharSequence text;
    float minx, miny, maxx, maxy;
    float midx, midy;
    float width, height;

    PathSet() {
        paths = new ArrayList<>();
    }

    void newPath() {
        Path path = new Path();
        path.reset();
        paths.add(path);
    }

    void moveTo(float x, float y) {
        getLastPath().moveTo(x, y);
        lastX = x;
        lastY = y;
    }

    void quadTo(float x, float y) {
        getLastPath().quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
        lastX = x;
        lastY = y;
    }

    void finish() {
        getLastPath().lineTo(lastX, lastY);
    }

    private Path getLastPath() {
        return paths.get(paths.size()-1);
    }

    void setCoords() {
        RectF bb = new RectF();
        minx = miny = Float.POSITIVE_INFINITY;
        maxx = maxy = Float.NEGATIVE_INFINITY;
        for (Path p : paths) {
            p.computeBounds(bb, false);
            minx = Math.min(minx, bb.left);
            maxx = Math.max(maxx, bb.right);
            miny = Math.min(miny, bb.top);
            maxy = Math.max(maxy, bb.bottom);
        }
        midx = (minx + maxx) / 2;
        midy = (miny + maxy) / 2;
        width = maxx - minx;
        height = maxy - miny;
    }
}
