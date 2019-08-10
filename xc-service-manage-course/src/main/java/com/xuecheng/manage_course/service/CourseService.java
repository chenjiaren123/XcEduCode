package com.xuecheng.manage_course.service;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.domain.cms.response.CmsPageResult;
import com.xuecheng.framework.domain.cms.response.CmsPostPageResult;
import com.xuecheng.framework.domain.course.*;
import com.xuecheng.framework.domain.course.ext.CourseInfo;
import com.xuecheng.framework.domain.course.ext.CourseView;
import com.xuecheng.framework.domain.course.ext.TeachplanNode;
import com.xuecheng.framework.domain.course.request.CourseListRequest;
import com.xuecheng.framework.domain.course.response.AddCourseResult;
import com.xuecheng.framework.domain.course.response.CourseCode;
import com.xuecheng.framework.domain.course.response.CoursePublishResult;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.QueryResult;
import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.manage_course.client.CmsPageClient;
import com.xuecheng.manage_course.dao.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class CourseService {
    @Autowired
    TeachplanMapper teachplanMapper;
    @Autowired
    TeachplanRepository teachplanRepository;

    @Autowired
    CourseBaseRepository courseBaseRepository;

    @Autowired
    CourseMapper courseMapper;

    @Autowired
    CourseMarketRepository courseMarketRepository;

    @Autowired
    CoursePicRepository coursePicRepository;

    @Autowired
    CmsPageClient cmsPageClient;

    @Autowired
    CoursePubRepository coursePubRepository;

    @Autowired
    TeachplanMediaRepository teachplanMediaRepository;

    @Autowired
    TeachplanMediaPubRepository teachplanMediaPubRepository;

    @Value("${course-publish.dataUrlPre}")
    private String publish_dataUrlPre;
    @Value("${course-publish.pagePhysicalPath}")
    private String publish_page_physicalpath;
    @Value("${course-publish.pageWebPath}")
    private String publish_page_webpath;
    @Value("${course-publish.siteId}")
    private String publish_siteId;
    @Value("${course-publish.templateId}")
    private String publish_templateId;
    @Value("${course-publish.previewUrl}")
    private String previewUrl;

    /**
     * 课程计划查询
     */
    public TeachplanNode findTeachplanList(String courseId) {
        return teachplanMapper.selectList(courseId);
    }

    /**
     * 添加课程计划
     *
     * @param teachplan
     * @return
     */
    @Transactional
    public ResponseResult addTeachplan(Teachplan teachplan) {
        //不选择上级结点表示当前课程计划为该课程的一级结点。
        //当添加该课程在课程计划中还没有节点时要自动添加课程的根结点。

        //判断请求的参数课程计划是否为空，是否包含课程id和课程计划名称，课程id和课程计划名称不能为空
        if (teachplan == null || StringUtils.isEmpty(teachplan.getCourseid()) || StringUtils.isEmpty(teachplan.getPname()))
            ExceptionCast.cast(CommonCode.INVALID_PARAM);


        String parentid = teachplan.getParentid();
        if (StringUtils.isEmpty(parentid)) {
            //获取课程根结点，关联到添加的课程计划，如果没有则添加根结点
            parentid = this.getTeachplanRoot(teachplan.getCourseid());
        }

        Optional<Teachplan> optional = teachplanRepository.findById(parentid);
        if (!optional.isPresent())
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        Teachplan teachplanParent = optional.get();//获取该课程计划的上级结点

        String parentGrade = teachplanParent.getGrade();
        teachplan.setParentid(parentid);
        teachplan.setStatus("0");
        teachplan.setCourseid(teachplanParent.getCourseid());

        //设置课程计划的分类等级
        if ("1".equals(parentGrade)) teachplan.setGrade("2");
        if ("2".equals(parentGrade)) teachplan.setGrade("3");
        teachplanRepository.save(teachplan);
        return new ResponseResult(CommonCode.SUCCESS);

    }

    //获取课程根结点，如果没有则添加根结点，返回根节点id
    public String getTeachplanRoot(String courseId) {
        //获取课程基本信息
        Optional<CourseBase> optional = courseBaseRepository.findById(courseId);
        //课程不存在时无法添加课程计划
        if (!optional.isPresent()) {
            ExceptionCast.cast(CourseCode.COURSE_BASE_NOTEXISTS);
        }

        CourseBase courseBase = optional.get();
        List<Teachplan> teachplanList = teachplanRepository.findByCourseidAndParentid(courseId, "0");
        //该课程没有一级分类时，创建一个课程计划作为一级分类
        if (teachplanList == null) {
            Teachplan teachplan = new Teachplan();
            teachplan.setParentid("0");//一级分类父id为0
            teachplan.setCourseid(courseId);
            teachplan.setPname(courseBase.getName());
            teachplan.setGrade("1");//设为一级分类
            teachplan.setStatus("0");//状态为0，待审核
            teachplanRepository.save(teachplan);
            return teachplan.getId();
        }
        //该课程的一级分类存在时，将该一级分类作为添加的课程计划的父id
        return teachplanList.get(0).getId();//由于parentid等于0时，为一级课程分类，此时list.size()=1;
    }

    /**
     * 查询我的课程列表
     *
     * @param page
     * @param size
     * @param courseListRequest
     * @return
     */
    //分页查询
    public QueryResponseResult findCourseList(int page, int size, CourseListRequest courseListRequest) {
        if (courseListRequest == null) courseListRequest = new CourseListRequest();
        if (page <= 0) page = 0;
        if (size <= 0) size = 20;
        PageHelper.startPage(page, size);

        Page<CourseInfo> courseListPage = courseMapper.findCourseListPage(courseListRequest);
        QueryResult queryResult = new QueryResult();
        queryResult.setList(courseListPage.getResult());
        queryResult.setTotal(courseListPage.getTotal());
        QueryResponseResult queryResponseResult = new QueryResponseResult(CommonCode.SUCCESS, queryResult);
        return queryResponseResult;
    }

    /**
     * 添加课程基础信息
     *
     * @param courseBase
     * @return
     */
    public AddCourseResult addCourseBase(CourseBase courseBase) {
        //添加课程基本信息时，必须保证课程的大小分类，课程名称，课程等级不能为空
        if (courseBase.getName() == null || courseBase.getMt() == null || courseBase.getGrade() == null || courseBase.getSt() == null)
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        courseBase.setStatus("202001");
        CourseBase save = courseBaseRepository.save(courseBase);
        AddCourseResult addCourseResult = new AddCourseResult(CommonCode.SUCCESS, save.getId());
        return addCourseResult;
    }

    /**
     * 获取课程基础信息
     *
     * @param courseId
     * @return
     * @throws RuntimeException
     */
    public CourseBase getCourseBaseById(String courseId) throws RuntimeException {
        Optional<CourseBase> optional = courseBaseRepository.findById(courseId);
        if (optional.isPresent())
            return optional.get();
        return null;
    }

    /**
     * 更新课程基础信息
     *
     * @param id
     * @param courseBase
     * @return
     */
    public ResponseResult updateCourseBase(String id, CourseBase courseBase) {
        //先查询后插入
        CourseBase one = this.getCourseBaseById(id);
        if (one == null)
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        //修改课程信息
        one.setName(courseBase.getName());
        one.setMt(courseBase.getMt());
        one.setSt(courseBase.getSt());
        one.setGrade(courseBase.getGrade());
        one.setStudymodel(courseBase.getStudymodel());
        one.setUsers(courseBase.getUsers());
        one.setDescription(courseBase.getDescription());
        CourseBase save = courseBaseRepository.save(one);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    /**
     * 获取课程营销信息
     *
     * @param courseId
     * @return
     */
    public CourseMarket getCourseMarketById(String courseId) {
        Optional<CourseMarket> optional = courseMarketRepository.findById(courseId);
        if (optional.isPresent())
            return optional.get();
        return null;
    }

    /**
     * 更新课程营销信息
     *
     * @param id
     * @param courseMarket
     * @return
     */
    public CourseMarket updateCourseMarket(String id, CourseMarket courseMarket) {
        CourseMarket one = this.getCourseMarketById(id);
        //如果没有，添加营销信息
        if (one == null) {
            one = new CourseMarket();
            BeanUtils.copyProperties(courseMarket, one);
            one.setId(id);
        } else {
            one.setCharge(courseMarket.getCharge());
            one.setStartTime(courseMarket.getStartTime());//课程有效期，开始时间
            one.setEndTime(courseMarket.getEndTime());//课程有效期，结束时间 
            one.setPrice(courseMarket.getPrice());
            one.setQq(courseMarket.getQq());
            one.setValid(courseMarket.getValid());
        }
        return courseMarketRepository.save(one);
    }

    /**
     * 添加课程图片
     *
     * @param courseId
     * @param pic
     * @return
     */
    //向课程管理数据添加课程与图片的关联信息
    @Transactional
    public ResponseResult addCoursePic(String courseId, String pic) {
        CoursePic coursePic = null;
        Optional<CoursePic> optional = coursePicRepository.findById(courseId);
        if (optional.isPresent())
            coursePic = optional.get();
        if (coursePic == null)
            coursePic = new CoursePic();
        coursePic.setCourseid(courseId);
        coursePic.setPic(pic);
        coursePicRepository.save(coursePic);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    /**
     * 获取课程图片信息
     *
     * @param courseId
     * @return
     */
    public CoursePic findCoursePic(String courseId) {
        Optional<CoursePic> optional = coursePicRepository.findById(courseId);
        if (optional.isPresent())
            return optional.get();
        return null;
    }

    /**
     * 删除课程图片
     *
     * @param courseId
     * @return
     */
    @Transactional
    public ResponseResult deleteCoursePic(String courseId) {
        if (coursePicRepository.deleteByCourseid(courseId) > 0)
            return new ResponseResult(CommonCode.SUCCESS);
        return new ResponseResult(CommonCode.FAIL);

    }

    /**
     * 课程视图查询。包括基本信息，图片，营销，课程计划
     *
     * @param id
     * @return
     */
    public CourseView getCourseView(String id) {
        CourseView courseView = new CourseView();
        Optional<CourseBase> courseBase = courseBaseRepository.findById(id);
        if (courseBase.isPresent())
            courseView.setCourseBase(courseBase.get());

        Optional<CoursePic> coursePic = coursePicRepository.findById(id);
        if (coursePic.isPresent())
            courseView.setCoursePic(coursePic.get());

        Optional<CourseMarket> courseMarket = courseMarketRepository.findById(id);
        if (courseMarket.isPresent())
            courseView.setCourseMarket(courseMarket.get());

        TeachplanNode teachplanNode = this.findTeachplanList(id);
        courseView.setTeachplanNode(teachplanNode);
        return courseView;
    }

    /**
     * 课程预览
     *
     * @param id
     * @return
     */
    public CoursePublishResult preview(String id) {
        CourseBase courseBase = this.getCourseBaseById(id);
        //请求cms添加页面
        //准备cmsPage信息
        CmsPage cmsPage = new CmsPage();
        cmsPage.setSiteId(publish_siteId);//站点id
        cmsPage.setDataUrl(publish_dataUrlPre + id);//数据模型url
        cmsPage.setPageName(id + ".html");//页面名称
        cmsPage.setPageAliase(courseBase.getName());//页面别名，就是课程名称
        cmsPage.setPagePhysicalPath(publish_page_physicalpath);//页面物理路径
        cmsPage.setPageWebPath(publish_page_webpath);//页面webpath
        cmsPage.setTemplateId(publish_templateId);//页面模板id
        //远程调用cms
        CmsPageResult cmsPageResult = cmsPageClient.saveCmsPage(cmsPage);
        if (!cmsPageResult.isSuccess())
            return new CoursePublishResult(CommonCode.FAIL, null);

        return new CoursePublishResult(CommonCode.SUCCESS, previewUrl + cmsPageResult.getCmsPage().getPageId());
    }

    /**
     * 课程发布
     *
     * @param id
     * @return
     */
    @Transactional
    public CoursePublishResult publish(String id) {
        CourseBase courseBase = this.getCourseBaseById(id);
        //准备页面信息
        CmsPage cmsPage = new CmsPage();
        cmsPage.setSiteId(publish_siteId);//站点id
        cmsPage.setDataUrl(publish_dataUrlPre + id);//数据模型url
        cmsPage.setPageName(id + ".html");//页面名称
        cmsPage.setPageAliase(courseBase.getName());//页面别名，就是课程名称
        cmsPage.setPagePhysicalPath(publish_page_physicalpath);//页面物理路径
        cmsPage.setPageWebPath(publish_page_webpath);//页面webpath
        cmsPage.setTemplateId(publish_templateId);//页面模板id
        cmsPage.setPageId(id);
        //调用cms一键发布接口将课程详情页面发布到服务器
        CmsPostPageResult cmsPostPageResult = cmsPageClient.postPageQuick(cmsPage);
        if (!cmsPostPageResult.isSuccess())
            return new CoursePublishResult(CommonCode.FAIL, null);

        //保存课程的发布状态为“已发布”
        CourseBase courseBase1 = this.saveCoursePubState(id);
        if (courseBase1 == null)
            return new CoursePublishResult(CommonCode.FAIL, null);

        //保存课程索引信息
        //先创建一个coursePub对象
        CoursePub coursePub = createCoursePub(id);
        //将coursePub对象保存到数据库
        saveCoursePub(id, coursePub);


        //缓存课程的信息
        //...

        this.saveTeachplanMediaPub(id);
        return new CoursePublishResult(CommonCode.SUCCESS, cmsPostPageResult.getPageUrl());
    }


    //将coursePub对象保存到数据库
    private CoursePub saveCoursePub(String id, CoursePub coursePub) {
        CoursePub coursePubNew = null;
        //根据课程id查询coursePub
        Optional<CoursePub> coursePubOptional = coursePubRepository.findById(id);
        if (coursePubOptional.isPresent()) {
            coursePubNew = coursePubOptional.get();
        } else {
            coursePubNew = new CoursePub();
        }

        //将coursePub对象中的信息保存到coursePubNew中
        BeanUtils.copyProperties(coursePub, coursePubNew);
        coursePubNew.setId(id);
        //时间戳,给logstach使用
        coursePubNew.setTimestamp(new Date());
        //发布时间
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
        String date = simpleDateFormat.format(new Date());
        coursePubNew.setPubTime(date);
        coursePubRepository.save(coursePubNew);
        return coursePubNew;
    }

    //创建coursePub对象
    private CoursePub createCoursePub(String id) {
        CoursePub coursePub = new CoursePub();
        //根据课程id查询course_base
        Optional<CourseBase> baseOptional = courseBaseRepository.findById(id);
        if (baseOptional.isPresent()) {
            CourseBase courseBase = baseOptional.get();
            //将courseBase属性拷贝到CoursePub中
            BeanUtils.copyProperties(courseBase, coursePub);
        }

        //查询课程图片
        Optional<CoursePic> picOptional = coursePicRepository.findById(id);
        if (picOptional.isPresent()) {
            CoursePic coursePic = picOptional.get();
            BeanUtils.copyProperties(coursePic, coursePub);
        }

        //课程营销信息
        Optional<CourseMarket> marketOptional = courseMarketRepository.findById(id);
        if (marketOptional.isPresent()) {
            CourseMarket courseMarket = marketOptional.get();
            BeanUtils.copyProperties(courseMarket, coursePub);
        }

        //课程计划信息
        TeachplanNode teachplanNode = teachplanMapper.selectList(id);
        String jsonString = JSON.toJSONString(teachplanNode);
        //将课程计划信息json串保存到 course_pub中
        coursePub.setTeachplan(jsonString);
        return coursePub;
    }

    //更新课程状态为已发布 202002
    private CourseBase saveCoursePubState(String courseId) {
        CourseBase courseBaseById = this.getCourseBaseById(courseId);
        courseBaseById.setStatus("202002");
        courseBaseRepository.save(courseBaseById);
        return courseBaseById;
    }

    /**
     * 保存媒资信息
     *
     * @param teachplanMedia
     * @return
     */
    public ResponseResult saveMedia(TeachplanMedia teachplanMedia) {
        if (teachplanMedia == null)
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        //查询课程计划
        Optional<Teachplan> optional = teachplanRepository.findById(teachplanMedia.getTeachplanId());
        if (!optional.isPresent())
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        //只允许为叶子结点课程计划选择视频
        Teachplan teachplan = optional.get();
        if (StringUtils.isEmpty(teachplan.getGrade()) || !teachplan.getGrade().equals("3"))
            ExceptionCast.cast(CourseCode.COURSE_MEDIA_TEACHPLAN_GRADEERROR);
        TeachplanMedia one = null;
        Optional<TeachplanMedia> optionalTeachplanMedia = teachplanMediaRepository.findById(teachplan.getId());
        if (optionalTeachplanMedia.isPresent()) {
            one = optionalTeachplanMedia.get();
        } else {
            one = new TeachplanMedia();
        }

        //保存媒资信息与课程计划信息
        one.setTeachplanId(teachplanMedia.getTeachplanId());
        one.setCourseId(teachplanMedia.getCourseId());
        one.setMediaId(teachplanMedia.getMediaId());
        one.setMediaUrl(teachplanMedia.getMediaUrl());
        one.setMediaFileOriginalName(teachplanMedia.getMediaFileOriginalName());
        teachplanMediaRepository.save(one);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    //保存课程计划媒资信息
    private void saveTeachplanMediaPub(String courseId) {
        long deleteByCourseId = teachplanMediaPubRepository.deleteByCourseId(courseId);
        List<TeachplanMedia> teachplanMediaList = teachplanMediaRepository.findByCourseId(courseId);
        if (teachplanMediaList == null || teachplanMediaList.isEmpty())
            ExceptionCast.cast(CommonCode.FAIL);
        List<TeachplanMediaPub> teachplanMediaPubList = new ArrayList<>();
        //将课程计划媒资信息存储到索引表
        for (TeachplanMedia teachplanMedia : teachplanMediaList) {
            TeachplanMediaPub teachplanMediaPub = new TeachplanMediaPub();
            BeanUtils.copyProperties(teachplanMedia, teachplanMediaPub);
            teachplanMediaPubList.add(teachplanMediaPub);
        }
        teachplanMediaPubRepository.saveAll(teachplanMediaPubList);
    }

}
