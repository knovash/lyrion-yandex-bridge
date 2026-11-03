package knovash.saclient.http;

import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class HttpClientFactory {

    private static final HttpClient httpClient;

    static {
        RequestConfig config = RequestConfig.custom()
                .setConnectionRequestTimeout(5000)   // время ожидания соединения из пула (мс)
                .setConnectTimeout(5000)             // таймаут установки соединения
                .setSocketTimeout(10000)             // таймаут чтения данных
                .build();

        // Ретрай при «смерти» keep-alive соединения: LMS закрывает простаивавшее соединение
        // ровно в момент его переиспользования из пула -> NoHttpResponseException (было x8391
        // с 28.09). По умолчанию POST не ретраится (non-idempotent), но наши вызовы (jsonrpc
        // LMS, callback Яндекса) безопасно повторить — команда просто применится ещё раз.
        HttpRequestRetryHandler retryHandler = (ex, executionCount, ctx) -> {
            if (executionCount > 3) return false;
            return ex instanceof NoHttpResponseException;
        };

        // не переиспользовать соединения, простаивавшие >2с, без проверки (лечит гонку keep-alive)
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setValidateAfterInactivity(2000);

        httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(config)
                .setRetryHandler(retryHandler)
                .setConnectionManager(connectionManager)
                .disableCookieManagement()           // отключаем куки, чтобы избежать предупреждений о невалидных expires
                .setUserAgent("SqueezeAlice/1.0")
                .build();
    }

    public static HttpClient getHttpClient() {
        return httpClient;
    }
}