package knovash.saclient.yandex.provider;

import lombok.extern.log4j.Log4j2;
import knovash.saclient.Context;

@Log4j2
public class ProviderUserUnlink {

    public static Context providerUserUnlinkRun(Context context) {
        log.info("");

        String xRequestId = context.requestHeaders.getFirst("X-request-id");
        log.info("XREQUESTID: " + xRequestId);

        String json = "{\"request_id\":\"" + xRequestId + "\"}";
        log.info("RESPONSE: " + json);
        context.bodyResponse = json;
        context.code = 200;
        return context;
    }
}