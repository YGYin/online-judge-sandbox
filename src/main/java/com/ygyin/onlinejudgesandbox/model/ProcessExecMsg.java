package com.ygyin.onlinejudgesandbox.model;

import lombok.Data;

/**
 * 代码沙箱进程的执行信息
 */

@Data
public class ProcessExecMsg {
    /**
     * 退出码，应使用 Integer，int 默认值为 0 与正常执行返回值相同
     */
    private Integer exitCode;

    private String msg;

    private String errorMsg;

    private Long execTime;

    private Long execMem;
}
