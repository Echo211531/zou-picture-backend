package com.zr.yunbackend.manager.websocket.disruptor;

import cn.hutool.json.JSONUtil;
import com.lmax.disruptor.WorkHandler;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.service.UserService;
import com.zr.yunbackend.manager.websocket.PictureEditHandler;
import com.zr.yunbackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.zr.yunbackend.manager.websocket.model.PictureEditRequestMessage;
import com.zr.yunbackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

//事件处理，消费者
@Slf4j
@Component                                       //实现消费者接口，这里传入前面定义的事件类型
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    @Lazy  //防止循环依赖
    private PictureEditHandler pictureEditHandler;
    @Resource
    private UserService userService;
	//和消息队列差不多，定义消息传递过来的具体处理		
    @Override
    public void onEvent(PictureEditEvent event) throws Exception {
        //从事件中获取消息
        PictureEditRequestMessage pictureEditRequestMessage = event.getPictureEditRequestMessage();
        //获取当前客户端session
        WebSocketSession session = event.getSession();
        User user = event.getUser();
        Long pictureId = event.getPictureId();
        // 获取到消息类别
        String type = pictureEditRequestMessage.getType();
        //根据消息执行的操作，转成枚举类
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.valueOf(type);
        // 调用对应的消息处理方法
        switch (pictureEditMessageTypeEnum) {
            case ENTER_EDIT:  //收到进入编辑消息
                pictureEditHandler.handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EDIT_ACTION:    //收到执行编辑消息
                pictureEditHandler.handleEditActionMessage(pictureEditRequestMessage, session, user, pictureId);
                break; 
            case EXIT_EDIT:   //收到退出编辑消息
                pictureEditHandler.handleExitEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            default:
                //如果发来的消息操作不在枚举类中，则发送对应的错误信息给当前用户session
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                pictureEditResponseMessage.setMessage("消息类型错误");
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
        }
    }
}