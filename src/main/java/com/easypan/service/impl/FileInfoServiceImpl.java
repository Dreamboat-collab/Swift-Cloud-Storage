package com.easypan.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.*;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.query.UserInfoQuery;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.utils.DateUtil;
import com.easypan.utils.ProcessUtils;
import com.easypan.utils.ScaleFilter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.easypan.entity.enums.FileTypeEnums;
import com.easypan.entity.query.FileInfoQuery;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.entity.query.SimplePage;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.service.FileInfoService;
import com.easypan.utils.StringTools;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.easypan.entity.constants.Constants;

/**
 * 文件信息表 业务接口实现
 */
@Service("fileInfoService")
public class FileInfoServiceImpl implements FileInfoService {

	private static final Logger logger = LoggerFactory.getLogger(FileInfoServiceImpl.class);

	@Resource
	@Lazy
	private FileInfoServiceImpl fileInfoService;
	@Resource
	private FileInfoMapper<FileInfo, FileInfoQuery> fileInfoMapper;

	@Resource
	private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

	@Resource
	private RedisComponent redisComponent;


	@Resource
	private AppConfig appConfig;

	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<FileInfo> findListByParam(FileInfoQuery param) {
		return this.fileInfoMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(FileInfoQuery param) {
		return this.fileInfoMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<FileInfo> findListByPage(FileInfoQuery param) {
		//获取符合FileInfoQuery查询条件的总记录数
		int count = this.findCountByParam(param);
		//确定每页记录数
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();
		//使用当前页码、总记录数和每页记录数创建一个SimplePage对象。
		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		//设置分页信息
		param.setSimplePage(page);
		List<FileInfo> list = this.findListByParam(param);
		PaginationResultVO<FileInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(FileInfo bean) {
		return this.fileInfoMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<FileInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.fileInfoMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<FileInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.fileInfoMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(FileInfo bean, FileInfoQuery param) {
		StringTools.checkParam(param);
		return this.fileInfoMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(FileInfoQuery param) {
		StringTools.checkParam(param);
		return this.fileInfoMapper.deleteByParam(param);
	}

	/**
	 * 根据FileIdAndUserId获取对象
	 */
	@Override
	public FileInfo getFileInfoByFileIdAndUserId(String fileId, String userId) {
		return this.fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
	}

	/**
	 * 根据FileIdAndUserId修改
	 */
	@Override
	public Integer updateFileInfoByFileIdAndUserId(FileInfo bean, String fileId, String userId) {
		return this.fileInfoMapper.updateByFileIdAndUserId(bean, fileId, userId);
	}

	/**
	 * 根据FileIdAndUserId删除
	 */
	@Override
	public Integer deleteFileInfoByFileIdAndUserId(String fileId, String userId) {
		return this.fileInfoMapper.deleteByFileIdAndUserId(fileId, userId);
	}

	/**
	 * 上传文件
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public UploadResultDto uploadFile(SessionWebUserDto webUserDto, String fileId, MultipartFile file, String fileName, String filePid, String fileMd5, Integer chunkIndex, Integer chunks) {
		UploadResultDto resultDto = new UploadResultDto();
		boolean uploadSuccess = true;
		File tempFileFolder = null;
		try {
			if (StringTools.isEmpty(fileId)) {
				fileId = StringTools.getRandomString(Constants.LENGTH_10);
			}
			resultDto.setFileId(fileId);
			Date curDate = new Date();
			UserSpaceDto spaceDto = redisComponent.getUserSpaceUse(webUserDto.getUserId());
			//chunkIndex为分片的index，为0表示第一个分片，文件还未上传。此时可以判断文件是否已经存在于数据库；若存在则实现秒传
			if (chunkIndex == 0) {
				FileInfoQuery infoQuery = new FileInfoQuery();
				infoQuery.setFileMd5(fileMd5);
				infoQuery.setSimplePage(new SimplePage(0, 1));
				infoQuery.setStatus(FileStatusEnums.USING.getStatus());
				List<FileInfo> dbFileList = this.fileInfoMapper.selectList(infoQuery);
				//秒传
				if (!dbFileList.isEmpty()) {
					FileInfo dbFile = dbFileList.get(0);
					//判断上传当前文件是否会超出用户的使用空间
					if (dbFile.getFileSize() + spaceDto.getUseSpace() > spaceDto.getTotalSpace()) {
						throw new BusinessException(ResponseCodeEnum.CODE_904);
					}
					dbFile.setFileId(fileId);
					//文件的父级，表明文件在哪个文件夹下
					dbFile.setFilePid(filePid);
					dbFile.setUserId(webUserDto.getUserId());
					dbFile.setFileMd5(null);
					dbFile.setCreateTime(curDate);
					dbFile.setLastUpdateTime(curDate);
					dbFile.setStatus(FileStatusEnums.USING.getStatus());
					dbFile.setDelFlag(FileDelFlagEnums.USING.getFlag());
					dbFile.setFileMd5(fileMd5);
					//文件重命名，用户前端上传的文件名可能和数据库中已有文件名相同，为避免冲突需要将上传的文件重命名
					fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
					dbFile.setFileName(fileName);
					this.fileInfoMapper.insert(dbFile);
					//resultDto的status设定为秒传状态
					resultDto.setStatus(UploadStatusEnums.UPLOAD_SECONDS.getCode());
					//更新用户使用空间
					updateUserSpace(webUserDto, dbFile.getFileSize());
					return resultDto;
				}
			}
			//分片上传
			//判断磁盘空间是否足够（文件大小+用户已使用空间+暂存目录大小）
			Long currentTempSize = redisComponent.getFileTempSize(webUserDto.getUserId(), fileId);
			if(file.getSize()+currentTempSize+spaceDto.getUseSpace() > spaceDto.getTotalSpace()){
				throw new BusinessException(ResponseCodeEnum.CODE_904);
			}

			//上述判断完磁盘空间足够，创建暂存的临时目录：用于临时存放文件的分片
			String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP; //project/temp/
			String currentUserFolderName = webUserDto.getUserId() + fileId;
			tempFileFolder = new File(tempFolderName + currentUserFolderName);
			if(!tempFileFolder.exists()){
				tempFileFolder.mkdirs();
			}

			//分片文件目录的创建
			File newFile = new File(tempFileFolder.getPath() + "/" + chunkIndex);
			file.transferTo(newFile); //将MultipartFile对象中的文件内容写入到指定的文件或目录中
			//分片还尚未上传完
			if(chunkIndex<chunks-1){
				resultDto.setStatus(UploadStatusEnums.UPLOADING.getCode());  //uploading
				redisComponent.saveFileTempSize(webUserDto.getUserId(), fileId, file.getSize());
				return resultDto;
			}
			//最后一个分片上传完也要在缓存中更新用户的使用空间
			redisComponent.saveFileTempSize(webUserDto.getUserId(), fileId,file.getSize());

			//最后一个分片上传完成，记录数据库，异步合并分片
			String month = DateUtil.format(new Date(), DateTimePatternEnum.YYYYMM.getPattern());
			String fileSuffix = StringTools.getFileSuffix(fileName);
			//真实文件名
			String realFileName = currentUserFolderName+fileSuffix;
			//文件类型枚举类
			FileTypeEnums fileTypeEnum = FileTypeEnums.getFileTypeBySuffix(fileSuffix);
			//自动重命名(在数据库文件出现重名则自动将其改名)
			fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
			//进行数据库的操作,存入已上传的文件信息
			FileInfo fileInfo = new FileInfo();
			fileInfo.setFileId(fileId);
			fileInfo.setUserId(webUserDto.getUserId());
			fileInfo.setFileMd5(fileMd5);
			fileInfo.setFileName(fileName);
			fileInfo.setFilePath(month + "/" + realFileName);
			fileInfo.setFilePid(filePid);
			fileInfo.setCreateTime(curDate);
			fileInfo.setLastUpdateTime(curDate);
			fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
			fileInfo.setFileType(fileTypeEnum.getType());
			fileInfo.setStatus(FileStatusEnums.TRANSFER.getStatus());
			fileInfo.setFolderType(FileFolderTypeEnums.FILE.getType());
			fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
			this.fileInfoMapper.insert(fileInfo);
			//更新数据库、redis中用户的已使用空间(原来的已使用空间+当前传入的文件大小)
			Long totalSize = redisComponent.getFileTempSize(webUserDto.getUserId(),fileId);
			updateUserSpace(webUserDto, totalSize);

			resultDto.setStatus(UploadStatusEnums.UPLOAD_FINISH.getCode());

			//如果事务回滚，则不会调用此方法;若下述代码成功运行，则表明此事务中的所有数据库操作成功运行
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					//执行转码操作
					fileInfoService.transferFile(fileInfo.getFileId(), webUserDto);
				}
			});

			return resultDto;
		}catch (BusinessException e) {
			uploadSuccess = false;
			logger.error("文件上传失败", e);
			throw e;
		} catch (Exception e) {
			uploadSuccess = false;
			logger.error("文件上传失败", e);
			throw new BusinessException("文件上传失败");
		}finally {
			if(!uploadSuccess && tempFileFolder!=null){
				//上传失败，删除temp临时目录
				try {
					FileUtils.deleteDirectory(tempFileFolder);
				}catch (IOException e){
					logger.error("删除临时目录失败",e);
				}
			}
		}
	}

	/**
	 * 上自动重命名
	 */
	private String autoRename(String filePid, String userId, String fileName) {
		//查找数据库有无重复文件名
		FileInfoQuery fileInfoQuery = new FileInfoQuery();
		fileInfoQuery.setUserId(userId);
		fileInfoQuery.setFilePid(filePid);
		fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
		fileInfoQuery.setFileName(fileName);
		Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
		if(count>0){
			return StringTools.rename(fileName);
		}
		//若上述没有查出重复的文件名，则直接返回用户命名的文件名
		return fileName;
	}

	//useSpace参数：表示文件的大小
	private void updateUserSpace(SessionWebUserDto webUserDto,Long useSpace){
		//更新数据库中用户已使用的空间、总空间
		Integer count = userInfoMapper.updateUserSpace(webUserDto.getUserId(),useSpace,null);
		if(count == 0){
			//用户空间不足
			throw new BusinessException(ResponseCodeEnum.CODE_904);
		}
		//更新缓存中用户已使用的空间
		UserSpaceDto userSpaceDto = redisComponent.getUserSpaceUse(webUserDto.getUserId());
		userSpaceDto.setUseSpace(userSpaceDto.getUseSpace()+useSpace);
		redisComponent.saveUserSpaceUse(webUserDto.getUserId(), userSpaceDto);
	}

	//合并分片文件。
	@Async //异步方法执行的注解.调用这个方法时，Spring 会在一个单独的线程中运行它，而不是在调用者线程中直接执行.使用 @Async 可以将这些耗时的操作放到另一个线程中执行，主线程可以立即返回并处理其他任务，从而提高应用的响应速度。
	public void transferFile(String fileId, SessionWebUserDto webUserDto) {
		Boolean transferSuccess = true;
		String targetFilePath = null;
		String cover = null; //文件封面
		FileTypeEnums fileTypeEnum = null;
		FileInfo fileInfo = this.fileInfoMapper.selectByFileIdAndUserId(fileId,webUserDto.getUserId());
		try {
			//文件状态为转码中
			if(fileInfo == null || !FileStatusEnums.TRANSFER.getStatus().equals(fileInfo.getStatus())){
				return;
			}
			//临时文件目录
			String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
			String currentUserFolderName = webUserDto.getUserId()+fileId;
			File fileFolder = new File(tempFolderName + currentUserFolderName);
			if(!fileFolder.exists()){
				fileFolder.mkdirs();
			}
			//文件后缀
			String fileSuffix = StringTools.getFileSuffix(fileInfo.getFileName());
			String month = DateUtil.format(fileInfo.getCreateTime(),DateTimePatternEnum.YYYYMM.getPattern());

			//目标目录（目标是将temp目录中的分片转到新的目标目录中）
			String targetFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;  //project/file
			File targetFolder = new File(targetFolderName + "/" + month); //project/file/202409
			if(!targetFolder.exists()){
				targetFolder.mkdirs();
			}

			//真实的文件名
			String realFileName = currentUserFolderName+fileSuffix;
			targetFilePath = targetFolder.getPath()+"/"+realFileName;

			//合并文件(合并成功后删除掉temp下的分片内容)   参数：临时文件目录、要写入的目标文件路径、文件名，是否删除temp中源文件
			union(fileFolder.getPath(),targetFilePath,fileInfo.getFileName(),true);

			//视频文件切割
			fileTypeEnum = FileTypeEnums.getFileTypeBySuffix(fileSuffix);
			if(FileTypeEnums.VIDEO==fileTypeEnum){
				cutFile4Video(fileId,targetFilePath);
				//视频生成缩略图
				cover = month+"/"+currentUserFolderName+Constants.IMAGE_PNG_SUFFIX;
				String coverPath = targetFolderName+"/"+cover;
				//第一个参数是视频文件的路径，第二个参数是视频文件的长宽，第三个是视频缩略图的路径
				ScaleFilter.createCover4Video(new File(targetFilePath), Constants.LENGTH_150, new File(coverPath));
			}else if(FileTypeEnums.IMAGE==fileTypeEnum){
				//图片生成缩略图
				cover = month + "/" + realFileName.replace(".","_."); //缩略图的路径，将原图中的图片名末尾添加一个“_”符号以作区分
				String coverPath = targetFolderName+"/"+cover;
				Boolean created = ScaleFilter.createThumbnailWidthFFmpeg(new File(targetFilePath),Constants.LENGTH_150,new File(coverPath),false); //缩略图长宽为50
				if(!created){
					FileUtils.copyFile(new File(targetFilePath), new File(coverPath));//可能存在文件图片过小的问题导致缩略图生成失败，因此直接将原图拷贝一份作为缩略图
				}
			}
		}catch (Exception e){
			logger.error("文件转码失败，文件ID:{},userId:{}",fileId,webUserDto.getUserId());
			transferSuccess=false;
		} finally {
			FileInfo updateInfo = new FileInfo();
			updateInfo.setFileSize(new File(targetFilePath).length()); //File 类的 length() 方法用于获取指定文件的大小
			updateInfo.setFileCover(cover);
			//更新文件状态
			updateInfo.setStatus(transferSuccess ? FileStatusEnums.USING.getStatus() : FileStatusEnums.TRANSFER_FAIL.getStatus());
			fileInfoMapper.updateByFileIdAndUserId(updateInfo,fileId, webUserDto.getUserId());
		}
	}

	//dirPath是tmp目录的路径; toFilePath是合并分片后存储到的新文件
	private void union(String dirPath, String toFilePath, String fileName, Boolean delSource){
		File dir = new File(dirPath);
		if(!dir.exists()){
			throw new BusinessException("目录不存在");
		}
		//temp目录下的分片文件
		File[] fileList = dir.listFiles(); //获取该目录下的所有文件列表
		File targetFile = new File(toFilePath);
		RandomAccessFile writeFile = null;
		try {
			//new RandomAccessFile指定读写的权限
			writeFile = new RandomAccessFile(targetFile,"rw");
			//10KB大小的缓冲区
			byte[] b =new byte[1024 * 10];
			//遍历temp中的每个分片文件
			for(int i=0;i<fileList.length;i++){
				int len =-1;
				File chunkFile = new File(dirPath + "/" + i);
				RandomAccessFile readFile = null;
				try {
					readFile = new RandomAccessFile(chunkFile,"r"); //指定读权限
					while ((len = readFile.read(b))!=-1){
						writeFile.write(b,0,len); //将字节数组 b 中的内容写入到目标文件 writeFile 中,第二个参数表示从数组中哪个位置开始写入，第三个参数表示终止位置
					}
				} catch (Exception e){
					logger.error("合并分片失败",e);
					throw new BusinessException("合并分片失败");
				} finally{
					readFile.close();
				}
			}
		} catch (Exception e){
			logger.error("合并文件:{}失败", fileName,e);
			throw new BusinessException("合并文件"+fileName+"出错了");
		} finally {
			if(null != writeFile){
				try {
					writeFile.close();
				} catch (IOException e){
					e.printStackTrace();
				}
			}
			//删除temp目录下该文件的分片内容
			if(delSource && dir.exists()){
				try{
					FileUtils.deleteDirectory(dir);
				} catch (Exception e){
					e.printStackTrace();
				}
			}
		}
	}

	//视频切割
	public void cutFile4Video(String fileId, String videoFilePath){
		//创建同名切片目录,生成的 TS 文件和切片存放的目录
		File tsFolder = new File(videoFilePath.substring(0,videoFilePath.lastIndexOf(".")));
		if(!tsFolder.exists()){
			tsFolder.mkdirs();
		}
		//这个命令用于将输入的视频文件转换为 TS 格式，
		final String CMD_TRANSFER_2TS = "ffmpeg -y -i %s  -vcodec copy -acodec copy -vbsf h264_mp4toannexb %s";
		//用于将一个视频文件（通常是 TS 格式）切割成多个小片段，并生成一个播放列表文件
		final String CMD_CUT_TS = "ffmpeg -i %s -c copy -map 0 -f segment -segment_list %s -segment_time 30 %s/%s_%%4d.ts";
		String tsPath = tsFolder + "/" + Constants.TS_NAME;//生成.ts的路径
		String cmd = String.format(CMD_TRANSFER_2TS, videoFilePath, tsPath);//将输入的视频文件转换为 TS 格式并输出到 tsPath。
		ProcessUtils.executeCommand(cmd, false);
		//生成索引文件.m3u8 和切片.ts
		cmd = String.format(CMD_CUT_TS, tsPath, tsFolder.getPath() + "/" + Constants.M3U8_NAME, tsFolder.getPath(), fileId);
		ProcessUtils.executeCommand(cmd, false);
		//删除index.ts
		new File(tsPath).delete();
	}

	//创建新的目录
	@Override
	public FileInfo newFolder(String filePid, String userId, String folderName) {
		checkFileName(filePid,userId,folderName,FileFolderTypeEnums.FILE.getType());
		FileInfo fileInfo = new FileInfo();
		Date curDate = new Date();
		fileInfo.setFileId(StringTools.getRandomString(Constants.LENGTH_10));
		fileInfo.setFilePid(filePid);
		fileInfo.setUserId(userId);
		fileInfo.setFileName(folderName);
		fileInfo.setFolderType(FileFolderTypeEnums.FOLDER.getType());
		fileInfo.setCreateTime(curDate);
		fileInfo.setLastUpdateTime(curDate);
		fileInfo.setStatus(FileStatusEnums.USING.getStatus());
		fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
		fileInfoMapper.insert(fileInfo);
		return fileInfo;
	}

    //检查是否同级目录下存在命名重复(参数中folderType表示是目录还是文件类型)
	private void checkFileName(String filePid, String userId, String fileName, Integer folderType) {
		FileInfoQuery fileInfoQuery = new FileInfoQuery();
		fileInfoQuery.setFolderType(folderType);
		fileInfoQuery.setFileName(fileName);
		fileInfoQuery.setFilePid(filePid);
		fileInfoQuery.setUserId(userId);
		fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
		Integer count = this.fileInfoMapper.selectCount(fileInfoQuery);
		if (count > 0) {
			throw new BusinessException("此目录下已存在同名文件，请修改名称");
		}
	}

	//文件重命名
	@Transactional(rollbackFor = Exception.class) //数据库查询到出现同名文件，需要回滚之前的update操作
	@Override
	public FileInfo rename(String fileId, String userId, String fileName) {
		//判断文件是否存在
		FileInfo fileInfo = fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
		if(null == fileInfo){
			throw new BusinessException("文件命名重复");
		}

		String filePid = fileInfo.getFilePid();
		//判断名称在同级目录下是否存在重复
		checkFileName(filePid,userId,fileName,fileInfo.getFolderType());
		//获取文件后缀，因为前端传来的文件名是不带后缀的，因此需要将后缀补充上
		if(FileFolderTypeEnums.FILE.getType().equals(fileInfo.getFolderType())){
			fileName = fileName + StringTools.getFileSuffix(fileInfo.getFileName());
		}

		//更新数据库对应的文件名、修改时间
		Date curDate = new Date();
		FileInfo fileInfo1 = new FileInfo();
		fileInfo1.setLastUpdateTime(curDate);
		fileInfo1.setFileName(fileName);
		fileInfoMapper.updateByFileIdAndUserId(fileInfo1,fileId,userId);

		//插入数据后查询数据库是否出现文件重名问题
		FileInfoQuery fileInfoQuery = new FileInfoQuery();
		fileInfoQuery.setFilePid(filePid);
		fileInfoQuery.setFileName(fileName);
		fileInfoQuery.setUserId(userId);
		Integer count = fileInfoMapper.selectCount(fileInfoQuery);
		if(count > 0){
			throw new BusinessException("出现文件重名错误");
		}
		fileInfo.setFileName(fileName);
		fileInfo.setLastUpdateTime(curDate);
		return fileInfo;
	}

	@Override
	public void changeFileFolder(String fileIds, String filePid, String userId) {
		if(fileIds.equals(filePid)){
			throw new BusinessException(ResponseCodeEnum.CODE_600);  //要移动的内容和将要移动到的地方重合了
		}
		//非根目录
		if(!Constants.ZERO_STR.equals(filePid)){
			FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(filePid,userId);
			if(null == fileInfo || !FileDelFlagEnums.USING.getFlag().equals(fileInfo.getDelFlag())){
				throw new BusinessException(ResponseCodeEnum.CODE_600); //目标移动目录不存在或者已经被删除
			}
		}

		//目标移动目录中是否存在和被移动文件同名的文件
		String[] fileIdArray = fileIds.split(",");
		FileInfoQuery fileInfoQuery = new FileInfoQuery();
		fileInfoQuery.setFilePid(filePid);
		fileInfoQuery.setUserId(userId);
		List<FileInfo> dbFileList = fileInfoMapper.selectList(fileInfoQuery); //找到的目标移动目录下的文件集合
		//FileInfo文件名作为键，FileInfo作为值；当出现两个同名的FileInfo，保留后出现的FileInfo对象
		Map<String, FileInfo> dbFileNameMap = dbFileList.stream().collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));

		//查询选中的文件
		fileInfoQuery = new FileInfoQuery();
		fileInfoQuery.setFileIdArray(fileIdArray);
		fileInfoQuery.setUserId(userId);
		List<FileInfo> selectFileList  = fileInfoService.findListByParam(fileInfoQuery);

		//将所选文件重命名
		for(FileInfo item : selectFileList){
			FileInfo rootFileInfo = dbFileNameMap.get(item.getFileId());
			//文件名已存在,重命名目标目录下的文件名
			FileInfo updateInfo = new FileInfo();
			if(null != rootFileInfo){
				String fileName = StringTools.rename(item.getFileName());
				updateInfo.setFileName(fileName);
			}
			updateInfo.setFilePid(item.getFilePid());
			fileInfoMapper.updateByFileIdAndUserId(updateInfo,item.getUserId(),userId);  //如果没有重名，只需要修改被移动文件的pid属性
		}

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeFile2RecycleBatch(String userId, String fileIds) {
		String[] fileIdArray = fileIds.split(",");
		FileInfoQuery fileInfoQuery = new FileInfoQuery();
		fileInfoQuery.setFileIdArray(fileIdArray);
		fileInfoQuery.setUserId(userId);
		fileInfoQuery.setDelFlag(FileDelFlagEnums.USING.getFlag());
		List<FileInfo> fileInfoList = fileInfoMapper.selectList(fileInfoQuery);
		if(fileInfoList.isEmpty()){
			return;
		}

		//递归删除所选内容中类型为文件夹下的内容，将他们的fileId作为filePid存储在数组
		List<String> delFilePidList = new ArrayList<>(); //此集合中的文件夹下的内容需更新为已删除
		for(FileInfo item : fileInfoList){
			traverDel(delFilePidList, userId, item.getFileId(), FileDelFlagEnums.USING.getFlag());
		}

		//将需要删除的文件夹下的内容更新为已删除
		if(!delFilePidList.isEmpty()){
			FileInfo updateInfo = new FileInfo();
			updateInfo.setDelFlag(FileDelFlagEnums.DEL.getFlag());
			fileInfoMapper.updateFileDelFlagBatch(updateInfo,userId,delFilePidList,null,FileDelFlagEnums.USING.getFlag());
		}
		//所选内容更新为已删除（包括文件、文件夹）
		List<String> delFileIdList = Arrays.asList(fileIdArray);
		FileInfo fileInfo = new FileInfo();
		fileInfo.setRecoveryTime(new Date());
		fileInfo.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
		this.fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, null, delFileIdList, FileDelFlagEnums.USING.getFlag());
	}
	//递归删除文件夹内容，将找到的可删除的文件夹id放入delFilePidList
	private void traverDel(List<String> delFilePidList, String userId, String fileId, Integer delFlag){
		if(fileId==null || fileId.isEmpty()){
			return;
		}
		delFilePidList.add(fileId);
		FileInfoQuery fileInfoQuery = new FileInfoQuery();
		fileInfoQuery.setUserId(userId);
		fileInfoQuery.setFilePid(fileId);
		fileInfoQuery.setDelFlag(delFlag);
		fileInfoQuery.setFolderType(FileFolderTypeEnums.FOLDER.getType());  //只有文件夹类型才需要递归删除
		List<FileInfo> fileInfoList = fileInfoMapper.selectList(fileInfoQuery);
		for(FileInfo item : fileInfoList){
			traverDel(delFilePidList,userId,item.getFileId(),delFlag);
		}
	}
}
