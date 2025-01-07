package com.zr.yunbackend.mapper;

import com.zr.yunbackend.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
*/
public interface PictureMapper extends BaseMapper<Picture> {
    @Select("SELECT * FROM zou_picture.picture WHERE isDelete = #{isDelete}")
    List<Picture> selectByIsDelete(@Param("isDelete") Integer isDelete);
}




