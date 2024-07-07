package com.easypan.service.impl;

import java.util.Date;
import java.util.List;

import javax.annotation.Resource;
import javax.mail.Session;

import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.SysSettingsDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.UserStatusEnum;
import com.easypan.exception.BusinessException;
import com.easypan.service.EmailCodeService;
import org.apache.catalina.User;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.reflection.ArrayUtil;
import org.springframework.stereotype.Service;

import com.easypan.entity.enums.PageSize;
import com.easypan.entity.query.UserInfoQuery;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.entity.query.SimplePage;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.service.UserInfoService;
import com.easypan.utils.StringTools;
import org.springframework.transaction.annotation.Transactional;


/**
 *  业务接口实现
 */
@Service("userInfoService")
public class UserInfoServiceImpl implements UserInfoService {

	@Resource
	private UserInfoMapper<UserInfo, UserInfoQuery> userInfoMapper;

	@Resource
	private EmailCodeService emailCodeService;

	@Resource
	private RedisComponent redisComponent;

	@Resource
	private AppConfig appConfig;

	/**
	 * 根据条件查询列表
	 */
	@Override
	public List<UserInfo> findListByParam(UserInfoQuery param) {
		return this.userInfoMapper.selectList(param);
	}

	/**
	 * 根据条件查询列表
	 */
	@Override
	public Integer findCountByParam(UserInfoQuery param) {
		return this.userInfoMapper.selectCount(param);
	}

	/**
	 * 分页查询方法
	 */
	@Override
	public PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param) {
		int count = this.findCountByParam(param);
		int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

		SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
		param.setSimplePage(page);
		List<UserInfo> list = this.findListByParam(param);
		PaginationResultVO<UserInfo> result = new PaginationResultVO(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
		return result;
	}

	/**
	 * 新增
	 */
	@Override
	public Integer add(UserInfo bean) {
		return this.userInfoMapper.insert(bean);
	}

	/**
	 * 批量新增
	 */
	@Override
	public Integer addBatch(List<UserInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userInfoMapper.insertBatch(listBean);
	}

	/**
	 * 批量新增或者修改
	 */
	@Override
	public Integer addOrUpdateBatch(List<UserInfo> listBean) {
		if (listBean == null || listBean.isEmpty()) {
			return 0;
		}
		return this.userInfoMapper.insertOrUpdateBatch(listBean);
	}

	/**
	 * 多条件更新
	 */
	@Override
	public Integer updateByParam(UserInfo bean, UserInfoQuery param) {
		StringTools.checkParam(param);
		return this.userInfoMapper.updateByParam(bean, param);
	}

	/**
	 * 多条件删除
	 */
	@Override
	public Integer deleteByParam(UserInfoQuery param) {
		StringTools.checkParam(param);
		return this.userInfoMapper.deleteByParam(param);
	}

	/**
	 * 根据UserId获取对象
	 */
	@Override
	public UserInfo getUserInfoByUserId(String userId) {
		return this.userInfoMapper.selectByUserId(userId);
	}

	/**
	 * 根据UserId修改
	 */
	@Override
	public Integer updateUserInfoByUserId(UserInfo bean, String userId) {
		return this.userInfoMapper.updateByUserId(bean, userId);
	}

	/**
	 * 根据UserId删除
	 */
	@Override
	public Integer deleteUserInfoByUserId(String userId) {
		return this.userInfoMapper.deleteByUserId(userId);
	}

	/**
	 * 用户注册
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void register(String email, String nickName, String password, String emailCode) {
		UserInfo userInfo = this.userInfoMapper.selectByEmail(email);
		if(userInfo!=null){
			throw new BusinessException("邮箱账号已经存在");
		}
		UserInfo nickNameUser = this.userInfoMapper.selectByNickName(nickName);
		if(userInfo!=null){
			throw new BusinessException("昵称已经存在");
		}

		//检验邮箱验证码
		emailCodeService.checkCode(email,emailCode);

		String userId = StringTools.getRandomString(Constants.LENGTH_15);
		userInfo = new UserInfo();
		userInfo.setUserId(userId);
		userInfo.setNickName(nickName);
		userInfo.setEmail(email);
		userInfo.setPassword(StringTools.encodeByMd5(password));
		userInfo.setJoinTime(new Date());
		userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
		userInfo.setUseSpace(0L);
		SysSettingsDto sysSettingsDto = redisComponent.getSysSettingDto();
		userInfo.setTotalSpace(sysSettingsDto.getUserInitSpace() * Constants.MB);
		this.userInfoMapper.insert(userInfo);
	}

	/**
	 * 用户登录
	 */
	@Override
	public SessionWebUserDto login(String email, String password) {
		UserInfo userInfo = this.userInfoMapper.selectByEmail(email);
		if(userInfo==null || !userInfo.getPassword().equals(password)){
			throw new BusinessException("账号密码错误");
		}
		if(UserStatusEnum.DISABLE.getStatus().equals(userInfo.getStatus())){
			throw new BusinessException("账号已经被禁用");
		}
		UserInfo updateInfo = new UserInfo();
		updateInfo.setLastLoginTime(new Date());
		this.userInfoMapper.updateByUserId(updateInfo,userInfo.getUserId());

		//用户的会话信息
		SessionWebUserDto sessionWebUserDto = new SessionWebUserDto();
		sessionWebUserDto.setUserId(userInfo.getUserId());
		sessionWebUserDto.setNickName(userInfo.getNickName());
		if(ArrayUtils.contains(appConfig.getAdminEmails().split(","),email)){
			sessionWebUserDto.setAdmin(true);
		}
		else{
			sessionWebUserDto.setAdmin(false);
		}

		//用户空间
		UserSpaceDto userSpaceDto = new UserSpaceDto();
//		userSpaceDto.setUseSpace(userInfo.getUseSpace());
		userSpaceDto.setTotalSpace(userInfo.getTotalSpace());
		//将用户内存使用情况存入redis
		redisComponent.saveUserSpaceUse(userInfo.getUserId(),userSpaceDto);
		return null;
	}

	/**
	 * 重置密码
	 */
	@Override
	@Transactional(rollbackFor = Exception.class) //保证邮箱验证码和重置密码在一个事物里
	public void resetPwd(String email, String password, String emailCode) {
		UserInfo userInfo = this.userInfoMapper.selectByEmail(email);
		if(userInfo==null){
			throw new BusinessException("邮箱账号不存在");
		}
		//验证邮箱验证码
		emailCodeService.checkCode(email,emailCode);
		UserInfo updateInfo = new UserInfo();
		//生成字符串的md5哈希值
		updateInfo.setPassword(StringTools.encodeByMd5(password));
		this.userInfoMapper.updateByEmail(updateInfo,email);
	}


}
