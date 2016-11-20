package com.yotouch.base.web.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yotouch.base.web.controller.BaseController;
import com.yotouch.core.Consts;
import com.yotouch.core.entity.Entity;
import com.yotouch.core.runtime.DbSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;

@Controller
public class QiniuNotiController extends BaseController{
    private static final Logger logger = LoggerFactory.getLogger(QiniuNotiController.class);

    @Value("${qiniu.domain:}")
    private String domain;

    @RequestMapping("/qiniu/persistent/notify")
    public @ResponseBody void receive(
            @RequestBody String jsonString,
            HttpServletRequest request
    ) {
        DbSession dbSession = this.getDbSession(request);
        jsonString = URLDecoder.decode(jsonString);
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode rootNode = mapper.readTree(jsonString);
            String persistentId = rootNode.get("id").toString();
            Entity attachment = getAttachment(dbSession, persistentId);
            if (attachment != null) {
                String key = rootNode.get("items").get(0).get("key").toString();
                String qiniuUrl = "http://" + this.domain + "/" + key;
                attachment.setValue("qiniuUrl", qiniuUrl);
                dbSession.save(attachment);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Entity getAttachment(DbSession dbSession, String persistentId) {
        Entity attachemnt = dbSession.queryOneRawSql(
                "attachment",
                "persistentId = ? AND status = ?",
                new Object[]{persistentId, Consts.STATUS_NORMAL}
        );
        return attachemnt;
    }
}


