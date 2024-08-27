package com.easypan;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.servlet.MultipartConfigElement;

@EnableAsync //启用Spring的异步方法执行功能。
@SpringBootApplication(scanBasePackages = {"com.easypan"})
@MapperScan(basePackages = {"com.easypan.mappers"})  //Mapper所在的地址
@EnableTransactionManagement //启用Spring的声明式事务管理功能。
@EnableScheduling //启用Spring的任务调度功能。
public class EasypanApplication {
    public static void main(String[] args) {
        SpringApplication.run(EasypanApplication.class, args);
    }
}
