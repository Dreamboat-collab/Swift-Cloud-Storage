package com.easypan.utils;

import com.easypan.entity.enums.VerifyRegexEnum;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

//用于匹配参数值和正则表达式
public class VerifyUtils {
    //参数分别为正则和具体的参数值
    public static Boolean verify(String regs, String value){
        if(StringTools.isEmpty(value)){
            return false;
        }
        Pattern pattern = Pattern.compile(regs);
        Matcher matcher = pattern.matcher(value);
        return matcher.matches();
    }

    public static Boolean verify(VerifyRegexEnum regs,String value){
        return verify(regs.getRegex(),value);
    }
}
