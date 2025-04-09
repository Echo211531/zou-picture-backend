package com.zr.yunbackend.manager.pictureSearch.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 以图搜图结果
 */
@Data
@Accessors(chain = true)
public class SearchPictureResult implements Serializable {

	//图片地址
	private String imageUrl;
	//图片名称
	private String imageName;
	//图片 KEY
	private String imageKey;

	private static final long serialVersionUID = 1L;
}