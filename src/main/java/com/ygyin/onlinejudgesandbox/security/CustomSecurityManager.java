package com.ygyin.onlinejudgesandbox.security;

import java.security.Permission;

public class CustomSecurityManager extends SecurityManager {

    // 检查所有的权限
    @Override
    public void checkPermission(Permission perm) {
//        throw new SecurityException("权限异常: "+perm.getActions());
//        super.checkPermission(perm);
    }

    @Override
    public void checkRead(String file) {
        // release hutool
        if (file.contains("hutool"))
            return;

        throw new SecurityException("权限异常: checkRead " + file);
    }

    @Override
    public void checkExec(String cmd) {
        throw new SecurityException("权限异常: checkExec " + cmd);
    }

    @Override
    public void checkWrite(String file) {
        throw new SecurityException("权限异常: checkWrite " + file);

    }

    @Override
    public void checkDelete(String file) {
        throw new SecurityException("权限异常: checkDelete " + file);
    }

    @Override
    public void checkConnect(String host, int port) {
        throw new SecurityException("权限异常: checkConnect " + host + ": " + port);
    }
}
