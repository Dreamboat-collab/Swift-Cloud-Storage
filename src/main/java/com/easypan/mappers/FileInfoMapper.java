package com.easypan.mappers;

import com.easypan.entity.po.FileInfo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

	/**
	 * 批量删除文件夹
	 */
	void updateFileDelFlagBatch(@Param("bean") FileInfo fileInfo, @Param("userId") String userId,
								@Param("filePidList") List<String> filePidList, @Param("fileIdList") List<String> fileIdList,
								@Param("oldDelFlag") Integer oldDelFlag);
}
