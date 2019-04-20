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

    /**
     * 采用递归方式打印语法树
     * @param root
     */
    static void printTree(GrammerTree root, StringBuilder output){
        output.append(root.value);
        for(GrammerTree tree : root.child){
            printTree(tree, output);
        }
    }

}
