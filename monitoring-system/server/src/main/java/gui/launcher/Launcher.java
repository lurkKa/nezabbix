package gui.launcher;

import com.example.server.ServerMain;
import gui.gui.GuiMain;

public class Launcher {
    public static void main(String[] args) {
        new Thread(() -> ServerMain.main(args)).start();
        new Thread(() -> GuiMain.main(args)).start();
    }
}
