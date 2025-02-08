package com.zr.yunbackend.model.vo;

import cn.hutool.json.JSONUtil;
import com.zr.yunbackend.model.entity.Picture;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class PictureVO implements Serializable {
  
    //id 
    private Long id;  
  
    //图片 url 
    private String url;  
  
    //图片名称  
    private String name;  
  
    //简介    
    private String introduction;  
  
    //标签   
    private List<String> tags;  //实体类picture这里是string类型
  
    //分类 
    private String category;  
  
    //文件体积 
    private Long picSize;  
  
    //图片宽度  
    private Integer picWidth;  
  
    //图片高度  
    private Integer picHeight;  
  
    //图片比例   
    private Double picScale;  
  
    //图片格式   
    private String picFormat;  
  
    //用户 id 
    private Long userId;

    //权限列表
    private List<String> permissionList = new ArrayList<>();

    /**
     * 空间 id
     */
    private Long spaceId;
    /**
     * 图片主色调
     */
    private String picColor;
    /**
     * 图片指纹
     */
    private long hashValue;

    //创建时间  
    private Date createTime;
  
    //编辑时间 
    private Date editTime;  
  
    //更新时间 
    private Date updateTime;

  
    //创建用户信息 
    private UserVO user;  
    private static final long serialVersionUID = 1L;  
  
    //编写 Picture 实体类和该 VO 类的转换方法，便于后续快速传值。
    //封装类转对象  
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {  
            return null;  
        }  
        Picture picture = new Picture();  
        BeanUtils.copyProperties(pictureVO, picture);
        // 类型不同，需要转换  比如上面的tags类型，手动转换即可
        picture.setTags(JSONUtil.toJsonStr(pictureVO.getTags()));
        return picture;  
    }  
  
    //对象转封装类    
    public static PictureVO objToVo(Picture picture) {  
        if (picture == null) {  
            return null;  
        }  
        PictureVO pictureVO = new PictureVO();  
        BeanUtils.copyProperties(picture, pictureVO);  
        // 类型不同，需要转换  
        pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));  
        return pictureVO;  
    }  
}