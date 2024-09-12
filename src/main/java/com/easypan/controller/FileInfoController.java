package com.easypan.controller;

import java.util.List;

import com.easypan.annotation.VerifyParam;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.enums.FileCategoryEnums;
import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.query.FileInfoQuery;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.vo.FileInfoVO;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.service.FileInfoService;
import com.easypan.utils.StringTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import com.easypan.annotation.GlobalInterceptor;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件信息表 Controller
 */
@RestController("fileInfoController")
@RequestMapping("/file")
public class FileInfoController extends CommonFileController{

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

	//获取图片的缩略图
	@RequestMapping("/getImage/{imageFolder}/{imageName}")
	@GlobalInterceptor(checkParams = true)
	public void getImage(HttpServletResponse response, @PathVariable("imageFolder") String imageFolder, @PathVariable("imageName") String imageName) {
		super.getImage(response, imageFolder, imageName);
	}

	//创建新的文件目录(为什么需要filePid：因为用户可能创建多级目录，需要知道目录的父目录已得知其层级)
	@RequestMapping("/newFoloder")
	@GlobalInterceptor(checkParams = true)
	public ResponseVO newFolder(HttpSession session, @VerifyParam(required = true) String filePid, @VerifyParam(required = true) String fileName) {
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		FileInfo fileInfo = fileInfoService.newFolder(filePid, sessionWebUserDto.getUserId(), fileName);
		return getSuccessResponseVO(fileInfo);
	}

	//获取当前目录下的内容（用户点入文件夹后需要显示该文件夹下的内容） 参数path：文件ID
	@RequestMapping("/getFolderInfo")
	@GlobalInterceptor(checkParams = true)
	public ResponseVO getFolderInfo(HttpSession session, @VerifyParam(required = true) String path) {
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		return super.getFolderInfo(path, sessionWebUserDto.getUserId()); //后续很多功能都需要用到getFolderInfo，因此直接写在父类中
	}

	//文件重命名
	@RequestMapping("/rename")
	@GlobalInterceptor(checkParams = true)
	public ResponseVO rename(HttpSession session, @VerifyParam(required = true) String fileId, @VerifyParam(required = true) String fileName) {
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		FileInfo fileInfo = fileInfoService.rename(fileId, sessionWebUserDto.getUserId(), fileName);
		return getSuccessResponseVO(fileInfo);
	}


	//用户移动文件时，点开某文件夹，会显示文件夹中的内容进行预览；第二个参数用于对当前文件/文件夹进行过滤，因为是移动操作，不能从本文件移动到本文件
	@RequestMapping("loadAllFolder")
	@GlobalInterceptor(checkParams = true)
	public ResponseVO loadAllFolder(HttpSession session, @VerifyParam(required = true) String filePid, String currentFileIds) {
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		FileInfoQuery fileInfoQuery = new FileInfoQuery();
		fileInfoQuery.setFilePid(filePid);
		fileInfoQuery.setUserId(sessionWebUserDto.getUserId());
		fileInfoQuery.setFolderType(FileFolderTypeEnums.FOLDER.getType());  //移动文件是移动到文件夹中，因此查询返回的类型应该是文件夹类型
		if(!StringTools.isEmpty(currentFileIds)){
			fileInfoQuery.setExcludeFileIdArray(currentFileIds.split(","));
		}
		fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
		fileInfoQuery.setOrderBy("create_time desc");
		List<FileInfo> list = fileInfoService.findListByParam(fileInfoQuery);
		return getSuccessResponseVO(list);
	}

	//移动文件;  第一个参数是多个fileId组成的String，第二个是要移动到的文件夹id
	@RequestMapping("/changeFileFolder")
	@GlobalInterceptor(checkParams = true)
	public ResponseVO changeFileFolder(HttpSession session, @VerifyParam(required = true) String fileIds, @VerifyParam String filePid) {
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		fileInfoService.changeFileFolder(fileIds, filePid, sessionWebUserDto.getUserId());
		return getSuccessResponseVO(null);
	}
}
