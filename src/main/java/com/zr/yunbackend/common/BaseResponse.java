package com.zr.yunbackend.common;
import com.zr.yunbackend.exception.ErrorCode;
import lombok.Data;
import java.io.Serializable;

@Data
public class BaseResponse<T> implements Serializable {

    private int code;  //状态码
    private T data;    //数据
    private String message;  //信息

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }
    public BaseResponse(int code, T data) {
        this(code, data, "");
    }
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}