package com.decathlon.github.kubernetesstatus.configuration.process;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.ProxySelector;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods=false)
public class RestConfiguration {

    @Bean
    public RestTemplate template(RestTemplateBuilder builder){
        HttpRoutePlanner routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());

        int timeout=30;

        ConnectionConfig connConfig = ConnectionConfig.custom()
                .setConnectTimeout(timeout, TimeUnit.SECONDS)
                .setSocketTimeout(timeout, TimeUnit.SECONDS)
                .build();

        PoolingHttpClientConnectionManager poolingConnManager = new PoolingHttpClientConnectionManager();
        poolingConnManager.setDefaultConnectionConfig(connConfig);

        var requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(timeout))
                .build();


        HttpClient httpClient = HttpClientBuilder
                .create()
                .setRoutePlanner(routePlanner)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(poolingConnManager)
                .build();

        return builder
                .requestFactory(()->new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

}

