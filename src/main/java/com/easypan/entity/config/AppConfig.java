package com.easypan.entity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("appConfig")
public class AppConfig {
    @Value("${spring.mail.username:}")  //用于将外部配置application.properties中的值注入到修饰的变量中
    private String sendUserName;

    @Value("${admin.emails:}")
    private String adminEmails;

    //applaction.properties中的项目地址
    @Value("${project.folder}")
    private String projectFolder;

    public String getSendUserName() {
        return sendUserName;
    }

    public String getAdminEmails() {
        return adminEmails;
    }

    public String getProjectFolder() {
        return projectFolder;
    }

}
