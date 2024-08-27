package com.easypan.annotation;

import java.lang.annotation.*;

//全局拦截器

@Target({ElementType.METHOD,ElementType.TYPE}) //注解需要用在什么上面
@Retention(RetentionPolicy.RUNTIME)  //注解生命周期：运行时
@Documented
@Inherited  //指定注解是否被子类继承
public @interface GlobalInterceptor {
    //是否需要登录（有一些资源无需登录也可访问到）
    boolean checkLogin() default true;
    //是否需要校验参数
    boolean checkParams() default false;
    //校验是否为超级管理员
    boolean checkAdmin () default false;

}
