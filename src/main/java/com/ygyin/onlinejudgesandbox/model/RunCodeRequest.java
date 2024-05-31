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
public class RunCodeRequest {

    /**
     * 接收输入
     */
    private List<String> inputList;
    private String code;
    private String language;
}
