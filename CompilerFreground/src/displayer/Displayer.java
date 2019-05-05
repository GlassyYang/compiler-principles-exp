package displayer;

import analyzer.CodeGenException;
import analyzer.GrammerAnalyzer;
import analyzer.GrammerTree;
import analyzer.SemanticAnalyzer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.IOException;
import java.util.Vector;

public class Displayer {
    private JTable transformTable;
    private JPanel root;
    private JButton showSymbolTable;
    private JButton seeSourceCode;
    private JButton grammerAnalyze;
    private JList<String> errorOutput;
    private JList<String> codeOutput;
    private JScrollPane codePane;

    private GrammerAnalyzer analyzer;
    SemanticAnalyzer semanticAnalyzer;
    private JFrame parent;

    private DefaultTableModel transModel;
    private Displayer(GrammerAnalyzer analyzer, JFrame parent) {
        this.analyzer = analyzer;
        this.parent = parent;
        semanticAnalyzer = null;
        showSymbolTable.addActionListener((e)-> {
            if(semanticAnalyzer == null){
                JOptionPane.showMessageDialog(parent, "请先进行语义分析！");
                return;
            }
            semanticAnalyzer.showSymbolTable(transModel);
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
            Vector<String> codeList;
            GrammerTree tree;
            try {
                tree = analyzer.translate("./file/test3.s", errorList);
            }catch(IOException err){
                JOptionPane.showMessageDialog(parent, err.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try{
                semanticAnalyzer = new SemanticAnalyzer();
                semanticAnalyzer.parseGrammerTree(tree);
                codeList = semanticAnalyzer.getCodeList();
                codeOutput.setListData(codeList);
            }catch(CodeGenException err) {
                errorList.add("Error occurred during compile.");
                errorList.add(err.getMessage());
                errorOutput.setListData(errorList);
            }
        });
    }

    private void createUIComponents() {
        transformTable = new JTable();
        transModel = new DefaultTableModel();
        transformTable.setModel(transModel);
        transformTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        codePane = new JScrollPane();
        LineNumberHeaderView lineNumberHeader = new LineNumberHeaderView();
        lineNumberHeader.setLineHeight(17);
        codePane.setRowHeaderView(lineNumberHeader);
    }

    public static JFrame getInstance(){
        JFrame frame = new JFrame("Displayer");
        GrammerAnalyzer analyzer = new GrammerAnalyzer();
        analyzer.parseTransformTable("./file/trans.lext");
        try {
            analyzer.readGrammer("./file/grammer.lext");
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
