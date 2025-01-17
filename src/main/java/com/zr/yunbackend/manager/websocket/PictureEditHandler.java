package com.zr.yunbackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.service.UserService;
import com.zr.yunbackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.zr.yunbackend.manager.websocket.model.PictureEditActionEnum;
import com.zr.yunbackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.zr.yunbackend.manager.websocket.model.PictureEditRequestMessage;
import com.zr.yunbackend.manager.websocket.model.PictureEditResponseMessage;
import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//图片编辑处理器
@Component
public class PictureEditHandler extends TextWebSocketHandler {
    @Resource
    private UserService userService;
    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    //建立连接后执行，发送广播消息
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 保存用户会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        //如果是是初始加入该集合，则先初始化空的会话集合
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);  //往会话集合中加入当前连接上用户

        // 构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue()); //发送通知
        String message = String.format("%s加入编辑", user.getUserName());   //构造消息
        pictureEditResponseMessage.setMessage(message);   //传入消息
        pictureEditResponseMessage.setUser(userService.getUserVO(user));  //传入当前用户
        // 广播给同一张图片的所有用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);  //发送通知的消息，包括自己也可以发

        // 从redis加载当前图片的编辑状态
        PictureEditResponseMessage currentState = loadPictureEditState(pictureId);
        //如果有
        if (currentState != null) {
            // 向新加入的用户单独发送当前的编辑状态
            ObjectMapper objectMapper = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            //自定义序列化器避免精度丢失
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(module);
            //将图片编辑状态转成json格式并保存到消息体中
            String stateMessageJson = objectMapper.writeValueAsString(currentState);
            TextMessage textMessage = new TextMessage(stateMessageJson);
            if (session.isOpen()) {
                session.sendMessage(textMessage);
            }
        }

    }

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;
    //接收客户端消息并处理
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 将前端消息解析为 PictureEditMessage，这里不需要用响应序列化，是因为前端发来的本来就是字符串
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.
                toBean(message.getPayload(), PictureEditRequestMessage.class);
        String type = pictureEditRequestMessage.getType();

        // 从 Session 属性中获取公共参数，如当前用户id，要编辑的图片id
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        // 生产消息
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);

    }

    //用户关闭连接后的操作
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();  //获取当前用户的session属性
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);

        // 删除会话集合中的该用户信息
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        // 构造响应消息
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        //广播给所有人
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }


    //用户进入编辑
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置当前用户为编辑用户
            pictureEditingUsers.put(pictureId, user.getId());
            //构造响应消息
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            //广播给所有人
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    //用户执行编辑操作
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage,
                                        WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);  //获取正在编辑用户的id
        String editAction = pictureEditRequestMessage.getEditAction(); //获取要执行的动作
        //根据值获取枚举
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            //构造响应消息
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);

            // 保存退出编辑的状态到 Redis
            savePictureEditState(pictureId, pictureEditResponseMessage);
        }
    }

    //用户退出编辑
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage,
                                      WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            //广播给所有人
            broadcastToPicture(pictureId, pictureEditResponseMessage);

            // 保存退出编辑的状态到 Redis
            savePictureEditState(pictureId, pictureEditResponseMessage);
        }
    }

    //广播响应消息给其他会话
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage,
                                    WebSocketSession excludeSession) throws Exception {
        //拿到当前图片的所有会话集合
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 创建 ObjectMapper，这里是jackson库，而不是hutool
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化：将响应中的Long类型转为 String，解决丢失精度问题
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
            objectMapper.registerModule(module);

            // 利用jackson 序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除掉的 session 不发送，比如自己编辑，消息不能广播给自己
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {   //广播发送给每个会话
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    // 全部广播
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    //------redis操作-------
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // Redis key 的前缀，用于区分不同的图片编辑记录
    private static final String REDIS_KEY_PREFIX = "picture:edit:state:";

    // 加载图片的编辑状态
    private PictureEditResponseMessage loadPictureEditState(Long pictureId) {
        // 构造 Redis key
        String redisKey = REDIS_KEY_PREFIX + pictureId;
        // 从 Redis 中获取编辑状态
        String editStateJson = stringRedisTemplate.opsForValue().get(redisKey);
        // 如果有编辑状态，则反序列化为对象
        if (editStateJson != null) {
            return JSONUtil.toBean(editStateJson, PictureEditResponseMessage.class);
        }
        // 如果没有编辑状态，则返回null或默认值
        return null;
    }

    // 保存图片的编辑状态到Redis
    private void savePictureEditState(Long pictureId, PictureEditResponseMessage message) {
        // 构造 Redis key
        String redisKey = REDIS_KEY_PREFIX + pictureId;
        // 将消息序列化为 JSON 字符串
        String messageJson = JSONUtil.toJsonStr(message);
        // 存储到 Redis 中
        stringRedisTemplate.opsForValue().set(redisKey, messageJson);
    }

}