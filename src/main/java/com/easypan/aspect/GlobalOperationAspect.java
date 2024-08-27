package com.easypan.aspect;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.enums.ResponseCodeEnum;
import com.easypan.exception.BusinessException;
import com.easypan.utils.StringTools;
import com.easypan.utils.VerifyUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.reflection.ArrayUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.mail.Session;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.logging.Logger;

//实现一个全局拦截器，主要功能包括登录校验和参数校验。通过AOP的切面编程，将这些通用逻辑从业务代码中分离出来，增强了代码的可维护性和复用性。
@Component
@Aspect //表明这个类是一个切面类
public class GlobalOperationAspect {
    //基本参数类型
    private static final String[] TYPE_BASE = {"java.lang.String","java.lang.Integer","java.lang.Long"};

    //切点：指定拦截哪些方法,这里指定了所有带有@GlobalInterceptor注解的方法
    @Pointcut("@annotation(com.easypan.annotation.GlobalInterceptor)")
    public void requestInterceptor(){

    }
    //Around：在目标方法（被拦截的方法）执行的前后都执行一些代码逻辑
    @Around("requestInterceptor()")
    public Object interceptorDo(ProceedingJoinPoint point){//ProceedingJoinPoint提供了对被 @Globallnterceptor 注解修饰的方法的访问。
        try {
            Object target = point.getTarget();//获取当前执行方法的实例对象
            Object[] arguments = point.getArgs();//获取当前执行方法的参数
            String methodName = point.getSignature().getName();//获取当前执行方法的名称。
            Class<?>[] parameterTypes = ((MethodSignature) point.getSignature()).getMethod().getParameterTypes(); //获取方法参数的类型。
            Method method = target.getClass().getMethod(methodName,parameterTypes);//获取方法对象
            //获取注解(此注解是定义在方法上的)
            GlobalInterceptor interceptor = method.getAnnotation(GlobalInterceptor.class);
            if(interceptor==null){
                return null;
            }
            //检验登录
            if(interceptor.checkLogin() || interceptor.checkAdmin()){
                checkLogin(interceptor.checkAdmin());
            }
            //检验参数
            if(interceptor.checkParams()){
                validateParams(method,arguments);
            }
            //校验全部通过，放行
            Object pointResult = point.proceed();
            return pointResult;
        }
        catch (BusinessException e){
            throw e;
        }
        catch (Exception e){
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
        catch (Throwable e){
            throw new BusinessException(ResponseCodeEnum.CODE_500);
        }
    }

    //检验登录
    private void checkLogin(Boolean checkAdmin){
        //RequestContextHolder表示在任意位置获取与当前请求相关的上下文信息；getRequestAttributes表示返回当前请求的属性对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();  //获取到请求
        HttpSession session = request.getSession();
        SessionWebUserDto sessionWebUserDto = (SessionWebUserDto) session.getAttribute(Constants.SESSION_KEY);
        //未登录的情况
        if(sessionWebUserDto==null){
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }

        //需要是管理员但是用户不是管理员
        if(checkAdmin && !sessionWebUserDto.getAdmin()){
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
    }

    //检验参数
    public void validateParams(Method m,Object[] Params){
        //Parameter包含了参数的name，type和value属性
        Parameter[] parameters=m.getParameters();
        for(int i=0;i<parameters.length;i++){
            //参数属性
            Parameter parameter=parameters[i];
            //参数的具体值
            Object value=Params[i];
            //获取参数校验注解；VerifyParam.class 是 VerifyParam 注解的类对象
            VerifyParam verifyParam = parameter.getAnnotation(VerifyParam.class);
            //判断是否引用了参数注解
            if(verifyParam==null){
                continue;
            }
            if(ArrayUtils.contains(TYPE_BASE,parameter.getParameterizedType().getTypeName())){
                //具体的校验逻辑放在checkValue方法中
                checkValue(value,verifyParam);
            }
        }
    }

    private void checkValue(Object value,VerifyParam verifyParam){
        //判断数值型或整数型对象是否为空
        Boolean isEmpty = value==null || StringTools.isEmpty(value.toString());
        int length = value==null?0:value.toString().length();
        //1)判断参数是否不能为空
        if(isEmpty && verifyParam.required()){
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //2)检验长度
        if(!isEmpty && (verifyParam.max()!=-1 && length>verifyParam.max() || verifyParam.min()!=-1 && length< verifyParam.min())){
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        //3)检验正则:参数不为空，正则不为空，正则和参数值匹配
        if(!isEmpty && !StringTools.isEmpty(verifyParam.regex().getRegex()) && !VerifyUtils.verify(verifyParam.regex(),String.valueOf(value))){
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

}
