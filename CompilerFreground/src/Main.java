import displayer.Displayer;

import javax.swing.*;

/**
 * 程序入口类
 */
public class Main {
    public static void main(String[] args) {
        JFrame frame = Displayer.getInstance();
        if(frame == null){
            return;
        }
        frame.setVisible(true);
    }
}
