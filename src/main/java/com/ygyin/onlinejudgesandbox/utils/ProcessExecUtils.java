package com.ygyin.onlinejudgesandbox.utils;

import com.ygyin.onlinejudgesandbox.model.ProcessExecMsg;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ProcessExecUtils {
    /**
     * 代码沙箱执行对应进程，并返回进程执行的信息
     *
     * @param process
     * @param operation 操作名称
     * @return
     */
    public static ProcessExecMsg doProcessAndGetMsg(Process process, String operation) {
        ProcessExecMsg processExecMsg = new ProcessExecMsg();

        try {
            // 用于计时
            StopWatch watch = new StopWatch();
            watch.start();

            int exitCode = process.waitFor();
            processExecMsg.setExitCode(exitCode);
            // 正常运行退出后退出码为 0
            if (exitCode == 0) {
                System.out.println(operation + ": Java 代码编译成功");
                // 通过流获取控制台输出
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                ArrayList<String> outputMsgList = new ArrayList<>();
                // 对输出进行按行读取
                String cmdOutputMsg;
                while ((cmdOutputMsg = reader.readLine()) != null)
                    outputMsgList.add(cmdOutputMsg);

                // 通过 StringUtils 拼接得到最终结果
                processExecMsg.setMsg(StringUtils.join(outputMsgList, "\n"));
            } else {
                System.out.println(operation + ": Java 代码编译失败，Exit Code: " + exitCode);

                // 通过流获取控制台正常输出 (控制台的输出此处会被写入到输入流里)
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                ArrayList<String> outputMsgList = new ArrayList<>();
                // 对输出进行按行读取
                String cmdOutputMsg;
                while ((cmdOutputMsg = reader.readLine()) != null)
                    outputMsgList.add(cmdOutputMsg);

                // 通过 StringUtils 拼接得到最终结果
                processExecMsg.setMsg(StringUtils.join(outputMsgList, "\n"));

                // 同时也要获取错误流
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                ArrayList<String> errOutputMsgList = new ArrayList<>();

                String errorOutputMsg;
                while ((errorOutputMsg = errorReader.readLine()) != null)
                    errOutputMsgList.add(errorOutputMsg);

                // 通过 StringUtils 拼接得到最终结果
                processExecMsg.setErrorMsg(StringUtils.join(errOutputMsgList, "\n"));
            }
            watch.stop();
            processExecMsg.setExecTime(watch.getLastTaskTimeMillis());

        } catch (Exception e) {
            e.printStackTrace();
        }

        return processExecMsg;
    }

    // todo: 可以对于传统 scanner 和 sout 获取输出的类型做匹配
}
