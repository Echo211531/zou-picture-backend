package com.zr.yunbackend.task;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zr.yunbackend.manage.CosManager;
import com.zr.yunbackend.service.PictureService;
import com.zr.yunbackend.model.entity.Picture;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CleanupTask {
    @Resource
    private CosManager cosManager;
    @Resource
    private PictureService pictureService;

    /**
     * 定时任务，每7天执行一次，在每周日凌晨0点执行
     */
    @Scheduled(cron = "0 0 0 ? * SUN")
    public void cleanupMarkedImages() {
        // 调用自定义方法查询所有标记为逻辑删除的图片
        List<Picture> picturesToDelete = pictureService.selectByIsDelete(1);

        if (picturesToDelete.isEmpty()) {
            return; // 如果没有需要删除的记录，则直接返回
        }
        try {
            // 提取并解析所有要删除的对象键
            List<String> keysToDelete = picturesToDelete.stream()
                    .filter(picture -> isUrlUniqueToThisRecord(picture.getUrl(), picture.getId())) // 检查URL唯一性
                    .map(Picture::getUrl) // 获取 url 字段
                    .filter(java.util.Objects::nonNull) // 确保 URL 不为空
                    .map(this::extractObjectKeyFromUrl)  // 从 URL 中提取对象键
                    .collect(Collectors.toList());

            // 删除COS中的对象
            if (!keysToDelete.isEmpty()) {
                cosManager.deleteObjects(keysToDelete);
                // 物理删除数据库记录或更新状态
                for (Picture picture : picturesToDelete) {
                    // 假设有一个方法可以物理删除记录，或者更新状态
                    // 这里可以选择物理删除或更新 isDelete 字段为其他值，例如 2，以表示已处理
                    pictureService.removeById(picture.getId());
                    // 或者
                    // picture.setIsDelete(2); // 更新状态
                    // pictureService.updateById(picture);
                }
            }
        } catch (Exception e) {
            log.error("清理图片发生错误.", e);
        }
    }

    /**
     * 从完整的URL中提取对象键（ObjectKey）
     *
     * @param url 完整的URL
     * @return 对象键（ObjectKey）
     */
    private String extractObjectKeyFromUrl(String url) {
        try {
            URL parsedUrl = new URL(url);
            String path = parsedUrl.getPath();
            //比如url为https://zr.cos.ap-shanghai.myqcloud.com//public/187/7q.png
            //解析出的对象键为 public/187/7q.png，双斜杠会被http自动处理不用管
            if (path.startsWith("/")) {
                path = path.substring(1); // 去除开头的斜杠
            }
            return path;
        } catch (MalformedURLException e) {
            log.warn("无效 URL: {}", url, e);
            return null;
        }
    }

    // 检查给定的URL是否只属于指定ID的记录，即确认该URL不在其他未删除的记录中出现
    private boolean isUrlUniqueToThisRecord(String url, Long id) {
        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Picture::getUrl, url)
                .ne(Picture::getId, id)
                .eq(Picture::getIsDelete, 0); // 假设0代表未删除

        return pictureService.count(queryWrapper) == 0;
    }

}