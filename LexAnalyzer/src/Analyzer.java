import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.util.Arrays;
import java.util.Vector;

public class Analyzer {

    private byte[][] transformTable;
    private static final int charaNum = 93;
    private static final int tableBase = 33;

    private int stateNum;
    private State[] acceptList;

    private Analyzer() {
        transformTable = null;

    }

    private boolean parseTransformTable(String filepath) {
        BufferedReader reader;
        String line;
        try {
            reader = new BufferedReader(new FileReader(filepath));
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found, Inner error!");
            return false;
        }
        try {
            do {
                line = reader.readLine();
                if (line == null) {
                    System.out.println("Illegal File Format");
                    return false;
                }
            } while (line.startsWith("#"));
            stateNum = Integer.parseInt(line);
            transformTable = new byte[stateNum][charaNum];
            for (byte[] arr : transformTable) {
                Arrays.fill(arr, (byte) -1);
            }
            int curState = 0;
            while ((line = reader.readLine()) != null) {
                if (line.equals("end")) {
                    break;
                } else if (line.isEmpty()) {
                    curState++;
                    continue;
                }
                String[] blocks = line.split(";\\s+");
                for (String block : blocks) {
                    block = block.strip();
                    String[] parts = block.split(":");
                    fillTable(curState, parts[1], Byte.parseByte(parts[0]));
                }
                curState++;
            }
            acceptList = new State[stateNum];
            Arrays.fill(acceptList, null);
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                int index = Integer.parseInt(parts[0]);
                boolean neededValue = parts[2].charAt(0) == '1';
                acceptList[index] = new State(parts[1], neededValue);
            }
        } catch (IOException e) {
            e.printStackTrace();
            try {
                reader.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return false;
        }
        return true;
    }

    private void fillTable(int initState, String ident, byte jumpState) {
        if (ident.length() == 1) {
            transformTable[initState][ident.charAt(0) - tableBase] = jumpState;
        } else {
            for (int i = ident.charAt(0); i <= ident.charAt(2); i++) {
                transformTable[initState][i - tableBase] = jumpState;
            }
        }
    }

    //这儿少一个分析函数
    void analyze(String source, DefaultTableModel tokenList, Vector<String> errorList) {
        int i = 0;
        StringBuilder charBuf = new StringBuilder();
        int curState = 0;
        char curChar;
        int acceptState = -1;
        int acceptStateBufIndex = -1;
        int line = 1;
        while (i < source.length()) {
            curChar = source.charAt(i++);
            //空白符代表着一段标识的结束，不能忽略
            if (Character.isWhitespace(curChar)) {
                if(curChar == '\n'){
                    line++;
                }
                if (charBuf.length() != 0) {
                    if (acceptList[curState] != null) {
                        addToken(tokenList, curState, charBuf.toString());
                        charBuf.setLength(0);
                    } else if (acceptState != -1) {
                        //输出一段接收的字符串和一段错误字符串
                        errorList.add(charBuf.substring(acceptStateBufIndex, charBuf.length()));
                        charBuf.delete(acceptStateBufIndex, charBuf.length());
                        addToken(tokenList, acceptState, charBuf.toString());
                        charBuf.setLength(0);
                    } else {
                        errorList.add(String.format("Unrecognized string %s", charBuf.toString()));
                        charBuf.setLength(0);
                    }
                    curState = 0;
                    acceptState = -1;
                }
                continue;
            }
            if (curChar < tableBase || curChar >= 126) {
                //添加条目error，进入错误恢复状态
                errorList.add(String.format("Unrecognized character %c at line %d", curChar, line));
                continue;
            }
            curState = transformTable[curState][curChar - tableBase];
            //进行分情况讨论
            System.out.println(curState);
            if(curState == -2){
                charBuf.append(curChar);
                while(i < source.length() && !Character.isWhitespace(source.charAt(i))){
                    charBuf.append(source.charAt(i));
                    i++;
                }
                errorList.add(String.format("Unrecognized string %s at line %d", charBuf.toString(), line));
                charBuf.setLength(0);
                curState = 0;
            } else if (curState != -1) {
                charBuf.append(curChar);
                if (acceptList[curState] != null) {
                    acceptState = curState;
                    acceptStateBufIndex = charBuf.length();
                }
            } else {
                // 这儿还有几种情况需要考虑
                if (transformTable[0][curChar - tableBase] == -1) {
                    //出现错误，进入错误恢复状态
                    if (acceptState != -1) {
                        //添加一条token序列
                        String temp = charBuf.substring(acceptStateBufIndex, charBuf.length());
                        charBuf.delete(acceptStateBufIndex, charBuf.length());
                        addToken(tokenList, acceptState, charBuf.toString());
                        charBuf.setLength(0);
                        charBuf.append(temp);
                    }
                    while (transformTable[0][curChar - tableBase] == -1) {
                        charBuf.append(curChar);
                        curChar = source.charAt(i);
                        while (curChar < tableBase || curChar >= 126) {
                            curChar = source.charAt(i++);
                            charBuf.append(curChar);
                        }
                    }
                    //输出错误序列
                    errorList.add(String.format("Unrecognized string %s at line %d", charBuf.toString(), line));
                    charBuf.setLength(0);
                    i--;
                } else {
                    //查看接受状态
                    //要求当前状态就是接收状态；如果当前状态不是接收状态，那么说明出错了，直接忽略掉这一段
                    if (acceptState != -1) {
                        //输出一条token序列和一条错误序列
                        errorList.add(charBuf.substring(acceptStateBufIndex, charBuf.length()));
                        charBuf.delete(acceptStateBufIndex, charBuf.length());
                        addToken(tokenList, acceptState, charBuf.toString());
                    } else {
                        //输出一条错误序列
                        errorList.add(String.format("Unrecognized string %s at line %d", charBuf.toString(), line));
                    }
                    charBuf.setLength(0);
                    i--;
                }
                curState = 0;
                acceptState = -1;
            }
        }
        //最后检查一下缓冲区是不是空的，如果不是就说明还有别的东西
        if(charBuf.length() != 0){
            if(acceptList[curState] != null){
                addToken(tokenList, curState, charBuf.toString());
            }else if(acceptState != -1){
                errorList.add(String.format("Unrecognized string %s at line %d", charBuf.substring(acceptStateBufIndex, charBuf.length()), line));
                charBuf.delete(acceptStateBufIndex, charBuf.length());
                addToken(tokenList, acceptState, charBuf.toString());
            }else{
                errorList.add(String.format("Unrecognized string %s at line %d", charBuf.toString(), line));
            }
        }
    }

    private void addToken(DefaultTableModel model, int state, String value) {
        String[] row = new String[2];
        row[0] = acceptList[state].getIdentifier();
        row[1] = (acceptList[state].isNeedValue()) ? value : "";
        model.addRow(row);
    }

    void showTransformTable(DefaultTableModel model) {
        String[] row = new String[charaNum + 1];
        row[0] = "状态";
        for (int i = 0; i < charaNum; i++) {
            row[i + 1] = Character.toString(i + tableBase);
        }
        model.setColumnIdentifiers(row);
        for (int i = 0; i < stateNum; i++) {
            row[0] = Integer.toString(i);
            for (int j = 0; j < charaNum; j++) {
                if (transformTable[i][j] == -1) {
                    row[j + 1] = "";
                } else {
                    row[j + 1] = Integer.toString(transformTable[i][j]);
                }
            }
            model.addRow(row);
        }
    }

    static Analyzer getInstance(String filepath) {
        Analyzer analyzer = new Analyzer();
        if (analyzer.parseTransformTable(filepath)) {
            return analyzer;
        }
        return null;
    }

    class State {
        final private String identifier;
        final private boolean needValue;

        State(String identifier, boolean needValue) {
            this.identifier = identifier;
            this.needValue = needValue;
        }

        String getIdentifier() {
            return identifier;
        }

        boolean isNeedValue() {
            return needValue;
        }
    }

    /**
     * 此方法用于测试
     *
     * @param args 控制台参数，没用。该死的intellij非要让我写
     */
    public static void main(String[] args) {
        Analyzer any = new Analyzer();
        any.parseTransformTable("./file/trans.lext");
    }
}
