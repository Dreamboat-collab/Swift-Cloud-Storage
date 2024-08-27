package com.easypan.controller;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.CreateImageCode;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.VerifyRegexEnum;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.exception.BusinessException;
import com.easypan.service.EmailCodeService;
import com.easypan.service.UserInfoService;
import com.easypan.utils.StringTools;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 *  Controller
 */
@RestController("userInfoController")
public class AccountController extends ABaseController{
	@Resource
	EmailCodeService emailCodeService;

	@Resource
	UserInfoService userInfoService;

	@Resource
	RedisComponent redisComponent;

	@Resource
	AppConfig appConfig;
	private static final Logger logger = LoggerFactory.getLogger(ABaseController.class);
	private static final String CONTENT_TYPE = "Content-Type";
	private static final String CONTENT_TYPE_VALUE = "application/json;charset=UTF-8";

	//图形验证码生成      type:0:登录注册 1:邮箱验证码
	@RequestMapping(value = "/checkCode")
	public void checkCode(HttpServletResponse response, HttpSession session, @RequestParam Integer type) throws
			IOException {
		CreateImageCode vCode = new CreateImageCode(130, 38, 5, 10);
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Cache-Control", "no-cache");
		response.setDateHeader("Expires", 0);
		response.setContentType("image/jpeg");
		//验证码字符串
		String code = vCode.getCode();
		//根据 type 参数的值，将验证码字符串存储在会话的不同属性中,用于区分验证码的不同使用场景：登陆注册/邮箱图形验证码
		if (type == null || type == 0) {
			session.setAttribute(Constants.CHECK_CODE_KEY, code);
		} else {
			session.setAttribute(Constants.CHECK_CODE_KEY_EMAIL, code);
		}
		//将生成的验证码图片写入响应的输出流中，发送给客户端。
		vCode.write(response.getOutputStream());
	}

	//发送邮箱验证码（尚未校验）   checkcode:用户输入的验证码   type:0:注册 1:找回密码
	@RequestMapping("/sendEmailCode")
	@GlobalInterceptor(checkParams = true,checkLogin = false)
	public ResponseVO sendEmailCode(HttpSession session, @VerifyParam(required = true,regex = VerifyRegexEnum.EMAIL,max = 150) String email, String checkCode, Integer type){
		//发送邮箱验证码之前，需要先验证用户在前端输入的图形验证码是否正确
		try{
			if(!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY_EMAIL))){
				throw new BusinessException("图片验证码不正确");
			}
			//给邮箱发送验证码
			emailCodeService.sendEmailCode(email,type);
			return getSuccessResponseVO(null);
		}
		finally {
			session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL);
		}
	}

	//注册(参数：emailCode是邮箱验证码)
	@RequestMapping("/register")
	@GlobalInterceptor(checkParams = true,checkLogin = false)
	public ResponseVO register(HttpSession session, @VerifyParam(required = true,regex = VerifyRegexEnum.EMAIL,max = 150) String email, @VerifyParam(required = true) String nickName, @VerifyParam(required = true,regex = VerifyRegexEnum.PASSWORD,max = 18) String password, @VerifyParam(required = true) String checkCode, @VerifyParam(required = true) String emailCode){
		try{
			if(!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))){
				throw new BusinessException("图片验证码不正确");
			}
			userInfoService.register(email,nickName,password,emailCode);
			return getSuccessResponseVO(null);
		}
		finally {
			session.removeAttribute(Constants.CHECK_CODE_KEY);
		}
	}

	//登录
	@RequestMapping("/login")
	@GlobalInterceptor(checkLogin = false,checkParams = true)
	public ResponseVO login(HttpSession session, @VerifyParam(required = true) String email, @VerifyParam(required = true) String password, @VerifyParam(required = true) String checkCode){
		try{
			if(!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))){
				throw new BusinessException("图片验证码不正确");
			}
			SessionWebUserDto sessionWebUserDto = userInfoService.login(email,password);
			session.setAttribute(Constants.SESSION_KEY,sessionWebUserDto);
			return getSuccessResponseVO(sessionWebUserDto);
		}
		finally {
			session.removeAttribute(Constants.CHECK_CODE_KEY);
		}
	}

	//找回密码
	@RequestMapping("/resetPwd")
	@GlobalInterceptor(checkParams = true,checkLogin = false)
	public ResponseVO resetPwd(HttpSession session, @VerifyParam(required = true) String email, @VerifyParam(required = true) String password, @VerifyParam(required = true) String checkCode, @VerifyParam(required = true) String emailCode){
		try{
			//验证图形验证码
			if(!checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))){
				throw new BusinessException("图片验证码不正确");
			}
			//重置密码
			userInfoService.resetPwd(email,password,emailCode);
			return getSuccessResponseVO(null);
		}
		finally {
			session.removeAttribute(Constants.CHECK_CODE_KEY);
		}
	}

	//获取用户头像
	@RequestMapping("/getAvatar/{userId}")
	@GlobalInterceptor(checkParams = true,checkLogin = false)
	public void getAvatar(HttpServletResponse response, HttpSession session, @VerifyParam(required = true) @PathVariable("userId") String userId) throws IOException {
		//头像所在的文件夹
		String avatarFolderName = Constants.FILE_FOLDER_FILE+Constants.FILE_FOLDER_AVATAR_NAME;
		File folder = new File(appConfig.getProjectFolder()+avatarFolderName);
		//检查目录是否存在
		if(!folder.exists()){
			folder.mkdirs();
		}
		//头像存储具体位置(getProjectFolder为项目地址，avatarFolderName是动漫头像文件地址，AVATAR_SUFFIX是jpg后缀)
		String avatarPath = appConfig.getProjectFolder()+avatarFolderName+userId+Constants.AVATAR_SUFFIX;
		File file = new File(avatarPath);
		//用户头像地址不存在
		if(!file.exists()){
			//默认头像路径不存在
			if(!new File(appConfig.getProjectFolder()+avatarFolderName+Constants.AVATAR_DEFAULT).exists()){
				printNoDefaultImage(response);
				return;
			}
			avatarPath = appConfig.getProjectFolder()+avatarFolderName+Constants.AVATAR_DEFAULT;
		}
		//读取头像流
		response.setContentType("image/jpg");
		readFile(response,avatarPath);
	}

	private void printNoDefaultImage(HttpServletResponse response) {
		response.setHeader(CONTENT_TYPE, CONTENT_TYPE_VALUE);
		response.setStatus(HttpStatus.OK.value());
		PrintWriter writer = null;
		try {
			writer = response.getWriter();
			writer.print("请在头像目录下放置默认头像default_avatar.jpg");
			writer.close();
		} catch (Exception e) {
			logger.error("输出无默认图失败", e);
		} finally {
			writer.close();
		}
	}

	//获取用户信息
	@RequestMapping("/getUserInfo")
	@GlobalInterceptor
	public ResponseVO getUserInfo(HttpSession session){
		//ABaseController中过去用户信息的方法
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		return getSuccessResponseVO(sessionWebUserDto);
	}

	//获取用户使用空间的信息
	@RequestMapping("/getUseSpace")
	@GlobalInterceptor
	public ResponseVO getUserSpace(HttpSession session){
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		UserSpaceDto spaceDto = redisComponent.getUserSpaceUse(sessionWebUserDto.getUserId());
		return getSuccessResponseVO(spaceDto);
	}

	//退出登录
	@RequestMapping("/logout")
	public ResponseVO logout(HttpSession session){
		session.invalidate();
		return getSuccessResponseVO(null);
	}

	//更新头像
	@RequestMapping("/updateUserAvatar")
	@GlobalInterceptor
	public ResponseVO updateUserAvatar(HttpSession session, MultipartFile avatar) {
		SessionWebUserDto webUserDto = getUserInfoFromSession(session);
		String baseFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
		//动漫头像的文件夹路径
		File targetFileFolder = new File(baseFolder + Constants.FILE_FOLDER_AVATAR_NAME);
		if (!targetFileFolder.exists()) {
			targetFileFolder.mkdirs();
		}
		//头像要存储的路径
		File targetFile = new File(targetFileFolder.getPath() + "/" + webUserDto.getUserId() + Constants.AVATAR_SUFFIX);
		try {
			//文件保存到目标文件 targetFile 中
			avatar.transferTo(targetFile);
		} catch (Exception e) {
			logger.error("上传头像失败", e);
		}
		//用户上传头像后，要将原先的qq头像置为null
		UserInfo userInfo = new UserInfo();
		userInfo.setQqAvator("");
		userInfoService.updateUserInfoByUserId(userInfo, webUserDto.getUserId());
		webUserDto.setAvatar(null);
		session.setAttribute(Constants.SESSION_KEY, webUserDto);
		return getSuccessResponseVO(null);
	}

	//更新密码
	@RequestMapping("/updatePassword")
	@GlobalInterceptor(checkParams = true)
	public ResponseVO updatePassword(HttpSession session,
									 @VerifyParam(required = true, regex = VerifyRegexEnum.PASSWORD, min = 8, max = 18) String password) {
		SessionWebUserDto sessionWebUserDto = getUserInfoFromSession(session);
		UserInfo userInfo = new UserInfo();
		userInfo.setPassword(StringTools.encodeByMd5(password));
		userInfoService.updateUserInfoByUserId(userInfo, sessionWebUserDto.getUserId());
		return getSuccessResponseVO(null);
	}
}
