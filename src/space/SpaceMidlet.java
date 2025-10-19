package space;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

public class SpaceMidlet extends MIDlet implements CommandListener {
    private Display display;
    private SpaceCanvas canvas;
    private final Command EXIT = new Command("Exit", Command.EXIT, 1);

    protected void startApp() {
        if (display == null) {
            display = Display.getDisplay(this);
            canvas = new SpaceCanvas();
            canvas.addCommand(EXIT);
            canvas.setCommandListener(this);
        }
        display.setCurrent(canvas);
        canvas.start();
    }

    protected void pauseApp() { if (canvas != null) canvas.pause(); }
    protected void destroyApp(boolean unconditional) { if (canvas != null) canvas.stop(); }

    public void commandAction(Command c, Displayable d) {
        if (c == EXIT) notifyDestroyed();
    }
}
