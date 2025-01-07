package com.zr.yunbackend.model.dto.user;

import com.zr.yunbackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)  //lombok提供自动生成equals和hashcode
@Data
public class UserQueryRequest extends PageRequest implements Serializable {
    //id
    private Long id;
    //用户昵称
    private String userName;
    //账号
    private String userAccount;
    //简介
    private String userProfile;
    //用户角色：user/admin/ban
    private String userRole;
    private static final long serialVersionUID = 1L;
}