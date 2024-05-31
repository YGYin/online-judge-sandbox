package com.ygyin.onlinejudgesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.ygyin.onlinejudgesandbox.model.ProcessExecMsg;
import com.ygyin.onlinejudgesandbox.model.RunCodeRequest;
import com.ygyin.onlinejudgesandbox.model.RunCodeResponse;
import com.ygyin.onlinejudgesandbox.model.TestInfo;
import com.ygyin.onlinejudgesandbox.utils.ProcessExecUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 通过直接实现 Sandbox 接口的 Java 代码沙箱 (deprecated)
 */
public class NativeJavaSandboxDeprecated implements Sandbox {
    private static final String JAVA_CLASS_NAME = "Main.java";

    private static final String TEMP_CODE_DIR = "userTempCode";

    private static final long OVERTIME_LIMIT = 5000L;
    private static final List<String> BLACK_LIST = Arrays.asList("exec", "Files");

    private static final WordTree WORD_TREE;

    static {
        // 初始化字典树，加载非法操作的黑名单
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(BLACK_LIST);
    }

    // 用于测试
    public static void main(String[] args) {
        NativeJavaSandboxDeprecated sandbox = new NativeJavaSandboxDeprecated();
        // 读取文件中的代码
        String code = ResourceUtil.readStr("sampleCode/APlusB/Main.java", StandardCharsets.UTF_8);

        RunCodeRequest runCodeRequest = new RunCodeRequest();

        runCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        runCodeRequest.setCode(code);
        runCodeRequest.setLanguage("java");
        RunCodeResponse runCodeResponse = sandbox.runCode(runCodeRequest);
        System.out.println(runCodeResponse);
    }

    @Override
    public RunCodeResponse runCode(RunCodeRequest runCodeRequest) {
//        System.setSecurityManager(new CustomSecurityManager());

        // 1. 先将用户代码保存为文件
        List<String> inputList = runCodeRequest.getInputList();
        String code = runCodeRequest.getCode();
        String language = runCodeRequest.getLanguage();

        // 校验用户代码是否存在非法操作
        FoundWord matchedWord = WORD_TREE.matchWord(code);

        if (matchedWord != null) {
            System.out.println("Command not allow: " + matchedWord.getFoundWord());
            return null;
        }

        // 获取用户当前目录
        String userDir = System.getProperty("user.dir");
        String globalCodePath = userDir + File.separator + TEMP_CODE_DIR;

        // 判断当前目录下是否存在存放代码的目录，若不存在则新建该目录
        if (!FileUtil.exist(globalCodePath))
            FileUtil.mkdir(globalCodePath);

        // 按不同用户将其提交的代码划分存放到不同文件夹中
        String userFolderPath = globalCodePath + File.separator + UUID.randomUUID();
        String userCodePath = userFolderPath + File.separator + JAVA_CLASS_NAME;
        // 将用户代码写入到对应路径的文件中
        File codeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // 2. 开始编译用户代码，并得到 .class 字节码
        // 用于执行命令行
        String compileCommand = String.format("javac -encoding utf-8 %s", codeFile.getAbsolutePath());

        Process compileProc = null;
        try {
            compileProc = Runtime.getRuntime().exec(compileCommand);
            ProcessExecMsg execMsg = ProcessExecUtils.doProcessAndGetMsg(compileProc, "Compile");
            System.out.println(execMsg);
        } catch (Exception e) {
            return getRunErrorResponse(e);
        }

        // 3. 执行编译后的 .class，运行代码并得到对应的运行输出结果

        // 每个输入用例都会对应一个输出信息
        List<ProcessExecMsg> execMsgList = new ArrayList<>();
        for (String arg : inputList) {
            // 每一个输入参数都放进命令中
            // "java -Dfile.encoding=UTF-8 -cp %s Main %s"
            // 分配最大堆内存做限制
            String runCommand = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userFolderPath, arg);
            try {
                // 实际上开启了一个新的子进程
                Process runProc = Runtime.getRuntime().exec(runCommand);
                // 做超时控制
                // 用于解决恶意超时问题，创建线程，要是 sleep 时间过后程序仍未执行完成将其销毁
                new Thread(() -> {
                    try {
                        Thread.sleep(OVERTIME_LIMIT);
                        runProc.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();

                ProcessExecMsg execMsg = ProcessExecUtils.doProcessAndGetMsg(runProc, "Run");
                System.out.println(execMsg);
                execMsgList.add(execMsg);
            } catch (Exception e) {
                return getRunErrorResponse(e);
            }
        }

        // 4. 整理运行输出结果
        RunCodeResponse runCodeResponse = new RunCodeResponse();
        // 从 execMsgList 获取每个执行信息中的 msg
        List<String> output = new ArrayList<>();
        // 记录所有执行信息中运行时间的最大值，只要有最大值超出了题目时间限制即可认为超时
        long maxExecTime = 0;

        for (ProcessExecMsg execMsg : execMsgList) {
            String errorMsg = execMsg.getErrorMsg();
            // 如果包含错误信息，加载错误信息和 status 到 resp 中，break
            // Status 3: 用户提交的代码运行时存在错误
            if (StrUtil.isNotBlank(errorMsg)) {
                runCodeResponse.setRunMsg(errorMsg);
                runCodeResponse.setRunStatus(3);
                break;
            }
            // 没有错误信息，按顺序添加到结果列表里
            output.add(execMsg.getMsg());
            // 同时更新当前最大执行时间
            Long execTime = execMsg.getExecTime();
            if (execTime != null)
                maxExecTime = Math.max(execTime, maxExecTime);
        }
        // 没有错误信息，设置对应 status
        if (output.size() == execMsgList.size())
            runCodeResponse.setRunStatus(1);

        runCodeResponse.setOutputList(output);

        TestInfo testInfo = new TestInfo();
        testInfo.setTime(maxExecTime);
        // todo: 获取 Process 内存

        //  testInfo.setMemory();
        runCodeResponse.setTestInfo(testInfo);

        // 5. 对编译运行成功的用户代码做文件清理
        if (codeFile.getParentFile() != null) {
            boolean isDelete = FileUtil.del(userFolderPath);
            System.out.println(isDelete ? "代码文件删除成功" : "代码文件删除失败");
        }

        return runCodeResponse;
    }

    /**
     * 封装一个错误处理方法，对代码沙箱运行错误的情况时，获取到对应的错误响应
     *
     * @param e
     * @return
     */
    private RunCodeResponse getRunErrorResponse(Throwable e) {
        // 6. 对一些错误做通用处理
        RunCodeResponse runErrorResponse = new RunCodeResponse();
        runErrorResponse.setOutputList(new ArrayList<>());
        // Status 2: 代码沙箱本身运行存在错误
        runErrorResponse.setRunStatus(2);
        runErrorResponse.setRunMsg(e.getMessage());
        runErrorResponse.setTestInfo(new TestInfo());
        return runErrorResponse;
    }
}
