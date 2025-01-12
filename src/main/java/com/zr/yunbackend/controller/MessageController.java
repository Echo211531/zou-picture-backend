package com.zr.yunbackend.controller;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zr.yunbackend.common.BaseResponse;
import com.zr.yunbackend.common.ResultUtils;
import com.zr.yunbackend.exception.BusinessException;
import com.zr.yunbackend.exception.ErrorCode;
import com.zr.yunbackend.model.dto.message.MessageRequest;
import com.zr.yunbackend.model.entity.Message;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.service.MessageService;
import com.zr.yunbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;



@RestController
@RequestMapping("/ai")
@Slf4j
public class MessageController {
    @Resource
    private UserService userService;
   @Resource
   private MessageService messageService;


    //删除图片
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteMessage(@RequestBody MessageRequest messageRequest, HttpServletRequest request) {
        if (messageRequest == null ) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        //如果当前用户不等于请求的用户，则删除失败
        if (!loginUser.getId().equals(messageRequest.getUserId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean success = messageService.removeById(messageRequest.getId()); //删除指定id的AI扩图记录
        return ResultUtils.success(success);
    }


    //根据 id 获取指定的扩图信息
    @GetMapping("/get")
        public BaseResponse<Message> getAiMessage(long id) {
        return ResultUtils.success(messageService.getById(id));
    }

    //分页获取图片列表
    @PostMapping("/list")
    public BaseResponse<Page<Message>> listMessage(@RequestBody MessageRequest messageRequest, HttpServletRequest request) {
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        // 分页参数
        Page<Message> page = new Page<>(messageRequest.getCurrent(), messageRequest.getPageSize());
        // 构建查询条件，仅查询当前用户的消息，并且是未删除的消息
        LambdaQueryWrapper<Message> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Message::getUserId, loginUser.getId());
        // 执行分页查询
        Page<Message> messagePage = messageService.page(page, queryWrapper);
        return ResultUtils.success(messagePage);
    }
}

