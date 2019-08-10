package com.xuecheng.api.course;

import com.xuecheng.framework.domain.course.ext.CategoryNode;
import io.swagger.annotations.ApiOperation;

public interface CategoryControllerApi {
    @ApiOperation("课程分类查询")
    public CategoryNode findCategoryList();
}
