package com.easypan.entity.constants;

public class Constants {
    public static final Integer LENGTH_5 = 5;

    public static final Integer LENGTH_10 = 10;

    public static final Integer LENGTH_15 = 15;

    public static final Integer LENGTH_150 = 150;


    public static final Integer ZERO = 0;

    public static final String SESSION_KEY = "session_key";

    public static final Long MB = 1024 * 1024L;

    //文件地址
    public static final String FILE_FOLDER_FILE = "/file/";

    //文件暂存的地址
    public static final String FILE_FOLDER_TEMP = "/temp/";

    public static final String FILE_FOLDER_AVATAR_NAME = "avatar/";

    public static final String AVATAR_SUFFIX = ".jpg";

    //默认头像
    public static final String AVATAR_DEFAULT = "default_avatar.jpg";

    //存储生成的登录验证码以及邮箱验证码
    public static final String CHECK_CODE_KEY = "check_code_key";

    public static final String CHECK_CODE_KEY_EMAIL = "check_code_key_email";

    public static final Integer REDIS_KEY_EXPIRES_ONE_MIN = 60;

    public static final Integer REDIS_KEY_EXPIRES_DAY = REDIS_KEY_EXPIRES_ONE_MIN*60*24;

    public static final Integer REDIS_KEY_EXPIRES_ONE_HOUR = REDIS_KEY_EXPIRES_ONE_MIN*60;

    //Redis中系统设置的key
    public static final String REDIS_KEY_SYS_SETTING = "easypan:syssetting:";

    //Redis中存储用户使用空间情况
    public static final String REDIS_KEY_USER_SPACE_USE = "easypan:user:spaceuse:";

    //Redis中存储临时文件
    public static final String REDIS_KEY_USER_FILE_TEMP_SIZE = "easypan:user:file:temp:";

    public static final String TS_NAME = "index.ts";

    public static final String M3U8_NAME = "index.m3u8";

    public static final String IMAGE_PNG_SUFFIX = ".png";
}
