package analyzer;

import java.util.ArrayList;
import java.util.List;

enum Type {
    VARIABLE_INT(4),
    VARIABLE_FLOAT(4),
    VARIABLE_STRUCT(-1),
    VARIABLE_ARRAY(-1),
    FUNCTION(-1);
    private int width;

    //专为array设计
    private Type containType;

    Type(int width) {
        this.width = width;
    }

    int getWidth() {
        return this.width;
    }

    void setWidth(int width) {
        this.width = width;
    }

    Type getInnerType() {
        return this.containType;
    }

    void setInnerType(Type type) {
        this.containType = type;
    }
}

public class SemanticAnalyzer {

    private SymbolTable root;

    private List<String> codeList;
    private String assCode = "(%s, %s, %s, %s)\t\t %4$s = %2$s %1$s %3$s";
    private String conJmpCode = "(%s, %s, %s, %s)\t\t if %2$s %1$s %3$s goto %4$s";
    private String dirCmpCode = "(goto, _, _, %s)\t\t goto %s";
    private String paraCode = "(param, _, _, %s)\t\t param %s";
    private int tempVarNum;
    private StringBuilder varBuild;
    SemanticAnalyzer() {
        codeList = new ArrayList<>();
        varBuild = new StringBuilder();
        root = new SymbolTable();
    }

    private String newTemp() {
        varBuild.setLength(0);
        varBuild.append('t');
        varBuild.append(tempVarNum++);
        return varBuild.toString();
    }

    private int nextCode() {
        return tempVarNum;
    }

    private void fillList(List<Integer> codeList, String num, boolean bool) {
        String form;
        if (bool) {
            form = "true";
        } else {
            form = "false";
        }
        codeList.forEach(e -> {
            String ans = this.codeList.get(e);
            ans = ans.replace(form, num);
            this.codeList.add(e, ans);
        });
    }

    private Type C(GrammerTree tree, Type eType) {
        List<GrammerTree> child = tree.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        Type type = Type.VARIABLE_ARRAY;
        type.setInnerType(eType);
        try {
            int length = Integer.parseInt(child.get(1).getValue().getValue());
            type.setWidth(length * eType.getWidth());
        } catch (NumberFormatException e) {
            token = child.get(1).getValue();
            throw new CodeGenException(String.format("Error at Line %d: cannot parse \"%s\" to integer", token.getLine(), token.getValue()));
        }
        if (token.getName().equals("[")) {
            return type;
        } else {
            return C(child.get(0), type);
        }
    }

    private Type X(GrammerTree tree) {
        List<GrammerTree> child = tree.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        String name = token.getName();
        if (name.equals("int")) {
            return Type.VARIABLE_INT;
        } else if (name.equals("float")) {
            return Type.VARIABLE_FLOAT;
        } else {
            //需要在符号表中查找该记录声明
            token = child.get(1).getValue();
            SymbolTable.TableItem item = root.findItemByName(token.getValue());
            if (item != null) {
                return item.type;
            } else {
                //发现错误。一旦发现错误停止编译
                throw new CodeGenException(String.format("Error at line %d: Cannot find symbol \"%s\"",token.getLine(),  token.getValue()));
            }
        }
    }

    private Type T(GrammerTree tree, SymbolTable table) {
        List<GrammerTree> child = tree.getChild();
        Type type = X(child.get(0));
        if (child.size() > 1) {
            type = C(child.get(1), type);
        }
        return type;
    }

    private void D(GrammerTree tree, SymbolTable table) {
        List<GrammerTree> child = tree.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        if (token.getName().equals("D")) {
            D(child.get(0), table);
            D(child.get(1), table);
        } else {
            Type type = T(child.get(0), table);
            //向符号表中添加条目
            token = child.get(1).getValue();
            SymbolTable.TableItem item = table.new TableItem(type, token.getValue());
            table.addItem(item);
        }
    }

    private String R(GrammerTree tree) {
        GrammerAnalyzer.Token token = tree.getChild().get(0).getValue();
        return token.getName();
    }

    private CompAttr L(GrammerTree tree, SymbolTable table) {
        List<GrammerTree> child = tree.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        if (token.getName().equals("L")) {
            token = child.get(1).getValue();
            if (!token.getName().equals("int")) {
                throw new CodeGenException(String.format("Error at line %d: float number found, but integer needed.", token.getLine()));
            }
            CompAttr ans = L(child.get(0), table);
            String label = newTemp();
            codeList.add(String.format(assCode, "*", token.getValue(), Integer.toString(ans.type.getWidth()), label));
            String newLabel = newTemp();
            codeList.add(String.format(assCode, "+", label, ans.value, newLabel));
            CompAttr attr = new CompAttr(ans.type.getInnerType(), newLabel);
            attr.id = ans.id;
            return attr;
        } else {
            SymbolTable.TableItem item = table.findItemByName(token.getValue());
            if (item == null) {
                throw new CodeGenException(String.format("Error at line %d: cannot find symbol \"%s\"",token.getLine(), token.getValue()));
            } else if (item.type != Type.VARIABLE_ARRAY) {
                throw new CodeGenException(String.format("Error at line %d: variable must have array type",token.getLine()));
            } else {
                Type type = item.type;
                String id = token.getValue();
                token = child.get(1).getValue();
                if (!token.getName().equals("int")) {
                    throw new CodeGenException(String.format("Error at line %d: float number found, but integer needed.", token.getLine()));
                }
                String label = newTemp();
                codeList.add(String.format(assCode, "*", token.getValue(), Integer.toString(type.getWidth()), label));
                CompAttr attr = new CompAttr(type.getInnerType(), label);
                attr.id = id;
                return attr;
            }
        }
    }

    private CompAttr E(GrammerTree tree, SymbolTable table) {
        List<GrammerTree> child = tree.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        switch (token.getName()) {
            case "E":
                CompAttr e1 = E(child.get(0), table);
                CompAttr e2 = E(child.get(2), table);
                String label = newTemp();
                String oper = child.get(1).getValue().getName();
                codeList.add(String.format(assCode, oper, e1.getValue(), e2.getValue(), label));
                Type newType;
                if (e1.type == Type.VARIABLE_FLOAT || e2.type == Type.VARIABLE_FLOAT)
                    newType = Type.VARIABLE_FLOAT;
                else
                    newType = Type.VARIABLE_INT;
                return new CompAttr(newType, label);
            case "L":
                e1 = L(child.get(0), table);
                if (e1.type == Type.VARIABLE_ARRAY) {
                    throw new CodeGenException(String.format("Error at line %d: integer or float required.", token.getLine()));
                }
                label = newTemp();
                codeList.add(String.format("(-, %s, %s, %s)\t\t %3$s = %1$s[%2$s]", e1.id, e1.value, label));
                return new CompAttr(e1.type, label);
            case "-":
                label = newTemp();
                e1 = E(child.get(1), table);
                codeList.add(String.format("(=[], %s, _, %s)\t\t %2$s = -%1$s", e1.value, label));
                return new CompAttr(e1.type, label);
            case "(":
                return E(child.get(1), table);
            case "id":
                //查找符号表，找到该标识符的定义
                SymbolTable.TableItem item = table.findItemByName(token.getValue());
                if (item == null) {
                    throw new CodeGenException(String.format("Error at line %d: cannot find \"%s\"", token.getLine(), token.getValue()));
                } else if (item.type != Type.VARIABLE_INT && item.type != Type.VARIABLE_FLOAT) {
                    throw  new CodeGenException(String.format("Error at line %d: integer or float variable required", token.getLine()));
                } else {
                    return new CompAttr(item.type, item.getName());
                }
            case "real":
                return new CompAttr(Type.VARIABLE_FLOAT, token.getValue());
            case "num":
                return new CompAttr(Type.VARIABLE_INT, token.getValue());
            default:
                throw new CodeGenException(String.format("Error at line %d: inner error!", token.getLine()));
        }
    }

    private BCompAttr B(GrammerTree tree, SymbolTable table) {
        List<GrammerTree> child = tree.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        switch (token.getName()) {
            case "B":
                BCompAttr b1 = B(child.get(0), table);
                int nextCode = nextCode();
                BCompAttr b2 = B(child.get(2), table);
                token = child.get(1).getValue();
                if (token.getName().equals("&")) {
                    fillList(b1.trueList, Integer.toString(nextCode), true);
                    return new BCompAttr(b2.trueList, b1.combineList(b2.falseList, false));
                } else {
                    fillList(b1.falseList, Integer.toString(nextCode), false);
                    return new BCompAttr(b1.combineList(b2.trueList, true), b2.falseList);
                }
            case "!":
                b1 = B(child.get(1), table);
                return new BCompAttr(b1.falseList, b1.trueList);
            case "E":
                CompAttr e1 = E(child.get(0), table);
                String oper = R(child.get(1));
                CompAttr e2 = E(child.get(2), table);
                int temp = nextCode();
                codeList.add(String.format(conJmpCode, oper, e1.value, e2.value, "true"));
                List<Integer> trueList = new ArrayList<>();
                trueList.add(temp);
                temp = nextCode();
                codeList.add((String.format(dirCmpCode, "false")));
                List<Integer> falseList = new ArrayList<>();
                falseList.add(temp);
                return new BCompAttr(trueList, falseList);
            case "(":
                return B(child.get(1), table);
            case "true":
                temp = nextCode();
                codeList.add(String.format(dirCmpCode, "true"));
                trueList = new ArrayList<>();
                trueList.add(temp);
                return new BCompAttr(trueList, new ArrayList<>());
            case "false":
                temp = nextCode();
                codeList.add(String.format(dirCmpCode, "false"));
                falseList = new ArrayList<>();
                falseList.add(temp);
                return new BCompAttr(new ArrayList<>(), falseList);
            default:
                throw new CodeGenException(String.format("Error at line %d: inner error!", token.getLine()));
        }
    }

    private int A(GrammerTree tree, SymbolTable table) {
        List<GrammerTree> child = tree.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        Type type;
        if (token.getName().equals("T")) {
            type = T(child.get(0), table);
            token = child.get(1).getValue();
            table.addItem(table.new TableItem(type, token.getValue()));
            return type.getWidth();
        } else {
            int width = A(child.get(0), table);
            type = T(child.get(0), table);
            token = child.get(2).getValue();
            table.addItem(table.new TableItem(type, token.getName()));
            return width + type.getWidth();
        }
    }

    private int F(GrammerTree tree, SymbolTable table) {
        List<GrammerTree> child = tree.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        if (token.getName().equals("F")) {
            int temp = F(child.get(0), table);
            CompAttr b = E(child.get(1), table);
            codeList.add(String.format(paraCode, b.value));
            return temp + 1;
        } else {
            CompAttr b = E(child.get(0), table);
            codeList.add(String.format(paraCode, b.value));
            return 1;
        }
    }

    private void S(GrammerTree tree, SymbolTable table) {
        List<GrammerTree> child = tree.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        switch (token.getName()) {
            case "S":
                S(child.get(0), table);
                S(child.get(1), table);
                break;
            case "D":
                D(child.get(0), table);
                break;
            case "id":
                if (child.size() == 5) {
                    SymbolTable.TableItem item = table.findItemByName(token.getValue());
                    if (item == null) {
                        throw new CodeGenException(String.format("Error at line %d: cannot find symbol \"%s\"", token.getLine(), token.getValue()));
                    } else if (item.type != Type.FUNCTION) {
                        throw new CodeGenException(String.format("Error at line %d: \"%s\" is not a function name", token.getLine(), token.getValue()));
                    }
                    int num = F(child.get(2), table);
                    codeList.add(String.format("(call, %s, %d, _)\t\t call %1$s, %2$d", token.getValue(), num));
                } else {
                    SymbolTable.TableItem item = table.findItemByName(token.getValue());
                    if (item == null) {
                        throw new CodeGenException(String.format("Error at line %d: cannot find symbol \"%s\"", token.getLine(), token.getValue()));
                    } else if (item.type == Type.FUNCTION) {
                        throw new CodeGenException(String.format("Error at line %d: function \"%s\" not allowed here.", token.getLine(), token.getValue()));
                    }
                    CompAttr e = E(child.get(2), table);
                    codeList.add(String.format("(=, %s, _, %s)\t\t %2$s = %1$s", e.value, token.getName()));
                }
                break;
            case "L":
                CompAttr l = L(child.get(0), table);
                CompAttr e = E(child.get(2), table);
                if (l.type == Type.FUNCTION || l.type == Type.VARIABLE_ARRAY) {
                    throw new CodeGenException(String.format("Error at line %d: Only integer or float are allowed here.", token.getLine()));
                }
                codeList.add(String.format("([]=, %s,%s, %s)\t\t %2$s[%3$s] = %1$s", e.value, l.value, l.id));
                break;
            case "if":
                if (child.size() == 7) {
                    BCompAttr b = B(child.get(2), table);
                    fillList(b.trueList, Integer.toString(nextCode()), true);
                    S(child.get(5), table);
                    fillList(b.trueList, Integer.toString(nextCode()), false);
                } else {
                    BCompAttr b = B(child.get(2), table);
                    fillList(b.trueList, Integer.toString(nextCode()), true);
                    S(child.get(5), table);
                    int end = nextCode();
                    codeList.add(String.format(dirCmpCode, "end"));
                    fillList(b.falseList, Integer.toString(nextCode()), false);
                    S(child.get(9), table);
                    String endS = codeList.get(end);
                    codeList.add(end, endS.replace("end", Integer.toString(nextCode())));
                    fillList(b.falseList, Integer.toString(nextCode()), false);
                }
                break;
            case "while":
                int trueI = nextCode();
                BCompAttr b = B(child.get(2), table);
                fillList(b.trueList, Integer.toString(nextCode()), true);
                S(child.get(5), table);
                codeList.add(String.format(dirCmpCode, Integer.toString(trueI)));
                fillList(b.falseList, Integer.toString(nextCode()), false);
                break;
        }
    }

    private void P(GrammerTree root) {
        List<GrammerTree> child = root.getChild();
        GrammerAnalyzer.Token token = child.get(0).getValue();
        switch (token.getName()) {
            case "P":
                P(child.get(0));
                P(child.get(1));
                break;
            case "D":
                D(child.get(0), this.root);
                break;
            case "struct":
                SymbolTable table = new SymbolTable();
                token = child.get(1).getValue();
                SymbolTable.TableItem item = table.new TableItem(Type.VARIABLE_STRUCT, token.getValue());
                item.innerTable = table;
                this.root.addItem(item);
                table.setParent(this.root);
                A(child.get(3), table);
                break;
            case "function":
                table = new SymbolTable();
                token = child.get(1).getValue();
                item = table.new TableItem(Type.FUNCTION, token.getValue());
                item.innerTable = table;
                this.root.addItem(item);
                table.setParent(this.root);
                D(child.get(3), table);
                item.type.setWidth(table.itemList.size());
                S(child.get(6), table);
                break;
        }
    }

    /**
     * 将一棵语法分析树转换成三地址指令和四元式
     * @param root 一棵语法分析树的根
     */
    public void parseGrammerTree(GrammerTree root){
        P(root);
    }
    class BCompAttr {
        private List<Integer> trueList;
        private List<Integer> falseList;

        BCompAttr(List<Integer> trueList, List<Integer> falseList) {
            this.trueList = trueList;
            this.falseList = falseList;
        }

        List<Integer> combineList(List<Integer> list, boolean whi) {
            if (whi) {
                trueList.addAll(list);
                return trueList;
            } else {
                falseList.addAll(list);
                return falseList;
            }
        }
    }

    class CompAttr {
        private Type type;
        private String value;   //存放临时变量的地址或者是值

        private String id;

        CompAttr(Type type, String value) {
            this.type = type;
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }


    class SymbolTable {

        private List<TableItem> itemList;

        private SymbolTable parent;

        SymbolTable() {
            itemList = new ArrayList<>();
            parent = null;
        }

        void addItem(TableItem item) {
            itemList.add(item);
        }

        void setParent(SymbolTable parent) {
            this.parent = parent;
        }

        TableItem findItemByName(String name) {
            SymbolTable parent = this;
            while (parent != null) {
                for (TableItem item : itemList) {
                    if (item.name.equals(name)) {
                        return item;
                    }
                }
                parent = parent.parent;
            }
            return null;
        }

        class TableItem {

            private Type type;
            private String name;
            //该字段只有在符号表条目是FUNCTION 和 STRUCT 的时候才有用
            private SymbolTable innerTable;

            TableItem(Type type, String name) {
                this.type = type;
                this.name = name;
            }

            String getName() {
                return name;
            }
        }
    }
}

/**
 * 定义当语义分析阶段出现错误时抛出的异常。
 */
class CodeGenException extends RuntimeException{
    CodeGenException(String msg){
        super(msg);
    }
}