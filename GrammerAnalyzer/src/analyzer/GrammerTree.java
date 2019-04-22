package analyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于表示语法文分析树的类，由语法分析器生成，可以通过静态方法printTree打印出来。
 */
public class GrammerTree {

    private List<GrammerTree> child;
    private GrammerAnalyzer.Token value;

    GrammerTree(GrammerAnalyzer.Token token) {
        this.value = token;
        child = new ArrayList<>();
    }

    void addChild(GrammerTree tree){
        child.add(tree);
    }

    public GrammerAnalyzer.Token getValue() {
        return value;
    }

    /**
     * 采用递归方式打印语法树
     * @param root
     */
    public static void printTree(GrammerTree root, StringBuilder output){
        print(root, output, 0);
    }
    private static void print(GrammerTree root, StringBuilder output, int line){
        for(int i = 0; i < line; i++){
            output.append("  ");
        }
        output.append(root.value);
        output.append("\r\n");
        for(GrammerTree tree : root.child){
            print(tree, output, line+1);
        }
    }

}
