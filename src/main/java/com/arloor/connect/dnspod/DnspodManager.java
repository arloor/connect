package com.arloor.connect.dnspod;

import com.arloor.connect.common.JsonUtil;
import com.arloor.connect.dnspod.param.RecordListParam;
import com.arloor.connect.dnspod.result.RecordList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

public class DnspodManager {
    private static final Logger log = LoggerFactory.getLogger(DnspodManager.class);
    private static final String API_URL = "https://dnsapi.cn/";


    public static RecordList fetchRecordList() throws IOException {
        String content = HttpUtil.doPostForm(API_URL + "Record.List", JsonUtil.fromJson(JsonUtil.toJson(new RecordListParam()), HashMap.class), 100);
        return JsonUtil.fromJson(content, RecordList.class);
    }

    public static String fetchCurrentIp() throws IOException {
        String content = HttpUtil.get("https://sg.gcall.me", 100);
        return content;
    }

    public static void ddns() throws IOException {
        RecordList recordList = fetchRecordList();
        for (RecordList.Record record : recordList.getRecords()) {
            if (record.getName().equals(System.getenv("dnspod_subdomain"))) {
                String lastIp = record.getValue();
                String currentIp = fetchCurrentIp();
                if (!Objects.equals(currentIp, lastIp)) {
                    modifyRecord(record, currentIp);
                } else {
                    log.info("ip未变化：{} @{}", lastIp,new Date());
                }
            }
        }
    }

    private static void modifyRecord(RecordList.Record record, String currentIp) throws IOException {
        HashMap<String, String> ddnsParam = new HashMap<>();
        ddnsParam.put("login_token",System.getenv("dnspod_token"));
        ddnsParam.put("format","json");
        ddnsParam.put("error_on_empty","no");
        ddnsParam.put("domain",System.getenv("dnspod_domain"));
        ddnsParam.put("record_id", record.getId());
        ddnsParam.put("sub_domain",System.getenv("dnspod_subdomain"));
        ddnsParam.put("record_line_id", record.getLine_id());
        ddnsParam.put("value", currentIp);
        String s = HttpUtil.doPostForm(API_URL + "Record.Ddns", ddnsParam, 200);
        log.info(s);
    }
}
