package com.easypan.entity.enums;

//前端传递的是video，music等字符，需要将他们转化为对应的数字
public enum FileCategoryEnums {
    //枚举常量
    VIDEO(1, "video", "视频"),
    MUSIC(2, "music", "音频"),
    IMAGE(3, "image", "图片"),
    DOC(4, "doc", "文档"),
    OTHERS(5, "others", "其他");

    private Integer category;
    private String code;
    private String desc;

    FileCategoryEnums(Integer category, String code, String desc) {
        this.category = category;
        this.code = code;
        this.desc = desc;
    }

    //通过code获取枚举常量
    public static FileCategoryEnums getByCode(String code) {
        //values()方法是Java编译器为每个枚举类型自动生成的一个静态方法。它返回一个包含枚举类型所有常量的数组。
        for (FileCategoryEnums item : FileCategoryEnums.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return null;
    }

    public Integer getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }
}
