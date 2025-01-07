package com.zr.yunbackend.controller;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zr.yunbackend.annotation.AuthCheck;
import com.zr.yunbackend.common.BaseResponse;
import com.zr.yunbackend.common.DeleteRequest;
import com.zr.yunbackend.common.ResultUtils;
import com.zr.yunbackend.constant.UserConstant;
import com.zr.yunbackend.exception.BusinessException;
import com.zr.yunbackend.exception.ErrorCode;
import com.zr.yunbackend.exception.ThrowUtils;
import com.zr.yunbackend.manage.FileManager;
import com.zr.yunbackend.manage.upload.FilePictureUpload;
import com.zr.yunbackend.manage.upload.PictureUploadTemplate;
import com.zr.yunbackend.model.dto.file.UploadUserPictureResult;
import com.zr.yunbackend.model.dto.picture.*;
import com.zr.yunbackend.model.entity.Picture;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.model.enums.PictureReviewStatusEnum;
import com.zr.yunbackend.model.vo.PictureTagCategory;
import com.zr.yunbackend.model.vo.PictureVO;
import com.zr.yunbackend.service.PictureService;
import com.zr.yunbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {
    @Resource
    private UserService userService;
   @Resource
   private PictureService pictureService;
    @Resource
    private FileManager fileManager;
   @Resource
   private StringRedisTemplate stringRedisTemplate;
    //构造本地缓存
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)  //初始化本地缓存，并设置初始容量和数据
                    .maximumSize(10000L)
                    // 缓存 2 分钟移除
                    .expireAfterWrite(2L, TimeUnit.MINUTES)
                    .build();

    //上传图片（可重新上传）
    @PostMapping("/upload")
    //@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }
    //通过 URL 上传图片（可重新上传）
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }
    //批量上传图片
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }


    //上传用户头像
    @PostMapping("/uploadUserPicture")
    public BaseResponse<UploadUserPictureResult> uploadUserPicture(@RequestParam("file") MultipartFile multipartFile) {
        UploadUserPictureResult result = fileManager.uploadUserPicture(multipartFile);
        return ResultUtils.success(result);
    }

    //删除图片
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = pictureService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    //更新图片（仅管理员可用）
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将DTO转换成实体类picture
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 补充审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    //根据 id 获取图片（仅管理员可用）
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    //根据 id 获取图片（封装类）
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    //分页获取图片列表（仅管理员可用）
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    //分页获取图片列表（封装类）
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能查看已过审的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest)); //根据请求条件查的
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }
    @Resource
    private RedissonClient redissonClient;
    //分页获取图片(查缓存)
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能查看已过审的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 1. 构建缓存 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest); //查询请求对象转成json
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes()); //压缩成字符串
        String cacheKey = "zoupicture:listPictureVOByPage:" + hashKey;
        String lockKey = "zoupicture:listPictureVOByPage:lock:" + hashKey; //分布式锁

        //----本地缓存----
        // 2. 查询本地缓存
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {  // 如果本地缓存命中
            //反序列化 图片数据
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage); //返回结果
        }

        //----redis缓存----
        //3. 本地缓存未命中，则查询redis
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue(); //拿到操作对象
        cachedValue = valueOps.get(cacheKey);  //拿到key对应的数据
        if (cachedValue != null) { // 如果redis缓存命中
            // 序列化更新本地缓存
            LOCAL_CACHE.put(cacheKey, cachedValue);
            //反序列化 图片数据
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);   //返回结果
        }

        //----redisson分布式锁----
        RLock lock = redissonClient.getLock(lockKey); //获取一个分布式锁
        try {
            // 尝试获取该锁，最多等待10秒，上锁以后10秒自动解锁
            //如果某个线程成功获取该锁，就执行if内部逻辑，再次期间其他线程访问时被阻塞
            if (lock.tryLock(10, 10, TimeUnit.SECONDS)) {
                try {
                    //---数据库----
                    // 4. redis缓存为空，则查询数据库
                    Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                            pictureService.getQueryWrapper(pictureQueryRequest));
                    // 获取封装类
                    Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

                    // 5. 序列化更新Redis缓存
                    String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
                    // 6. 序列化更新本地缓存
                    LOCAL_CACHE.put(cacheKey, cacheValue);
                    // 2-4分钟随机过期，防止雪崩
                    int cacheExpireTime = 120 +  RandomUtil.randomInt(0, 120); //秒为单位
                    valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS); //哪怕查出来的是null，也设置到缓存，防止穿透
                    // 返回结果
                    return ResultUtils.success(pictureVOPage);
                } finally {
                    // 确保在任何情况下都会尝试释放锁
                    lock.unlock();
                }
            } else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取锁失败");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    //编辑图片（给用户使用）
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        pictureService.validPicture(picture);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 补充审核参数
        pictureService.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        //固定要在前端显示的标签和分类
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意", "动漫", "原创");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报", "头像", "电影");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    //审核图片(管理员)
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

}

