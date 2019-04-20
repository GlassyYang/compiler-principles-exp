import analyzer.GrammerAnalyzer;

import java.io.IOException;

/**
 * 程序入口类
 */
public class Main {
    public static void main(String[] args) {
        GrammerAnalyzer analyzer = new GrammerAnalyzer();
        boolean step1 = analyzer.parseTransformTable("./file/trans.lext");
        System.out.println(step1);
        try {
            boolean ans = analyzer.readGrammer("./file/grammer.lext");
            System.out.print(ans);
        }catch (IOException e){
            e.printStackTrace();
        }
        analyzer.gotoTableGen();
//        try {
//            analyzer.setSourceCode("./file/test.s");
//        }catch (IOException e){
//            e.printStackTrace();
//        }

    }
}
