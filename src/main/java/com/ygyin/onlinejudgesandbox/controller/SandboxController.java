package com.ygyin.onlinejudgesandbox.controller;

import com.ygyin.onlinejudgesandbox.NativeJavaSandbox;
import com.ygyin.onlinejudgesandbox.model.RunCodeRequest;
import com.ygyin.onlinejudgesandbox.model.RunCodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController("/")
public class SandboxController {
    private static final String AUTH_REQ_SECRET_KEY = "authKey";
    private static final String AUTH_REQ_HEADER = "authHeader";
    /**
     * 引入 Java 沙箱实现
     */
    @Resource
    private NativeJavaSandbox nativeJavaSandbox;
    // 分別鉴权请求头和请求密钥
    // private static final

    @GetMapping("/connect")
    public String connectCheck(){
        return "connect ok";
    }

    /**
     * 运行代码
     * @param runCodeRequest
     * @return
     */
    @PostMapping("/runCode")
    RunCodeResponse runCode(@RequestBody RunCodeRequest runCodeRequest, HttpServletRequest req,
                            HttpServletResponse resp){
        // 基本鉴权认证
        String authHeader = req.getHeader(AUTH_REQ_HEADER);
        if (!AUTH_REQ_SECRET_KEY.equals(authHeader)){
            resp.setStatus(403);
            return null;
        }
        if (runCodeRequest == null)
            throw new RuntimeException("运行代码请求参数为空");
        return nativeJavaSandbox.runCode(runCodeRequest);
    }


}
