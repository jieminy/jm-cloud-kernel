package com.jm.cloud.core.feign;

import cn.hutool.core.io.IoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String s, Response response) {
        /*
          解析response body内容，如果body解析异常则直接抛出ServiceException异常
         */
        String responseBody;
            try {
                if(response == null || response.body() == null){
                    if(response != null && response.status() == 404){
                        throw new ServiceException(CoreExceptionEnum.REMOTE_SERVICE_NULL);
                    }else{
                        throw new ServiceException(CoreExceptionEnum.SERVICE_ERROR);
                    }
                }else{
                    responseBody = IoUtil.read(response.body().asInputStream(), "UTF-8");
                }
            } catch (IOException e) {
                log.error(CoreExceptionEnum.IO_ERROR.getMessage(), e);
                return new ServiceException(CoreExceptionEnum.IO_ERROR);
            }

        /*
          解析response body，用json反序列化
         */
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> responseMap = objectMapper.convertValue(responseBody, Map.class);

        log.debug("FeignErrorDecoder收到错误信息：{}", responseBody);
        /*
          获取有效信息
         */
        String exceptionClazz = responseMap.get("exceptionClzz") == null ? null : responseMap.get("exceptionClzz").toString();
        Integer code = responseMap.get("code") == null ? null : Integer.valueOf(responseMap.get("code").toString());
        String message = responseMap.get("message") == null ? null : responseMap.get("message").toString();

        /*
          判断是否有ExceptionClazz字段，如果有，代表抛出的时服务异常子类，那么需要返回这个异常
         */
        if(ToolUtil.isNotEmpty(exceptionClazz)){
            ApiServiceException apiServiceException = getExceptionByReflection(exceptionClazz, code, message);
            if(apiServiceException != null){
                return apiServiceException;
            }
        }

        /*
          如果不是apiServiceException子类，则抛出ServiceException
         */
        if(message == null){
            message = CoreExceptionEnum.SERVICE_ERROR.getMessage();
        }

        if(code == null){
            Integer status = responseMap.get("status") == null ? null : Integer.valueOf(responseMap.get("status").toString());

            if(status == null){
                return new ServiceException(CoreExceptionEnum.SERVICE_ERROR.getCode(), message);
            }else{
                return new ServiceException(status, message);
            }

        }else{
            return new ServiceException(code, message);
        }
    }

    /**
     * 通过类名称获取具体异常
     */
    private ApiServiceException getExceptionByReflection(String className, Integer code, String message){
        try {
            Class<?> clazz = Class.forName(className);
            Constructor constructor = clazz.getConstructor(AbstractBaseExceptionEnum.class);
            return (ApiServiceException) constructor.newInstance(new AbstractBaseExceptionEnum() {
                @Override
                public Integer getCode() {
                    return code;
                }

                @Override
                public String getMessage() {
                    return message;
                }
            });
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            log.error("实例化异常", e);
            return null;
        }
    }
}
