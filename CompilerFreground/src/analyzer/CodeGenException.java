package analyzer;


/**
 * 定义当语义分析阶段出现错误时抛出的异常。
 */
public class CodeGenException extends RuntimeException{
    CodeGenException(String msg){
        super(msg);
    }
}
