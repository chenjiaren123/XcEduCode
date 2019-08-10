package com.xuecheng.manage_cms.dao;

import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.model.response.ResponseResult;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CmsPageRepository extends MongoRepository<CmsPage, String> {
    CmsPage findByPageNameAndPageAliase(String pageName, String pageAliase);

    CmsPage findByPageNameAndPageWebPathAndSiteId(String pageName, String pageWebPath, String siteId);

    CmsPage findByPageNameAndSiteIdAndPageWebPath(String pageName, String siteId, String pageWebPath);
}
