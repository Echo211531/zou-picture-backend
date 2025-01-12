package com.zr.yunbackend.model.dto.message;

import com.zr.yunbackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class MessageRequest extends PageRequest implements Serializable {
    //id
    private Long id;
    //任务id
    private String taskId;
    //用户id
    private Long userId;
    private static final long serialVersionUID = 1L;  
}