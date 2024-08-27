package com.easypan.component;

import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SysSettingsDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.query.FileInfoQuery;
import com.easypan.mappers.FileInfoMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component("rediComponent")
public class RedisComponent {
    @Resource
    private RedisUtils redisUtils;
    @Resource
    private FileInfoMapper<FileInfo, FileInfoQuery> fileInfoMapper;

    //SysSettingsDto是系统配置类
    public SysSettingsDto getSysSettingDto() {
        //先尝试在Redis里取系统配置，如果没有取到，则直接new一个,并且存入Redis
        SysSettingsDto sysSettingsDto = (SysSettingsDto) redisUtils.get(Constants.REDIS_KEY_SYS_SETTING);
        if(sysSettingsDto==null){
            sysSettingsDto=new SysSettingsDto();
            redisUtils.set(Constants.REDIS_KEY_SYS_SETTING,sysSettingsDto);
        }
        return sysSettingsDto;
    }

    //保存用户使用空间信息
    public void saveUserSpaceUse(String userId, UserSpaceDto userSpaceDto){
        redisUtils.setex(Constants.REDIS_KEY_USER_SPACE_USE+userId,userSpaceDto,Constants.REDIS_KEY_EXPIRES_DAY);
    }

    //获取用户使用空间
    public UserSpaceDto getUserSpaceUse(String userId){
        UserSpaceDto spaceDto = (UserSpaceDto) redisUtils.get(Constants.REDIS_KEY_USER_SPACE_USE+userId);
        if(spaceDto==null){
            spaceDto=new UserSpaceDto();
            Long useSpace = fileInfoMapper.selectUseSpace(userId);
            spaceDto.setUseSpace(useSpace);
            spaceDto.setTotalSpace(getSysSettingDto().getUserInitSpace()*Constants.MB);
            saveUserSpaceUse(userId,spaceDto);
        }
        return spaceDto;
    }

    //保存Redis中已存放的临时文件的大小
    public void saveFileTempSize(String userId, String fileId, Long fileSize){
        //已存放的临时文件的大小
        Long currentSize = getFileTempSize(userId,fileId);
        //保存已存放的临时文件的大小+当前文件大小,setex中第三个参数是过期时间
        redisUtils.setex(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE+userId+fileId,currentSize+fileSize,Constants.REDIS_KEY_EXPIRES_ONE_HOUR);
    }

    //获取临时文件的大小
    public Long getFileTempSize(String userId, String fileId) {
        Long currentSize = getFileSizeFromRedis(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId);
        return currentSize;
    }

    private Long getFileSizeFromRedis(String key) {
        Object sizeObj = redisUtils.get(key);
        if (sizeObj == null) {
            return 0L;
        }
        if (sizeObj instanceof Integer) {
            return ((Integer) sizeObj).longValue();
        } else if (sizeObj instanceof Long) {
            return (Long) sizeObj;
        }

        return 0L;
    }
}
