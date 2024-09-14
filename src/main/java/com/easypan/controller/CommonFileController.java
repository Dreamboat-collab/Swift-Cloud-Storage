package com.easypan.controller;

import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.DownloadFileDto;
import com.easypan.entity.enums.FileCategoryEnums;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.enums.ResponseCodeEnum;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.query.FileInfoQuery;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.exception.BusinessException;
import com.easypan.service.FileInfoService;
import com.easypan.utils.CopyTools;
import com.easypan.utils.StringTools;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.datatransfer.FlavorEvent;
import java.io.File;
import java.net.URLEncoder;
import java.util.List;

public class CommonFileController extends ABaseController {

    @Resource
    protected FileInfoService fileInfoService;

    @Resource
    protected AppConfig appConfig;

    @Resource
    private RedisComponent redisComponent;

    //获取指定文件位置、文件名的图片
    protected void getImage(HttpServletResponse response, String imageFolder, String imageName) {
        if (StringTools.isEmpty(imageFolder) || StringUtils.isBlank(imageName)) {
            return;
        }
        String imageSuffix = StringTools.getFileSuffix(imageName);
        //图片的路径
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + imageFolder + "/" + imageName;
        imageSuffix = imageSuffix.replace(".", "");
        String contentType = "image/" + imageSuffix;
        response.setContentType(contentType);  //e.g. image/jpg
        response.setHeader("Cache-Control", "max-age=2592000"); //设置缓存有效期30天
        readFile(response, filePath);
    }

    //path为前端传递的file_id，可能有多级目录，e.g. path=dnCxCoL5cU/Iz2lpSKnRw
    protected ResponseVO getFolderInfo(String path, String userId) {
        String[] pathArray = path.split("/");
        FileInfoQuery infoQuery = new FileInfoQuery();
        infoQuery.setUserId(userId);
        infoQuery.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        infoQuery.setFileIdArray(pathArray); //设置层级目录path属性，之后的查询语句中会通过此条件查询出新建目录相关联的父级目录
        //最终的查询语句类似于：SELECT * FROM file_info WHERE file_id IN("yasda", "dasdasd") ORDER BY FIELD(file_id, "dasdasd", "yasda", ...);
        String orderBy = "field(file_id,\"" + StringUtils.join(pathArray, "\",\"") + "\")"; //这里依据file_id进行排序，层级高的目录传参时在前面
        infoQuery.setOrderBy(orderBy);
        List<FileInfo> fileInfoList = fileInfoService.findListByParam(infoQuery);
        return getSuccessResponseVO(fileInfoList);
    }

    //创建下载code
    protected ResponseVO createDownloadUrl(String fileId, String userId){
        FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(fileId, userId);
        if(null == fileInfo){
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if(FileFolderTypeEnums.FOLDER.getType().equals(fileInfo.getFolderType())){  //传入的是文件夹
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String code = StringTools.getRandomString(Constants.LENGTH_50);

        DownloadFileDto downloadFileDto = new DownloadFileDto();
        downloadFileDto.setDownloadCode(code);
        downloadFileDto.setFileName(fileInfo.getFileName());
        downloadFileDto.setFilePath(fileInfo.getFilePath());

        redisComponent.saveDownloadCode(code, downloadFileDto); //将下载文件的code信息保存到redis，过期时间为5min
        return getSuccessResponseVO(code);  //前端拿到code去调用真正进行下载的接口
    }

    //下载文件
    protected void download(HttpServletRequest request, HttpServletResponse response, String code) throws Exception {
        DownloadFileDto downloadFileDto = redisComponent.getDownloadCode(code);
        if(null == downloadFileDto){
            return;
        }
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + downloadFileDto.getFilePath();  //要下载的文件的实际地址
        String fileName = downloadFileDto.getFileName();
        //设定HTTP 响应的 MIME 类型（媒体类型），此处表示可下载的文件
        response.setContentType("application/x-msdownload;charset=utf-8");
        //针对不同浏览器，设置文件名编码
        if (request.getHeader("User-Agent").toLowerCase().indexOf("msie") > 0) {//IE浏览器
            fileName = URLEncoder.encode(fileName, "UTF-8");
        } else {
            fileName = new String(fileName.getBytes("UTF-8"), "ISO8859-1"); //将字符串 fileName 从 UTF-8 编码转换为 ISO8859-1
        }
        // 设置响应头，指示浏览器以附件形式下载文件，并指定文件名
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        readFile(response, filePath); //将filePath的文件内容写入response的输出流
    }

}
