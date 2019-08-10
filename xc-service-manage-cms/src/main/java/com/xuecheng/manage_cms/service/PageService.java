package com.xuecheng.manage_cms.service;

import com.alibaba.fastjson.JSON;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.domain.cms.CmsSite;
import com.xuecheng.framework.domain.cms.CmsTemplate;
import com.xuecheng.framework.domain.cms.request.QueryPageRequest;
import com.xuecheng.framework.domain.cms.response.CmsCode;
import com.xuecheng.framework.domain.cms.response.CmsPageResult;
import com.xuecheng.framework.domain.cms.response.CmsPostPageResult;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.*;
import com.xuecheng.manage_cms.config.RabbitmqConfig;
import com.xuecheng.manage_cms.dao.CmsConfigRepository;
import com.xuecheng.manage_cms.dao.CmsPageRepository;
import com.xuecheng.manage_cms.dao.CmsSiteRepository;
import com.xuecheng.manage_cms.dao.CmsTemplateRepository;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PageService {
    @Autowired
    private CmsPageRepository cmsPageRepository;

    @Autowired
    CmsConfigRepository cmsConfigRepository;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    CmsTemplateRepository cmsTemplateRepository;

    @Autowired
    GridFsTemplate gridFsTemplate;

    @Autowired
    GridFSBucket gridFSBucket;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    CmsSiteRepository cmsSiteRepository;


    /**
     * 页面列表分页查询
     *
     * @param page             当前页面
     * @param size             每页显示数量
     * @param queryPageRequest 查询条件
     * @return 页面列表
     */
    public QueryResponseResult findList(int page, int size, QueryPageRequest queryPageRequest) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;
        page -= 1;
        Pageable pageable = PageRequest.of(page, size);

        ExampleMatcher matcher = ExampleMatcher.matching().withMatcher("pageAliase", ExampleMatcher.GenericPropertyMatchers.contains());
        CmsPage cmsPage = new CmsPage();
        if (StringUtils.isNotEmpty(queryPageRequest.getSiteId()))
            cmsPage.setSiteId(queryPageRequest.getSiteId());
        if (StringUtils.isNotEmpty(queryPageRequest.getPageId()))
            cmsPage.setPageId(queryPageRequest.getPageId());
        if (StringUtils.isNotEmpty(queryPageRequest.getPageName()))
            cmsPage.setPageName(queryPageRequest.getPageName());
        if (StringUtils.isNotEmpty(queryPageRequest.getPageAliase()))
            cmsPage.setPageAliase(queryPageRequest.getPageAliase());
        if (StringUtils.isNotEmpty(queryPageRequest.getTemplateId()))
            cmsPage.setTemplateId(queryPageRequest.getTemplateId());

        Example<CmsPage> example = Example.of(cmsPage, matcher);
        Page<CmsPage> cmsPages = cmsPageRepository.findAll(example, pageable);
        QueryResult queryResult = new QueryResult();
        queryResult.setTotal(cmsPages.getTotalElements());
        queryResult.setList(cmsPages.getContent());
        QueryResponseResult queryResponseResult = new QueryResponseResult(CommonCode.SUCCESS, queryResult);
        return queryResponseResult;
    }

    /**
     * 添加
     *
     * @param cmsPage
     * @return
     */
    public CmsPageResult add(CmsPage cmsPage) {
        CmsPage cmsPage1 = cmsPageRepository.findByPageNameAndPageWebPathAndSiteId(cmsPage.getPageName(), cmsPage.getPageWebPath(), cmsPage.getSiteId());
        if (cmsPage1 != null) {
            ExceptionCast.cast(CmsCode.CMS_ADDPAGE_EXISTSNAME);
        }
        cmsPage.setPageId(null);
        cmsPageRepository.save(cmsPage);
        return new CmsPageResult(CommonCode.SUCCESS, cmsPage);
    }

    /**
     * 通过id查询页面
     *
     * @param id
     * @return
     */
    public CmsPage findById(String id) {
        Optional<CmsPage> optional = cmsPageRepository.findById(id);
        if (optional.isPresent())
            return optional.get();
        return null;
    }

    /**
     * 修改页面
     *
     * @param id
     * @param cmsPage
     * @return
     */
    public CmsPageResult edit(String id, CmsPage cmsPage) {
        CmsPage cmsPage1 = this.findById(id);
        if (cmsPage1 != null) {
            cmsPage1.setTemplateId(cmsPage.getTemplateId());
            cmsPage1.setSiteId(cmsPage.getSiteId());
            cmsPage1.setPageAliase(cmsPage.getPageAliase());
            cmsPage1.setPageName(cmsPage.getPageName());
            cmsPage1.setPageWebPath(cmsPage.getPageWebPath());
            cmsPage1.setPagePhysicalPath(cmsPage.getPagePhysicalPath());
            cmsPage1.setDataUrl(cmsPage.getDataUrl());

            CmsPage save = cmsPageRepository.save(cmsPage1);
            if (save != null)
                return new CmsPageResult(CommonCode.SUCCESS, save);
        }
        return new CmsPageResult(CommonCode.FAIL, null);
    }

    /**
     * 删除
     *
     * @param id
     * @return
     */
    public ResponseResult delete(String id) {
        CmsPage cmsPage = this.findById(id);
        if (cmsPage != null) {
            cmsPageRepository.deleteById(id);
            return new ResponseResult(CommonCode.SUCCESS);
        }
        return new ResponseResult(CommonCode.FAIL);
    }

    //页面静态化方法

    /**
     * 静态化程序获取页面的DataUrl
     * <p>
     * 静态化程序远程请求DataUrl获取数据模型。
     * <p>
     * 静态化程序获取页面的模板信息
     * <p>
     * 执行页面静态化
     */
    public String getPageHtml(String pageId) {

        //获取数据模型
        Map model = getModelByPageId(pageId);
        if (model == null) {
            //数据模型获取不到
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_DATAISNULL);
        }

        //获取页面的模板信息
        String template = getTemplateByPageId(pageId);
        if (StringUtils.isEmpty(template)) {
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_TEMPLATEISNULL);
        }

        //执行静态化
        String html = generateHtml(template, model);
        return html;

    }

    //执行静态化
    private String generateHtml(String templateContent, Map model) {
        //创建配置对象
        Configuration configuration = new Configuration(Configuration.getVersion());
        //创建模板加载器
        StringTemplateLoader stringTemplateLoader = new StringTemplateLoader();
        stringTemplateLoader.putTemplate("template", templateContent);
        //向configuration配置模板加载器
        configuration.setTemplateLoader(stringTemplateLoader);
        //获取模板
        try {
            Template template = configuration.getTemplate("template");
            //调用api进行静态化
            String content = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            return content;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //获取页面的模板信息
    private String getTemplateByPageId(String pageId) {
        //取出页面的信息
        CmsPage cmsPage = this.findById(pageId);
        if (cmsPage == null) {
            //页面不存在
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        }
        //获取页面的模板id
        String templateId = cmsPage.getTemplateId();
        if (StringUtils.isEmpty(templateId)) {
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_TEMPLATEISNULL);
        }
        //查询模板信息
        Optional<CmsTemplate> optional = cmsTemplateRepository.findById(templateId);
        if (optional.isPresent()) {
            CmsTemplate cmsTemplate = optional.get();
            //获取模板文件id
            String templateFileId = cmsTemplate.getTemplateFileId();
            //从GridFS中取模板文件内容
            //根据文件id查询文件
            GridFSFile gridFSFile = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(templateFileId)));

            //打开一个下载流对象
            GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(gridFSFile.getObjectId());
            //创建GridFsResource对象，获取流
            GridFsResource gridFsResource = new GridFsResource(gridFSFile, gridFSDownloadStream);
            //从流中取数据
            try {
                String content = IOUtils.toString(gridFsResource.getInputStream(), "utf-8");
                return content;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;

    }

    //获取数据模型
    private Map getModelByPageId(String pageId) {
        //取出页面的信息
        CmsPage cmsPage = this.findById(pageId);
        if (cmsPage == null) {
            //页面不存在
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        }
        //取出页面的dataUrl
        String dataUrl = cmsPage.getDataUrl();
        if (StringUtils.isEmpty(dataUrl)) {
            //页面dataUrl为空
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_DATAURLISNULL);
        }
        //通过restTemplate请求dataUrl获取数据
        ResponseEntity<Map> forEntity = restTemplate.getForEntity(dataUrl, Map.class);
        Map body = forEntity.getBody();
        return body;

    }

    //页面发布
    public ResponseResult postPage(String pageId) {
        String pageHtml = this.getPageHtml(pageId);
        if (StringUtils.isEmpty(pageHtml))
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_HTMLISNULL);

        CmsPage cmsPage = this.saveHtml(pageId, pageHtml);

        //发送消息
        sendPostPage(pageId);
        return new ResponseResult(CommonCode.SUCCESS);

    }
    //向mq 发送消息
    private void sendPostPage(String pageId) {
        Optional<CmsPage> optional = cmsPageRepository.findById(pageId);
        if (!optional.isPresent())
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);

        Map<String, String> map = new HashMap<>();
        map.put("pageId", pageId);
        String msg = JSON.toJSONString(map);

        rabbitTemplate.convertAndSend(RabbitmqConfig.EX_ROUTING_CMS_POSTPAGE, optional.get().getSiteId(), msg);
    }

    //保存静态页面
    private CmsPage saveHtml(String pageId, String content) {
        Optional<CmsPage> optional = cmsPageRepository.findById(pageId);
        if (!optional.isPresent())
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        CmsPage cmsPage = optional.get();
        //存储前先删除
        if (StringUtils.isNotEmpty(cmsPage.getHtmlFileId()))
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(cmsPage.getHtmlFileId())));

        ObjectId objectId = null;
        try {
            InputStream inputStream = IOUtils.toInputStream(content, "utf-8");
            objectId = gridFsTemplate.store(inputStream, cmsPage.getPageName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        cmsPage.setHtmlFileId(objectId.toHexString());

        cmsPageRepository.save(cmsPage);
        return cmsPage;
    }

    //保存页面，有则更新，没有则添加
    public CmsPageResult save(CmsPage cmsPage) {
        //判断页面是否存在
        //CmsPage one = cmsPageRepository.findByPageNameAndSiteIdAndPageWebPath(cmsPage.getPageName(), cmsPage.getSiteId(), cmsPage.getPageWebPath());
        /**
         * 有问题，测试用
         */
        CmsPage one = cmsPageRepository.findById(cmsPage.getPageId()).get();
        if (one != null) {
            //进行更新
            return this.edit(one.getPageId(), cmsPage);
        }
        return this.add(cmsPage);
    }

    //一键发布页面
    public CmsPostPageResult postPageQuick(CmsPage cmsPage) {
        //保存到cms_page
        CmsPageResult cmsPageResult = this.save(cmsPage);
        CmsPage cmsPage1 = cmsPageResult.getCmsPage();
        //页面发布
        ResponseResult responseResult = this.postPage(cmsPage1.getPageId());

        if (!responseResult.isSuccess())
            ExceptionCast.cast(CommonCode.FAIL);
        CmsSite cmsSite = this.findCmsSiteById(cmsPage1.getSiteId());
        return new CmsPostPageResult(CommonCode.SUCCESS,cmsSite.getSiteDomain()
                +cmsSite.getSiteWebPath()+cmsPage1.getPageWebPath()+cmsPage1.getPageName());// cmsSite.siteDomain+cmsSite.siteWebPath+ cmsPage.pageWebPath + cmsPage.pageName
    }

    //根据站点id查询站点信息
    public CmsSite findCmsSiteById(String siteId){
        Optional<CmsSite> optional = cmsSiteRepository.findById(siteId);
        if(optional.isPresent()){
            return optional.get();
        }
        return null;
    }
}
