package com.decathlon.github.kubernetesstatus.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.matching.ContainsPattern;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.StubImport.stubImport;

public class MockServer {
    private static WireMockServer wireMockServer;


    @BeforeAll
    public static void creators() {
        wireMockServer = new WireMockServer(
                wireMockConfig()
                        .port(9999)
                        .extensions(new ResponseTemplateTransformer(false)
                        )
        );
        initializeFedid(wireMockServer);
        wireMockServer.start();
    }

    @AfterAll
    public static void destroyers() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    public static void initializeFedid(WireMockServer server) {
        server.importStubs(stubImport()
                .stub(get(urlEqualTo("/idp/jwks")).willReturn(aResponse()
                        .withHeader("Content-Type", "application/json;charset=UTF-8")
                        .withStatus(200).withBody("{\"keys\": [" + OauthTools.jwk.toPublicJWK().toString() + "]}")))
                .stub(post(urlEqualTo("/idp/token"))
                        .withRequestBody(new ContainsPattern("grant_type=client_credentials"))
                        .withHeader("authorization", new EqualToPattern("Basic bXktY2xpZW50LWlkOm15LWNsaWVudC1zZWNyZXQ=", false))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json;charset=UTF-8")
                                .withStatus(200).withBody("""
                                        {
                                          "access_token": "my-fed-access-token",
                                          "token_type": "Bearer",
                                          "expires_in": 7199
                                        }
                                        """))
                )
                .build()
        );
    }
}
