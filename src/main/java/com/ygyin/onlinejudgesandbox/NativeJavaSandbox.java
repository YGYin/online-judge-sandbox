package com.ygyin.onlinejudgesandbox;

import com.ygyin.onlinejudgesandbox.model.RunCodeRequest;
import com.ygyin.onlinejudgesandbox.model.RunCodeResponse;
import org.springframework.stereotype.Component;

/**
 * 通过实现抽象类并应用了模板方法的 Java 代码沙箱
 * 可以直接复用模板方法，也可以根据自己需求重写方法
 */
@Component
public class NativeJavaSandbox extends JavaSandboxTemplate {

    @Override
    public RunCodeResponse runCode(RunCodeRequest runCodeRequest) {
        return super.runCode(runCodeRequest);

    }
}
