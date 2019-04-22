package displayer;

import analyzer.GrammerAnalyzer;
import analyzer.GrammerTree;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.IOException;
import java.util.Vector;

public class Displayer {
    private JTextArea resOutput;
    private JTable transformTable;
    private JPanel root;
    private JButton showTransform;
    private JButton seeSourceCode;
    private JButton grammerAnalyze;
    private JList<String> errorOutput;

    private GrammerAnalyzer analyzer;
    private JFrame parent;

    private DefaultTableModel transModel;
    private Displayer(GrammerAnalyzer analyzer, JFrame parent) {
        this.analyzer = analyzer;
        this.parent = parent;
        showTransform.addActionListener((e)-> {
            analyzer.showTransformTable(transModel);
        });
        seeSourceCode.addActionListener((e)-> {
            try {
                Runtime.getRuntime().exec("gedit ./file/test2.s");
            }catch (IOException err){
                JOptionPane.showMessageDialog(parent, err.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        grammerAnalyze.addActionListener((e)-> {
            Vector<String> errorList = new Vector<>();
            try {
                GrammerTree tree = analyzer.translate("./file/test2.s", errorList);
                errorOutput.setListData(errorList);
                if(tree == null){
                    return;
                }
                StringBuilder build = new StringBuilder();
                GrammerTree.printTree(tree, build);
                resOutput.setText(build.toString());
            }catch(IOException err){
                JOptionPane.showMessageDialog(parent, err.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void createUIComponents() {
        transformTable = new JTable();
        transModel = new DefaultTableModel();
        transformTable.setModel(transModel);
        transformTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    }

    public static JFrame getInstance(){
        JFrame frame = new JFrame("Displayer");
        GrammerAnalyzer analyzer = new GrammerAnalyzer();
        analyzer.parseTransformTable("./file/trans.lext");
        try {
            //测试
            analyzer.readGrammer("./file/grammer2.lext");
//            analyzer.readGrammer("./file/testGrammer.lext");
        }catch (IOException e){
            e.printStackTrace();
            return null;
        }
        analyzer.gotoTableGen();
        frame.setContentPane(new Displayer(analyzer, frame).root);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        return frame;
    }
}
