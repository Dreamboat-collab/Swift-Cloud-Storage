package com.easypan.mappers;

import com.easypan.entity.po.FileInfo;
import com.easypan.entity.po.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 *  数据库操作接口
 */
@Mapper
public interface UserInfoMapper<T,P> extends BaseMapper<T,P> {

	/**
	 * 根据UserId更新
	 */
	 Integer updateByUserId(@Param("bean") T t,@Param("userId") String userId);


	/**
	 * 根据UserId删除
	 */
	 Integer deleteByUserId(@Param("userId") String userId);


	/**
	 * 根据UserId获取对象
	 */
	 T selectByUserId(@Param("userId") String userId);

	/**
	 * 根据Email获取对象
	 */
	UserInfo selectByEmail(@Param("email") String email);

	/**
	 * 根据Email更新
	 */
	Integer updateByEmail(@Param("bean") T t, @Param("email") String email);

	/**
	 * 根据NickName更新
	 */
	Integer updateByNickName(@Param("bean") T t, @Param("nickName") String nickName);


	/**
	 * 根据NickName删除
	 */
	Integer deleteByNickName(@Param("nickName") String nickName);


	/**
	 * 根据NickName获取对象
	 */
	T selectByNickName(@Param("nickName") String nickName);

	/**
	 * 更新用户使用空间
	 */
	Integer updateUserSpace(@Param("userId") String userId, @Param("useSpace") Long useSpace, @Param("totalSpace") Long totalSpace);


}
