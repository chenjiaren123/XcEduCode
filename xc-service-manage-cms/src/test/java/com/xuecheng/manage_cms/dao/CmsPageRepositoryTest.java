package com.xuecheng.manage_cms.dao;

import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.model.request.RequestData;
import com.xuecheng.manage_cms.dao.CmsPageRepository;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import javax.sound.midi.Soundbank;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Optional;

@SpringBootTest
@RunWith(SpringRunner.class)
public class CmsPageRepositoryTest {
    @Autowired
    CmsPageRepository cmsPageRepository;

    @Test
    public void testFindAll() {
        System.out.println(cmsPageRepository.findAll().size());
    }

    @Test
    public void testFindPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<CmsPage> page = cmsPageRepository.findAll(pageable);
        System.out.println(page);
    }

    //修改
    @Test
    public void testUpdate() {
        Optional<CmsPage> one = cmsPageRepository.findById("5abefd525b05aa293098fca6");
        if (one.isPresent()) {
            CmsPage cmsPage = one.get();
            cmsPage.setPageName("cccccc");
            cmsPageRepository.save(cmsPage);
        }

    }

    @Test
    public void testFindByPageNameAndPageAliase() {
        CmsPage aliase = cmsPageRepository.findByPageNameAndPageAliase("cccccc", "ccc");
        System.out.println(aliase);
    }

    @Autowired
    RestTemplate restTemplate;
    @Autowired
    GridFsTemplate gridFsTemplate;

    @Test
    public void testRestTemplate() {
        ResponseEntity<Map> forEntity = restTemplate.getForEntity("http://localhost:31001/cms/config/getModel/5a791725dd573c3574ee333f", Map.class);
        System.out.println(forEntity);
    }

}
