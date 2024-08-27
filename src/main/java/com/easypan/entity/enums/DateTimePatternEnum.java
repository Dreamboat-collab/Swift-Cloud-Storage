package com.easypan.entity.enums;


public enum DateTimePatternEnum {
    //枚举常量（enum constant）是枚举类中定义的固定的常量值。每个枚举常量都是枚举类的一个实例，具有独特的名称和相关的值。其参数传入枚举类 DateTimePatternEnum 的构造函数中的
    YYYY_MM_DD_HH_MM_SS("yyyy-MM-dd HH:mm:ss"), YYYY_MM_DD("yyyy-MM-dd"), YYYYMM("yyyyMM");

    private String pattern;

    DateTimePatternEnum(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }
}
