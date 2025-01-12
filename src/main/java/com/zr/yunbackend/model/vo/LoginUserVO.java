package com.zr.yunbackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
@Data
public class LoginUserVO implements Serializable {
    //id
    private Long id;
    //账号
    private String userAccount;
    //用户昵称
    private String userName;
    //用户头像
    private String userAvatar;
    //用户简介
    private String userProfile;
    //用户角色：user/admin
    private String userRole;
    // 扩图额度
    private Integer outPaintingQuota;
    //编辑时间
    private Date editTime;
    //创建时间
    private Date createTime;
    //更新时间
    private Date updateTime;
    private static final long serialVersionUID = 1L;

}
