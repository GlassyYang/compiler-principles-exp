import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Vector;

public class Displayer {
    private JPanel root;
    private JButton openTrans;
    private JButton openSource;
    private JButton showResult;
    private JTable transFormTable;
    private JTextArea source;
    private JTable resultTable;
    private JList<String> errorList;

    private DefaultTableModel resultTableModel, transformTableModel;
    private Analyzer analyzer;

    public Displayer() {

        analyzer = Analyzer.getInstance("./file/trans.lext");
        openTrans.addActionListener((e) -> analyzer.showTransformTable(transformTableModel));
        showResult.addActionListener((e) -> {
            resultTableModel.setNumRows(0);
            String src = source.getText();
            Vector<String> error = new Vector<>();
            analyzer.analyze(src, resultTableModel, error);
            errorList.setListData(error);
        });
        openSource.addActionListener((e)-> {
            Path file = Path.of("./file/test.s");
            try {
                List<String> lines = Files.readAllLines(file);
                StringBuilder build = new StringBuilder();
                lines.forEach(item ->{
                    build.append(item);
                    build.append('\n');
                });
                Vector<String> error = new Vector<>();
                String content = build.toString();
                source.setText(content);
                analyzer.analyze(content, resultTableModel, error);
                errorList.setListData(error);
            }catch(IOException exp){
                JOptionPane.showMessageDialog(null, exp.getMessage());
            }
        });
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Displayer");
        frame.setContentPane(new Displayer().root);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private void createUIComponents() {
        resultTable = new JTable();
        transFormTable = new JTable();
        transFormTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultTableModel = new DefaultTableModel();
        resultTableModel.setColumnIdentifiers(new String[]{"name", "value"});
        transformTableModel = new DefaultTableModel();
        resultTable.setModel(resultTableModel);
        transFormTable.setModel(transformTableModel);
    }
}
