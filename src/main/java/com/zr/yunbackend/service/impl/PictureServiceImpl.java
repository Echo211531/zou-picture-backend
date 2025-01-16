package com.zr.yunbackend.service.impl;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zr.yunbackend.api.aliyunai.AliYunApi;
import com.zr.yunbackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.zr.yunbackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.zr.yunbackend.exception.BusinessException;
import com.zr.yunbackend.exception.ErrorCode;
import com.zr.yunbackend.exception.ThrowUtils;
import com.zr.yunbackend.manager.upload.FilePictureUpload;
import com.zr.yunbackend.manager.upload.PictureUploadTemplate;
import com.zr.yunbackend.manager.upload.UrlPictureUpload;
import com.zr.yunbackend.model.dto.file.UploadPictureResult;
import com.zr.yunbackend.model.dto.picture.*;
import com.zr.yunbackend.model.entity.Message;
import com.zr.yunbackend.model.entity.Picture;
import com.zr.yunbackend.model.entity.Space;
import com.zr.yunbackend.model.entity.User;
import com.zr.yunbackend.model.enums.PictureReviewStatusEnum;
import com.zr.yunbackend.model.vo.PictureVO;
import com.zr.yunbackend.model.vo.UserVO;
import com.zr.yunbackend.mq.AiMessageProducer;
import com.zr.yunbackend.service.MessageService;
import com.zr.yunbackend.service.PictureService;
import com.zr.yunbackend.mapper.PictureMapper;
import com.zr.yunbackend.service.SpaceService;
import com.zr.yunbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
*/
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{
    @Resource
    private FilePictureUpload filePictureUpload;
    @Resource
    private UrlPictureUpload urlPictureUpload;
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private TransactionTemplate transactionTemplate;

    //上传图片或url
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 追加空间权限校验
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {  //不为空，则校验校验权限
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 必须空间所属人才能上传
            //已经改为注解鉴权
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        // 用于判断是上传图片还是更新图片
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑
            //已经改为注解鉴权
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 没传 spaceId，说明更新公共空间，直接复用原来的
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原有图片一致，即用户不能编辑其他空间的图片
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }

        // 上传图片，得到信息
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {  //公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {    //私有空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        //根据input类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(inputSource instanceof String){
            pictureUploadTemplate=urlPictureUpload;
        }

        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 构造要入库的图片信息
        Picture picture = new Picture();
        // 补充设置 spaceId
        picture.setSpaceId(spaceId);
        picture.setUrl(uploadPictureResult.getUrl());
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());

        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);

        // 开启事务
        Long finalSpaceId = spaceId;
        //更新空间使用额度
        transactionTemplate.execute(status -> {
            boolean result = this.saveOrUpdate(picture);  //插入数据
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
            if (finalSpaceId != null) {
                //更新空间表
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)  //更新指定id的空间
                        //当前空间大小=原本大小+现在图片占用的大小
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
        return PictureVO.objToVo(picture);  //返回picture脱敏后的vo给前端
    }
    //校验空间权限，判断当前用户是否能看到这张图片
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }
    //补充审核参数
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        boolean isPrivateAndOwned = picture.getSpaceId() != null &&
                picture.getUserId().equals(loginUser.getId());
        // 检查是否为私有空间且属于当前用户，或者用户是管理员
        if (userService.isAdmin(loginUser) || isPrivateAndOwned) {
            // 管理员或私有空间所有者自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage(isPrivateAndOwned ? "私有空间所有者自动过审" : "管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员,或非私有空间所有者的图片需要审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }
    //查询包装器
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();

        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        //时间>=开始时间
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        //时间<结束时间
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }
    //PictureService
    //获取单个图片封装类
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }
    //分页获取图片封装
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    //图片校验
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    //根据逻辑字段查询
    @Override
    public List<Picture> selectByIsDelete(Integer isDelete) {
        return baseMapper.selectByIsDelete(isDelete);
    }

    //图片审核
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 已是该状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 更新审核状态
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    //批量抓取和创建图片
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText(); //提取搜索词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix(); //提取名字前缀
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        final String prefix = namePrefix;  //更新名字前缀，因为后面的lambda表达式要求final
        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");

        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get(); //爬取该网站内容
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        //根据该html页面解析内容
        //1.根据类名dgControl获取最外层元素
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {  //最外层是空
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        //2.提取该元素中的图片标签 数组
        //Elements imgElementList = div.select("img.mimg");
        //修改选择器，获取包含完整数据的元素
        Elements imgElementList = div.select(".iusc");
        // 创建一个固定大小的线程池，考虑使用更合适的线程池配置
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        try {
            // 创建一个 AtomicInteger 用于生成图片名称的序号
            AtomicInteger picNameCounter = new AtomicInteger(1);

            // 存储所有异步任务的列表
            List<CompletableFuture<Void>> futures = imgElementList.stream()
                    .limit(count)  // 限制图片数量不超过count
                    //对流的每个图片创建一个异步任务
                    .map(imgElement -> CompletableFuture.runAsync(() -> {
                        try {
                            //执行上传图片任务
                            handleImageUpload(imgElement, prefix, picNameCounter, loginUser);
                        } catch (Exception e) {
                            log.error("图片上传失败", e);
                        }
                    }, executorService))  //在该线程池管理下
                    .collect(Collectors.toList()); //收集结果到futures

            // 等待所有任务完成，并处理可能的异常
            //futures列表==>数组传递给allOf==>join等待所有的图片上传任务都完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 计算成功上传的图片数
            long uploadCount = futures.stream()
                    //过滤掉异常的任务
                    .filter(future -> future.isDone() && !future.isCompletedExceptionally())
                    .count();  //收集成功后的图片数
            return (int) uploadCount;  // 最后返回上传成功的图片数
        } catch (Exception e) {
            Thread.currentThread().interrupt(); // 恢复中断状态
            log.error("线程被中断", e);
            throw new RuntimeException("线程被中断", e);
        } finally {
            // 关闭线程池
            executorService.shutdown();
        }
    }
    //处理上传图片
    private void handleImageUpload(Element imgElement, String namePrefix,
                                   AtomicInteger picNameCounter, User loginUser) {
        String fileUrl;
        // 获取data-m属性中的JSON字符串
        String dataM = imgElement.attr("m");
        // 解析JSON字符串
        JSONObject jsonObject = JSONUtil.parseObj(dataM);
        fileUrl = jsonObject.getStr("murl"); //获取图片高清地址

        if (StrUtil.isBlank(fileUrl)) {
            log.info("当前链接为空，已跳过: {}", fileUrl);
            return;
        }
        // 处理图片上传地址，防止出现对象存储转义问题
        int questionMarkIndex = fileUrl.indexOf("?"); //把查询条件干掉，比如aa.com?...
        if (questionMarkIndex > -1) {
            fileUrl = fileUrl.substring(0, questionMarkIndex);
        }
        // 准备上传请求
        PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
        if (StrUtil.isNotBlank(namePrefix)) {
            // 设置图片名称，通过 AtomicInteger 获取递增序号
            pictureUploadRequest.setPicName(namePrefix + picNameCounter.getAndIncrement());
        }
        PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        log.info("图片上传成功, id = {}", pictureVO.getId());
    }

    //删除图片
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        //权限校验
        //已经改为注解鉴权
        //checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 更新空间的使用额度，释放额度
            boolean update = spaceService.lambdaUpdate()
                    .eq(Space::getId, oldPicture.getSpaceId())
                    .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                    .setSql("totalCount = totalCount - 1")
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            return true;
        });
    }
    //查询指定空间的所有图片
    @Override
    public List<Picture> getPicturesBySpaceId(Long spaceId) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("space_id", spaceId);
        return this.list(queryWrapper);  // 使用 MyBatis-Plus 提供的 list 方法进行查询
    }

    @Resource
    AliYunApi aliYunApi;
    @Resource
    MessageService messageService;
   @Resource
    AiMessageProducer aiMessageProducer;
    //创建扩图任务
    @Override
    public void createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
                                                                      User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        //把有可能为空的对象封装到Optional中
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                //如果为空，就执行这个方法抛异常
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 权限图片空间校验
        //已经改为注解鉴权
        //checkPictureAuth(loginUser, picture);

        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl()); //传入图片url
        taskRequest.setInput(input);    //传入input到请求体中
        //把前端传来剩余的扩图属性 赋值到请求体中
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        //AI扩图结果
        CreateOutPaintingTaskResponse result;
        result= transactionTemplate.execute(status -> {
            // 检查是否有足够的扩图额度
            if (loginUser.getOutPaintingQuota() <= 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "额度不足");
            }
            // 预扣费
            boolean update = userService.lambdaUpdate()
                    .eq(User::getId, loginUser.getId())  //更新指定id的空间
                    //当前额度=原本额度-1
                    .setSql("outPaintingQuota = outPaintingQuota - " + 1)
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            try{
                //执行扩图
                return aliYunApi.createOutPaintingTask(taskRequest);

            }catch (Exception e){
              userService.lambdaUpdate()
                        .eq(User::getId, loginUser.getId())  //更新指定id的空间
                        //当前额度=原本额度+1
                        .setSql("outPaintingQuota = outPaintingQuota + " + 1)
                        .update();
                status.setRollbackOnly(); // 确保事务回滚
                throw e; // 重新抛出异常以确保调用方知道任务失败
            }
        });
        //执行完扩图后，创建一个Message实体并保存到数据库
        Message message = new Message();
        message.setTaskId(result.getOutput().getTaskId());
        message.setUserId(loginUser.getId());
        message.setEndTime(new Date());
        message.setTaskStatus(result.getOutput().getTaskStatus());
        message.setRequestId(result.getOutput().getTaskId()); //设置请求唯一标识
        messageService.save(message);

        // 发布消息到RabbitMQ，仅包含必要的信息
        aiMessageProducer.sendMessage(result.getOutput().getTaskId());

        // 返回操作成功信息
    }
}




