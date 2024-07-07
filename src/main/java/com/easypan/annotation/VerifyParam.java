package com.easypan.annotation;


import com.easypan.entity.enums.VerifyRegexEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//检验参数
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER,ElementType.FIELD})  //作用与参数和属性
public @interface VerifyParam {
    //正则校验，默认不校验
    VerifyRegexEnum regex() default VerifyRegexEnum.NO;
    //参数最长
    int min() default -1;
    //参数最短
    int max() default -1;
    boolean required() default false;
}
