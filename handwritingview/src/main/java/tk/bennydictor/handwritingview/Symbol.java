package tk.bennydictor.handwritingview;

import android.graphics.Path;
import java.util.ArrayList;

class Symbol {
    ArrayList<Path> paths;
    private float lastX, lastY;

    Symbol() {
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
}
