package com.xuecheng.api.cms;

import com.xuecheng.framework.domain.cms.CmsConfig;
import io.swagger.annotations.ApiOperation;

public interface CmsConfigControllerApi {

    @ApiOperation("通过id查询页面")
    CmsConfig findById(String id);

}
