package com.yotouch.base.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.yotouch.core.entity.Entity;

public interface AttachmentService {

    Entity saveAttachment(InputStream inputStream);

}

