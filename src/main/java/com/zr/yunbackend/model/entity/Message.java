package com.zr.yunbackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * AI扩图
 */
@TableName(value ="message")
@Data
public class Message implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    //任务id
    private String taskId;

    //任务状态
    private String taskStatus;
    //请求唯一标识
    private String requestId;

    //最终扩图url
    private String outputImageUrl;
    //生成时间
    private Date endTime;

    //用户id
    private Long userId;

    //是否删除
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}