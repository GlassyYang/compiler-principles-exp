package analyzer;

import javax.swing.table.DefaultTableModel;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class GrammerAnalyzer {


    /**
     * 域
     */
    private List<byte[]> gotoTable;
    private Map<String, List<Item>> grammer;
    private List<Item> grammerList;
    private String[] symbol;
    private List<ItemSet> itemSetList;

    private LexAnalyzer lexAnalyzer;

    public GrammerAnalyzer() {
        gotoTable = new ArrayList<>();
        itemSetList = new ArrayList<>();
        lexAnalyzer = new LexAnalyzer();
        grammer = new HashMap<>();
        grammerList = new ArrayList<>();
    }

    /**
     * 程序求得输入项目集的闭包，但是会将求得的闭包添加到传递进来的项目集中。相当于closure函数
     *
     * @param set 要求闭包的项目集
     * @return 返回传入的项目集，此时项目集中包含的已经是输入项目集的闭包了。
     */
    private void getClosure(ItemSet set) {
        boolean changed = true;
        Set<Item> itemSet = set.itemSet;
        Set<Item> tempSet = new HashSet<>();
        while (changed) {
            changed = false;
            for (Item item : itemSet) {
                String headCur = item.getCurChar();
                if (grammer.containsKey(headCur)) {
                    List<Item> itemList = grammer.get(headCur);
                    if (!itemSet.containsAll(itemList)) {
                        for (Item temp : itemList) {
                            Item newItem = new Item(temp);
                            tempSet.add(newItem);
                        }
                        changed = true;
                    }
                }
            }
            itemSet.addAll(tempSet);
            tempSet.clear();
        }
    }

    /**
     * goto函数，对输入的项目集，求此法定义的所有终结符的下一个项集，然后填写转换表。
     * 生成的新的项集和原来的项目集没关系，不会使用原来的项目集
     *
     * @param closure
     */
    private void gotoNext(ItemSet closure) {
        for (int i = 0; i < symbol.length; i++) {
            byte[] cur = gotoTable.get(itemSetList.indexOf(closure));
            Set<Item> newSet = new HashSet<>();
            Item reduceItem = null;
            for (Item item : closure.itemSet) {
                if(item.arriveReduce()){
                    continue;
                }
                if (item.getCurChar().equals(symbol[i])) {
                    Item newItem = item.moveIndex(symbol[i]);
                    if (newItem == null) {
                        assert reduceItem == null;
                        reduceItem = item;
                    }
                    newSet.add(newItem);
                }
            }
            if(newSet.isEmpty()){
                continue;
            }
            ItemSet newItemSet = new ItemSet(newSet);
            getClosure(newItemSet);
            if (!itemSetList.contains(newItemSet)) {
                byte[] row = new byte[symbol.length];
                if (reduceItem == null) {
                    Arrays.fill(row, (byte)0);
                } else {
                    int temp = reduceItem.index;
                    reduceItem.index = 0;
                    int index = grammerList.indexOf(reduceItem);
                    assert index != -1;
                    reduceItem.index = temp;
                    Arrays.fill(row, (byte)-(index + 1));
                }
                int size = itemSetList.size();
                itemSetList.add(newItemSet);
                gotoTable.add(row);
                cur[i] = (byte)size;
            }else{
                int index = itemSetList.indexOf(newItemSet);
                cur[i] = (byte)index;
            }
        }
    }

    /**
     * 生成转换表。
     */
    public void gotoTableGen() {
        //程序的开头被定义为s->p，不能修改；
        Item item = new Item();
        item.head = "Z";
        item.body = new ArrayList<>(1);
        item.body.add("P");
        Set<Item> init = new HashSet<>();
        init.add(item);
        ItemSet initSet = new ItemSet(init);
        getClosure(initSet);
        itemSetList.add(initSet);
        byte[] initRow = new byte[symbol.length];
        Arrays.fill(initRow, (byte)0);
        gotoTable.add(initRow);
        int beginIndex = 0;
        int endIndex = 1;
        while(beginIndex != endIndex){
            int temp = beginIndex;
            beginIndex = itemSetList.size();
            for(int i = temp; i < endIndex; i++){
                gotoNext(itemSetList.get(i));
            }
            endIndex = itemSetList.size();
        }
    }

    /**
     * 进行文法翻译。
     *
     * @param sourceFile 符合要求的源码文件
     */
    public void translate(String sourceFile) throws IOException{
        //将读到的源码翻译成语法树
        lexAnalyzer.setSourceCode(sourceFile);
        Token token;
        while((token = lexAnalyzer.nextToken()) != null){

        }
    }

    public boolean readGrammer(String filepath) throws IOException {
        BufferedReader reader;
        reader = new BufferedReader(new FileReader(filepath));
        String line;
        while ((line = reader.readLine()) != null) {
            Item item = new Item();
            if (item.parseExp(line)) {
                List<Item> itemList;
                if (!grammer.containsKey(item.head)) {
                    itemList = new ArrayList<>();
                    grammer.put(item.head, itemList);
                } else {
                    itemList = grammer.get(item.head);
                }
                itemList.add(item);
                grammerList.add(item);
            } else {
                return false;
            }
        }
        symbol = lexAnalyzer.getTerminalSymbol();
        return true;
    }

    public boolean parseTransformTable(String path){
        return lexAnalyzer.parseTransformTable(path);
    }

    public void setSourceCode(String path) throws IOException{
        lexAnalyzer.setSourceCode(path);
    }
    class Item {
        private String head;
        private List<String> body;
        //index 代表点当前所在的位置;
        private int index;

        private Item() {
            index = 0;
        }

        private Item(Item item) {
            this.head = item.head;
            this.body = item.body;
            this.index = 0;
        }

        boolean parseExp(String genExp) {
            String[] parts = genExp.split("\\s*->\\s*");
            if (parts.length != 2 || parts[0].length() != 1 || parts[1].length() < 1) {
                return false;
            }
            this.head = parts[0];
            body = new ArrayList<>();
            String body = parts[1];
            StringBuilder build = new StringBuilder();
            for (int i = 0; i < body.length(); i++) {
                if (body.charAt(i) == '"') {
                    build.setLength(0);
                    i += 1;
                    while (i < body.length() && body.charAt(i) != '"') {
                        build.append(body.charAt(i));
                        i++;
                    }
                    if (i >= body.length()) {
                        return false;
                    }
                    this.body.add(build.toString());
                } else {
                    this.body.add(Character.toString(body.charAt(i)));
                }
            }
            return true;
        }

        /**
         * 移动点号的下标，
         *
         * @return 返回移动后的新下标
         */
        private Item moveIndex(String c) {
            if (!body.get(index).equals(c)) {
                return null;
            }
            int newIndex = index + 1;
            if (newIndex > body.size()) {
                return null;
            } else {
                Item item = new Item();
                item.head = this.head;
                item.body = this.body;
                item.index = newIndex;
                return item;
            }
        }

        private boolean arriveReduce() {
            return index > body.size();
        }

        private String getCurChar() {
            if (index > body.size()) {
                return null;
            }
            if(index == body.size()){
                return "";
            }
            return body.get(index);
        }


        @Override
        public int hashCode() {
            int hashcode = head.hashCode() * body.size();
            for (int i = 1; i < body.size(); i++) {
                hashcode += body.get(i).hashCode() * i;
            }
            return hashcode + index * 31;
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof Item){
                Item item = (Item)obj;
                if(!item.head.equals(this.head) || item.body.size() != this.body.size()){
                    return false;
                }
                for(int i = 0; i < this.body.size(); i++){
                    if(!item.body.get(i).equals(this.body.get(i))){
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }

    /**
     * 表示项目集的类
     */
    class ItemSet {
        private Set<Item> itemSet;

        ItemSet(Set<Item> set) {
            this.itemSet = set;
        }

        //重写equals和hashcode两个函数用于以后项目集的比较
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ItemSet)) {
                return false;
            }
            ItemSet item = (ItemSet) obj;
            if (item.itemSet.containsAll(this.itemSet) && this.itemSet.containsAll(item.itemSet)) {
                return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            //这样的算法保证了只要是项目集中的Item都相同，则hashCode返回的值都是相同的。
            int hashcode = 0;
            for (Item i : itemSet) {
                hashcode += i.hashCode();
            }
            return hashcode;
        }
    }

    /**
     * 用于表示包括非终结符，终结符（有值的终结符可能包括值）。
     */
    class Token {
        private String name;
        private String value;
        private int line;
        private int contain;

        final static int ERROR = 0;
        final static int TOKEN = 1;

        Token(String name, String value) {
            this.name = name;
            this.value = value;
        }

        Token(String errorMsg) {
            this.value = errorMsg;
            this.contain = ERROR;
            line = -1;
        }

        Token(String name, String value, int line) {
            this.name = name;
            this.value = value;
            this.line = line;
            this.contain = TOKEN;
        }

        int getContain() {
            return this.contain;
        }

        String getName() {
            return this.name;
        }

        String getValue() {
            return this.value;
        }

        /**
         * 定义每一个token的输出格式
         *
         * @return
         */
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(name);
            if (value != null) {
                builder.append(':');
                builder.append(value);
            }
            builder.append('(');
            builder.append(line);
            builder.append(')');
            return builder.toString();
        }
    }

    /**
     * 内嵌的语法分析器
     */
    private class LexAnalyzer {

        private byte[][] transformTable;
        private static final int charaNum = 93;
        private static final int tableBase = 33;

        private int stateNum;
        private State[] acceptList;
        //用来分析的源代码
        private String sourceCode;
        //词法分析器分析得到的结果
        private List<Token> tokenList;
        private int index;
        private Future<?> finished;

        private LexAnalyzer() {
            transformTable = null;
            tokenList = new ArrayList<>();
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

        private String[] getTerminalSymbol() {
            List<State> accept = new ArrayList<>();
            for(State state : acceptList){
                if(state != null){
                    accept.add(state);
                }
            }
            String[] symbolList = new String[accept.size() + grammer.size()];
            for (int i = 0; i < accept.size(); i++) {
                symbolList[i] = accept.get(i).getIdentifier();
            }
            int i = accept.size();
            for (String key : grammer.keySet()) {
                symbolList[i++] = key;
            }
            return symbolList;
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

        void setSourceCode(String path) throws IOException {
            Path p = Paths.get(path);
            this.sourceCode = Files.readString(p);
            ExecutorService exec = Executors.newCachedThreadPool();
            finished = exec.submit(this::analyze);
            index = 0;
        }

        public Token nextToken() {
            if (finished.isDone()) {
                if (index >= tokenList.size()) {
                    return null;
                }
                return tokenList.get(index++);
            } else {
                int size;
                do {
                    size = tokenList.size();
                } while (!finished.isDone() && size <= index);
                if (index < size) {
                    return tokenList.get(index++);
                } else {
                    return null;
                }
            }
        }

        private void analyze() {
            int i = 0;
            StringBuilder charBuf = new StringBuilder();
            int curState = 0;
            char curChar;
            int acceptState = -1;
            int acceptStateBufIndex = -1;
            int line = 1;
            while (i < sourceCode.length()) {
                curChar = sourceCode.charAt(i++);
                //空白符代表着一段标识的结束，不能忽略
                if (Character.isWhitespace(curChar)) {
                    if (curChar == '\n') {
                        line++;
                    }
                    if (charBuf.length() != 0) {
                        if (acceptList[curState] != null) {
                            addToken(curState, charBuf.toString(), line);
                            charBuf.setLength(0);
                        } else if (acceptState != -1) {
                            //输出一段接收的字符串和一段错误字符串
                            addError(charBuf.substring(acceptStateBufIndex, charBuf.length()));
                            charBuf.delete(acceptStateBufIndex, charBuf.length());
                            addToken(acceptState, charBuf.toString(), line);
                            charBuf.setLength(0);
                        } else {
                            addError(String.format("Unrecognized string %s", charBuf.toString()));
                            charBuf.setLength(0);
                        }
                        curState = 0;
                        acceptState = -1;
                    }
                    continue;
                }
                if (curChar < tableBase || curChar >= 126) {
                    //添加条目error，进入错误恢复状态
                    addError(String.format("Unrecognized character %c at line %d", curChar, line));
                    continue;
                }
                curState = transformTable[curState][curChar - tableBase];
                //进行分情况讨论
                System.out.println(curState);
                if (curState == -2) {
                    charBuf.append(curChar);
                    while (i < sourceCode.length() && !Character.isWhitespace(sourceCode.charAt(i))) {
                        charBuf.append(sourceCode.charAt(i));
                        i++;
                    }
                    addError(String.format("Unrecognized string %s at line %d", charBuf.toString(), line));
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
                            addToken(acceptState, charBuf.toString(), line);
                            charBuf.setLength(0);
                            charBuf.append(temp);
                        }
                        while (transformTable[0][curChar - tableBase] == -1) {
                            charBuf.append(curChar);
                            curChar = sourceCode.charAt(i);
                            while (curChar < tableBase || curChar >= 126) {
                                curChar = sourceCode.charAt(i++);
                                charBuf.append(curChar);
                            }
                        }
                        //输出错误序列
                        addError(String.format("Unrecognized string %s at line %d", charBuf.toString(), line));
                        charBuf.setLength(0);
                        i--;
                    } else {
                        //查看接受状态
                        //要求当前状态就是接收状态；如果当前状态不是接收状态，那么说明出错了，直接忽略掉这一段
                        if (acceptState != -1) {
                            //输出一条token序列和一条错误序列
                            addError(charBuf.substring(acceptStateBufIndex, charBuf.length()));
                            charBuf.delete(acceptStateBufIndex, charBuf.length());
                            addToken(acceptState, charBuf.toString(), line);
                        } else {
                            //输出一条错误序列
                            addError(String.format("Unrecognized string %s at line %d", charBuf.toString(), line));
                        }
                        charBuf.setLength(0);
                        i--;
                    }
                    curState = 0;
                    acceptState = -1;
                }
            }
            //最后检查一下缓冲区是不是空的，如果不是就说明还有别的东西
            if (charBuf.length() != 0) {
                if (acceptList[curState] != null) {
                    addToken(curState, charBuf.toString(), line);
                } else if (acceptState != -1) {
                    addError(String.format("Unrecognized string %s at line %d", charBuf.substring(acceptStateBufIndex, charBuf.length()), line));
                    charBuf.delete(acceptStateBufIndex, charBuf.length());
                    addToken(acceptState, charBuf.toString(), line);
                } else {
                    addError(String.format("Unrecognized string %s at line %d", charBuf.toString(), line));
                }
            }
        }

        private void addToken(int state, String value, int line) {
            String name = acceptList[state].getIdentifier();
            String val = (acceptList[state].isNeedValue()) ? value : "";
            Token token = new Token(name, val);
            synchronized (tokenList) {
                tokenList.add(token);
            }

        }

        private void addError(String errorMsg) {
            Token token = new Token(errorMsg);
            synchronized (tokenList) {
                tokenList.add(token);
            }
        }

        public void showTransformTable(DefaultTableModel model) {
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
    }

    /**
     * 测试代码
     * @param args
     */
    public static void main(String[] args) {
        GrammerAnalyzer analyzer = new GrammerAnalyzer();
        Item item1 = analyzer.new Item();
        Set<Item> set1 = new HashSet<>();
        item1.parseExp("D->\"function\"D\";\"DS");
        set1.add(item1);
        Item item2 = analyzer.new Item();
        item2.parseExp("D->\"function\"D\";\"DS");
        Set<Item> set2 = new HashSet<>();
        set2.contains(item1);

        assert item1.equals(item2);
        assert item1.hashCode() == item2.hashCode();
        assert item1 != item2;
    }
}