package com.xuecheng.filesystem.service;

import com.alibaba.fastjson.JSON;
import com.xuecheng.filesystem.dao.FileSystemRepository;
import com.xuecheng.framework.domain.filesystem.FileSystem;
import com.xuecheng.framework.domain.filesystem.response.FileSystemCode;
import com.xuecheng.framework.domain.filesystem.response.UploadFileResult;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import org.csource.fastdfs.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class FileSystemService {
    @Autowired
    private FileSystemRepository fileSystemRepository;

    @Value("${xuecheng.fastdfs.tracker_servers}")
    String trackerServers;

    @Value("${xuecheng.fastdfs.connect_timeout_in_seconds}")
    int connect_timeout_in_seconds;

    @Value("${xuecheng.fastdfs.network_timeout_in_seconds}")
    int network_timeout_in_seconds;

    @Value("${xuecheng.fastdfs.charset}")
    String charset;

    public UploadFileResult upload(MultipartFile multipartFile, String filetag, String businesskey, String metadata) {

        if (multipartFile == null)
            ExceptionCast.cast(FileSystemCode.FS_UPLOADFILE_FILEISNULL);
        //上传到fastDFS，获得文件id
        String fileId = this.fdfs_upload(multipartFile);
        //把文件id和其他信息保存到数据库
        FileSystem fileSystem = new FileSystem();
        fileSystem.setFileId(fileId);
        fileSystem.setFilePath(fileId);
        //业务标识
        fileSystem.setBusinesskey(businesskey);
        //标签
        fileSystem.setFiletag(filetag);
        if (metadata != null) {
            Map map = JSON.parseObject(metadata, Map.class);
            fileSystem.setMetadata(map);
        }
        //名称
        fileSystem.setFileName(multipartFile.getOriginalFilename());
        //大小
        fileSystem.setFileSize(multipartFile.getSize());
        // 文件类型
        fileSystem.setFileType(multipartFile.getContentType());
        fileSystemRepository.save(fileSystem);
        return new UploadFileResult(CommonCode.SUCCESS, fileSystem);
    }

    private void initFdfsConfig() {
        try {
            ClientGlobal.initByTrackers(trackerServers);
            ClientGlobal.setG_connect_timeout(connect_timeout_in_seconds);
            ClientGlobal.setG_network_timeout(network_timeout_in_seconds);
            ClientGlobal.setG_charset(charset);
        } catch (Exception e) {
            e.printStackTrace();
            ExceptionCast.cast(FileSystemCode.FS_INITFDFSERROR);
        }
    }

    public String fdfs_upload(MultipartFile file) {
        this.initFdfsConfig();
        TrackerClient trackerClient = new TrackerClient();
        try {
            TrackerServer trackerServer = trackerClient.getConnection();
            StorageServer storeStorage = trackerClient.getStoreStorage(trackerServer);
            StorageClient1 storageClient1 = new StorageClient1(trackerServer, storeStorage);
            byte[] bytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String exName = originalFilename.substring(originalFilename.lastIndexOf("." )+ 1);
            return storageClient1.upload_file1(bytes, exName, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
