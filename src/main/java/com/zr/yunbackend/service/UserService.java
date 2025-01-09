package com.zr.yunbackend.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zr.yunbackend.common.DeleteRequest;
import com.zr.yunbackend.model.dto.user.UserQueryRequest;
import com.zr.yunbackend.model.entity.Picture;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.model.vo.LoginUserVO;
import com.zr.yunbackend.model.vo.UserVO;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
*/
public interface UserService extends IService<User> {
    long userRegister(String userAccount, String userPassword, String checkPassword);
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);
    User getLoginUser(HttpServletRequest request);
    String getEncryptPassword(String userPassword);
    LoginUserVO getLoginUserVO(User user);
    boolean userLogout(HttpServletRequest request);
    UserVO getUserVO(User user);
    List<UserVO> getUserVOList(List<User> userList);
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);
    boolean isAdmin(User user);

}
