package com.zr.yunbackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUploadByBatchRequest implements Serializable {
   //搜索词  
    private String searchText;  
    //抓取数量  
    private Integer count = 10;
    //名称前缀
    private String namePrefix;

    private static final long serialVersionUID = 1L;
}