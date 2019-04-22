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
     */
    private void gotoNext(ItemSet closure) {
        for (int i = 0; i < symbol.length; i++) {
            byte[] cur = gotoTable.get(itemSetList.indexOf(closure));
            Set<Item> newSet = new HashSet<>();
            Item reduceItem = null;
            for (Item item : closure.itemSet) {
                if (item.arriveReduce()) {
                    continue;
                }
                if (item.getCurChar().equals(symbol[i])) {
                    Item newItem = item.moveIndex(symbol[i]);
                    if (newItem.arriveReduce()) {
                        assert reduceItem == null;
                        reduceItem = newItem;
                    }
                    newSet.add(newItem);
                }
            }
            if (newSet.isEmpty()) {
                continue;
            }
            ItemSet newItemSet = new ItemSet(newSet);
            getClosure(newItemSet);
            if (!itemSetList.contains(newItemSet)) {
                //这样做的原因是检查空产生式是否加入到了新的项目集中
                if (reduceItem == null) {
                    for (Item item : newItemSet.itemSet) {
                        if (item.arriveReduce()) {
                            reduceItem = item;
                        }
                    }
                }
                byte[] row = new byte[symbol.length];
                if (reduceItem == null) {
                    Arrays.fill(row, (byte) 0);
                } else {
                    int temp = reduceItem.index;
                    reduceItem.index = 0;
                    int index = grammerList.indexOf(reduceItem);
                    assert index != -1;
                    reduceItem.index = temp;
                    Arrays.fill(row, (byte) -(index + 1));
                }
                itemSetList.add(newItemSet);
                gotoTable.add(row);
                cur[i] = (byte) itemSetList.size();
            } else {
                int index = itemSetList.indexOf(newItemSet);
                cur[i] = (byte) (index + 1);
            }
        }
    }

    /**
     * 生成转换表。
     */
    public void gotoTableGen() {
        //程序的开头被定义为Z->P，不能修改；
        Item item = new Item();
        item.head = "Z";
        item.body = new ArrayList<>(1);
        item.body.add("P");
        Set<Item> init = new HashSet<>();
        init.add(item);
        grammerList.add(item);
        ItemSet initSet = new ItemSet(init);
        getClosure(initSet);
        itemSetList.add(initSet);
        byte[] initRow = new byte[symbol.length];
        Arrays.fill(initRow, (byte) 0);
        gotoTable.add(initRow);
        int beginIndex = 0;
        int endIndex = 1;
        while (beginIndex != endIndex) {
            int temp = beginIndex;
            beginIndex = itemSetList.size();
            for (int i = temp; i < endIndex; i++) {
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
    public GrammerTree translate(String sourceFile, List<String> errorList) throws IOException {
        //TODO 将读到的源码翻译成语法树
        lexAnalyzer.setSourceCode(sourceFile);
        Token token;
        Stack<Integer> state = new Stack<>();
        state.push(0);
        Stack<String> sym = new Stack<>();
        sym.push("$");
        Stack<GrammerTree> node = new Stack<>();
        node.push(new GrammerTree(new Token("$")));
        while ((token = lexAnalyzer.nextToken()) != null) {
            if (token.isError()) {
                errorList.add(token.value);
                continue;
            }
            int index = findSymbolIndex(token.name);
            int nextStep = gotoTable.get(state.peek())[index];
            if (nextStep > 0) {
                state.push(nextStep - 1);
                sym.push(token.name);
                GrammerTree tree = new GrammerTree(token);
                node.push(tree);
            } else if (nextStep < 0) {
                Item item = grammerList.get(-nextStep - 1);
                if (item.head.equals("Z")) {
                    //当推导到程序定义的符号Z时，就认为其已经结束了翻译
                    break;
                }
                Stack<GrammerTree> child = new Stack<>();
                for (int i = item.body.size() - 1; i >= 0; i--) {
                    Token temp = node.peek().getValue();
                    if (temp.name.equals(item.body.get(i))) {
                        child.push(node.pop());
                        state.pop();
                        sym.pop();
                    } else {
                        //这儿产生了一个错误，暂时认为其为内部错误；
                        errorList.add("Inner error!");
                        return null;
                    }
                }
                sym.push(item.head);
                int temp = gotoTable.get(state.peek())[findSymbolIndex(item.head)] - 1;
                state.push(temp);
                Token newToken = new Token(item.head, "");
                newToken.line = token.line;
                GrammerTree tree = new GrammerTree(newToken);
                while (!child.isEmpty()) {
                    tree.addChild(child.pop());
                }
                node.push(tree);
                lexAnalyzer.tokenGoBack();
            } else {
                //这儿输出了一个错误，需要进行错误处理
                String errorMsg = "Unrecognized symbol %s, at line %d";
                if (token.value.isEmpty()) {
                    errorList.add(String.format(errorMsg, token.name, token.line));
                } else {
                    errorList.add(String.format(errorMsg, token.value, token.line));
                }
            }
        }
        return node.peek();
    }

    private int findSymbolIndex(String symbol) {
        for (int i = 0; i < this.symbol.length; i++) {
            if (symbol.equals(this.symbol[i])) {
                return i;
            }
        }
        return -1;
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

    public boolean parseTransformTable(String path) {
        return lexAnalyzer.parseTransformTable(path);
    }

    public void showTransformTable(DefaultTableModel model) {
        model.setColumnIdentifiers(symbol);
        String[] buf = new String[symbol.length];
        for (int i = 0; i < gotoTable.size(); i++) {
            byte[] row = gotoTable.get(i);
            for (int j = 0; j < symbol.length; j++) {
                buf[j] = Integer.toString(row[j]);
            }
            model.addRow(buf);
        }
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
//            if(this.body.size() == 1 && this.body.get(0).isEmpty()){
//                this.index = 1;
//            }
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
            return index >= body.size();
        }

        private String getCurChar() {
            if (index > body.size()) {
                return null;
            }
            if (index == body.size()) {
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
            if (obj instanceof Item) {
                Item item = (Item) obj;
                if (!item.head.equals(this.head) || item.body.size() != this.body.size()) {
                    return false;
                }
                for (int i = 0; i < this.body.size(); i++) {
                    if (!item.body.get(i).equals(this.body.get(i))) {
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
            this.contain = TOKEN;
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

        boolean isError() {
            return this.contain == ERROR;
        }

        /**
         * 定义每一个token的输出格式
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
            List<String> accept = new ArrayList<>();
            for (State state : acceptList) {
                if (state != null && !accept.contains(state.getIdentifier())) {
                    accept.add(state.getIdentifier());
                }
            }
            String[] symbolList = new String[accept.size() + grammer.size() + 1];
            for (int i = 0; i < accept.size(); i++) {
                symbolList[i] = accept.get(i);
            }
            int i = accept.size();
            symbolList[i] = "$";
            i++;
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
            this.analyze();
            Token token = new Token("$", "");
            tokenList.add(token);
            index = 0;
        }

        Token nextToken() {
            if (index >= tokenList.size()) {
                return null;
            }
            return tokenList.get(index++);
        }

        void tokenGoBack() {
            index--;
        }

        private void analyze() {
            int i = 0;
            StringBuilder charBuf = new StringBuilder();
            int curState = 0;
            char curChar;
            int acceptState = -1;
            int acceptStateBufIndex = -1;
            int line = 0;
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
                            if (acceptStateBufIndex < charBuf.length()) {
                                addError(charBuf.substring(acceptStateBufIndex, charBuf.length()));
                            }
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
                            if (acceptStateBufIndex < charBuf.length()) {
                                addError(charBuf.substring(acceptStateBufIndex, charBuf.length()));
                            }
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
            token.line = line;
            tokenList.add(token);
        }

        private void addError(String errorMsg) {
            Token token = new Token(errorMsg);
            tokenList.add(token);
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

    public static void main(String[] args) throws IOException {
        GrammerAnalyzer analyzer = new GrammerAnalyzer();
        analyzer.parseTransformTable("./file/trans.lext");
        try {
            //测试
            analyzer.readGrammer("./file/grammer.lext");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        analyzer.lexAnalyzer.setSourceCode("./file/test.s");
        analyzer.lexAnalyzer.analyze();
        for (Token token : analyzer.lexAnalyzer.tokenList) {
            System.out.println(token);
        }
    }
}