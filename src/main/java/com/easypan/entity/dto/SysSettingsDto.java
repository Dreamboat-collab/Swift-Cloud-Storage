package com.easypan.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
//系统设置

@JsonIgnoreProperties(ignoreUnknown = true)
public class SysSettingsDto implements Serializable {
    private String registerMailTitle = "邮箱验证码";
    private String registerEmailContent = "您好，你的邮箱验证码是：%s，15分钟有效";

    //用户的初始存储空间大小
    private Integer userInitSpace = 5;

    public String getRegisterMailTitle() {
        return registerMailTitle;
    }

    public String getRegisterEmailContent() {
        return registerEmailContent;
    }

    public Integer getUserInitSpace() {
        return userInitSpace;
    }

    public void setRegisterMailTitle(String registerMailTitle) {
        this.registerMailTitle = registerMailTitle;
    }

    public void setRegisterEmailContent(String registerEmailContent) {
        this.registerEmailContent = registerEmailContent;
    }

    public void setUserInitSpace(Integer userInitSpace) {
        this.userInitSpace = userInitSpace;
    }
}
