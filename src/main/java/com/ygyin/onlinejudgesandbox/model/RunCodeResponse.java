package com.ygyin.onlinejudgesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunCodeResponse {

    /**
     * 执行结果输出
     */
    private List<String> outputList;
    /**
     * 代码运行状态
     */
    private Integer runStatus;
    /**
     * 接口信息 (与判题信息有区别)，例如接口超时
     */
    private String runMsg;
    private TestInfo testInfo;
}
