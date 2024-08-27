package com.easypan.mappers;

import org.apache.ibatis.annotations.Param;

/**
 * 文件信息表 数据库操作接口
 */
public interface FileInfoMapper<T,P> extends BaseMapper<T,P> {

	/**
	 * 根据FileIdAndUserId更新
	 */
	 Integer updateByFileIdAndUserId(@Param("bean") T t,@Param("fileId") String fileId,@Param("userId") String userId);


	/**
	 * 根据FileIdAndUserId删除
	 */
	 Integer deleteByFileIdAndUserId(@Param("fileId") String fileId,@Param("userId") String userId);


	/**
	 * 根据FileIdAndUserId获取对象
	 */
	 T selectByFileIdAndUserId(@Param("fileId") String fileId,@Param("userId") String userId);

	/**
	 * 查询用户已使用的空间
	 */
	Long selectUseSpace(@Param("userId") String userId);
}
