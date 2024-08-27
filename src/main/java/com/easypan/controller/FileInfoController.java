package com.easypan.controller;

import java.util.List;

import com.easypan.annotation.VerifyParam;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.enums.FileCategoryEnums;
import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.query.FileInfoQuery;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.vo.FileInfoVO;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.service.FileInfoService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import com.easypan.annotation.GlobalInterceptor;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件信息表 Controller
 */
@RestController("fileInfoController")
@RequestMapping("/file")
public class FileInfoController extends ABaseController{

	@Resource
	private FileInfoService fileInfoService;
	/**
	 * 根据条件分页查询，返回分页查询结果的实体对象，即文件列表
	 */
	@RequestMapping("/loadDataList")
	@GlobalInterceptor(checkParams = true)
	//将查询的字段封装在FileInfoQuery实体类
	public ResponseVO loadDataList(HttpSession session, FileInfoQuery query, String category){
		//category：video，music，image...
		FileCategoryEnums categoryEnums = FileCategoryEnums.getByCode(category);
		if(categoryEnums!=null){
			//将字符串型的category转化为数字类型
			query.setFileCategory(categoryEnums.getCategory());
		}

		query.setUserId(getUserInfoFromSession(session).getUserId());
		//以文件最后的更新时间为依据，降序排序
		query.setOrderBy("last_update_time desc");
		query.setDelFlag(FileDelFlagEnums.USING.getFlag());
		PaginationResultVO result = fileInfoService.findListByPage(query);
		//将PaginationResultVO对象转化为FileInfoVO对象
		return getSuccessResponseVO(convert2PaginationVO(result, FileInfoVO.class));
	}

	//上传文件
	@RequestMapping("/uploadFile")
	@GlobalInterceptor(checkParams = true)
	//chunkIndex表示第几个分片；chunks表示分片的数量
	public ResponseVO uploadFile(HttpSession session,
								 String fileId,
								 MultipartFile file,
								 @VerifyParam(required = true) String fileName,
								 @VerifyParam(required = true) String filePid,
								 @VerifyParam(required = true) String fileMd5,
								 @VerifyParam(required = true) Integer chunkIndex,
								 @VerifyParam(required = true) Integer chunks) {

		SessionWebUserDto webUserDto = getUserInfoFromSession(session);
		UploadResultDto resultDto = fileInfoService.uploadFile(webUserDto, fileId, file, fileName, filePid, fileMd5, chunkIndex, chunks);
		return getSuccessResponseVO(resultDto);
	}
}
