package com.ygyin.onlinejudgesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.ygyin.onlinejudgesandbox.model.ProcessExecMsg;
import com.ygyin.onlinejudgesandbox.model.RunCodeRequest;
import com.ygyin.onlinejudgesandbox.model.RunCodeResponse;
import com.ygyin.onlinejudgesandbox.model.TestInfo;
import com.ygyin.onlinejudgesandbox.utils.ProcessExecUtils;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DockerJavaSandboxDeprecated implements Sandbox {
    private static final String JAVA_CLASS_NAME = "Main.java";

    private static final String TEMP_CODE_DIR = "userTempCode";

    private static final long OVERTIME_LIMIT = 5000L;

    private static final boolean INIT_NEEDED = true;


    // 用于测试
    public static void main(String[] args) {
        DockerJavaSandboxDeprecated sandbox = new DockerJavaSandboxDeprecated();
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

        // 3. 新建 docker container，并将编译好的文件复制到容器内运行
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
//        PingCmd pingCmd = dockerClient.pingCmd();
//        pingCmd.exec();

        // 3.1 获取镜像
        String image = "openjdk:8-alpine";
        if (INIT_NEEDED) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("Docker 下载镜像中: " + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("获取镜像异常");
                throw new RuntimeException(e);
            }
        }

        System.out.println("镜像下载完成");

        // 3.2 创建对应容器，通过 HostConfig 来限制资源，并且在创建时就复制文件
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        // 100m
        hostConfig.withMemory(100 * 100 * 1000L);
        hostConfig.withMemorySwap(0L);
        // 把本地的文件路径映射到容器中的路径
        hostConfig.setBinds(new Bind(userFolderPath, new Volume("/myCode")));

        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();


        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

//        // 3.3 查看容器状态
//        ListContainersCmd listContainersCmd = dockerClient.listContainersCmd();
//        List<Container> containers = listContainersCmd.withShowAll(true).exec();
//        for (Container container :containers)
//            System.out.println(container);

        // 4. 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // 用于存放执行结果
        List<ProcessExecMsg> execMsgList = new ArrayList<>();

        // 创建命令，在容器中执行命令，并获取执行输出结果
        // docker exec lucid_rhodes java -cp /myCode Main 1 3
        for (String args : inputList) {
            // 定义 stopWatch 用于计算执行时间
            StopWatch watch = new StopWatch();
            String[] splitArgs = args.split(" ");
            String[] cmd = ArrayUtil.append(new String[]{"java", "-cp", "/myCode", "Main"}, splitArgs);
            ExecCreateCmdResponse createCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmd)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            System.out.println("创建命令: " + createCmdResponse);

            // 新建 execMsg 用于存放单次执行结果
            ProcessExecMsg execMsg = new ProcessExecMsg();
            final String[] msg = {null};
            final String[] errorMsg = {null};
            long execTime = 0L;
            final boolean[] isTimeOut = {true};
            ExecStartResultCallback execStartResCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    // 需要区分输出结果是否为正常执行，输出前先装载结果到 msg 和 errorMsg 中
                    StreamType streamType = frame.getStreamType();

                    if (StreamType.STDERR.equals(streamType)) {
                        errorMsg[0] = new String(frame.getPayload());
                        System.out.println("执行命令错误输出: " + errorMsg[0]);
                    } else {
                        msg[0] = new String(frame.getPayload());
                        System.out.println("执行命令输出结果: " + msg[0]);
                    }

                    super.onNext(frame);
                }

                @Override
                public void onComplete() {
                    isTimeOut[0] = false;
                    super.onComplete();
                }
            };

            final long[] maxMemUsage = {0L};

            // 获取运行占用的内存，应该每隔一定时间区间获取一次
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statsCmdCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onNext(Statistics statistics) {
                    Long memUsage = statistics.getMemoryStats().getUsage();
                    System.out.println("程序内存占用: " + memUsage);
                    maxMemUsage[0] = Math.max(maxMemUsage[0], memUsage);
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });
            statsCmd.exec(statsCmdCallback);

            // 执行命令，并阻塞等待程序执行完成，不然可能拿不到结果
            try {
                watch.start();
                // todo: id 有可能为空，需要判空
                // 增加超时参数，如果在允许时间内执行完成会回调 callback 中的 onComplete
                dockerClient
                        .execStartCmd(createCmdResponse.getId())
                        .exec(execStartResCallback)
                        .awaitCompletion(OVERTIME_LIMIT, TimeUnit.MILLISECONDS);
                watch.stop();
                execTime = watch.getLastTaskTimeMillis();
                // 停止内存监控
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("Docker 命令执行异常");
                throw new RuntimeException(e);
            }
            execMsg.setMsg(msg[0]);
            execMsg.setErrorMsg(errorMsg[0]);
            execMsg.setExecTime(execTime);
            execMsg.setExecMem(maxMemUsage[0]);
            execMsgList.add(execMsg);
        }

        // 5. 整理封装运行输出结果
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

        // 6. 对编译运行成功的用户代码做文件清理
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
