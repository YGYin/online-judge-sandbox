package com.ygyin.onlinejudgesandbox.model;

import lombok.Data;

/**
 * 判题信息
 */
@Data
public class TestInfo {

    /**
     * 题目提交执行运行信息
     */
    private String msg;

    /**
     * 消耗内存
     */
    private Long memory;

    /**
     * 消耗时间
     */
    private Long time;
}
