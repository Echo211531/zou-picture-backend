package com.zr.yunbackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.zr.yunbackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.zr.yunbackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.zr.yunbackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.zr.yunbackend.exception.BusinessException;
import com.zr.yunbackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliYunApi {
    // 读取配置文件
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // 创建任务地址，可以从官方文档中查看
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";
    // 查询任务状态
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    /**
     * 创建任务
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        if (createOutPaintingTaskRequest == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "扩图参数为空");
        }
        //curl --location --request POST 'https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting' \
        //--header "Authorization: Bearer $DASHSCOPE_API_KEY" \
        //--header 'X-DashScope-Async: enable' \
        //--header 'Content-Type: application/json' \
        //--data '{
        //    "model": "image-out-painting",
        //    "input": {
        //        "image_url": "http://xxx/image.jpg"
        //    },
        //    "parameters":{
        //        "angle": 45,
        //        "x_scale":1.5,
        //        "y_scale":1.5
        //    }
        //}'

        // 发送请求，构造官网http那种格式的代码，ai直接生成
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)  //传密钥
                // 必须开启异步处理，设置为enable
                .header("X-DashScope-Async", "enable")
                .header(Header.CONTENT_TYPE, ContentType.JSON.getValue())
                //把请求对象转成json字符串去传递
                .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));
        //处理响应，放的try里面会自动释放资源
        try (HttpResponse httpResponse = httpRequest.execute()) {
            if (!httpResponse.isOk()) { //如果有任何错误
                log.error("请求异常：{}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }
            //若任务响应成功，则将json数据转化为 响应类对象
            CreateOutPaintingTaskResponse response = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            String errorCode = response.getCode();
            if (StrUtil.isNotBlank(errorCode)) {  //如果错误码存在，则说明有错误
                String errorMessage = response.getMessage();
                log.error("AI 扩图失败，errorCode:{}, errorMessage:{}", errorCode, errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图接口响应异常");
            }
            return response;  //反之，返回成功后的任务数据
        }
    }

    //查询创建的任务结果                                   //传入 指定任务id
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "任务 id 不能为空");
        }
        //查询结果，把前面定义的查询任务状态 地址的%s 替换为指定的taskId
        try (HttpResponse httpResponse = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header(Header.AUTHORIZATION, "Bearer " + apiKey)  //传密钥
                .execute()) {
            if (!httpResponse.isOk()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务失败，图片分辨率或格式不符");
            }
            //请求成功，则把响应的字符串转为 对于响应类
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}
