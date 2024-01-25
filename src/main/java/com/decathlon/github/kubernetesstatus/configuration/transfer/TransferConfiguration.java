package com.decathlon.github.kubernetesstatus.configuration.transfer;

import com.decathlon.github.kubernetesstatus.data.properties.AppProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// Native Conditional is not resolved at runtime without big config change.
//@ConditionalOnProperty(name = "app.mode", havingValue = "CAPTURE_AND_TRANSFER")
@Configuration(proxyBeanMethods=false)
public class TransferConfiguration {
    @Bean
    public WebClient webClientTransfer(
            OAuth2ClientProperties oauthProperties,
            AppProperties appProperties,
            WebClient.Builder builder) {

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10 * 1000)
                .proxyWithSystemProperties()
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(2 * 60 * 1000L, MILLISECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(2 * 60 * 1000L, MILLISECONDS));
                });


        WebClientReactiveClientCredentialsTokenResponseClient tokenResponseClient = new WebClientReactiveClientCredentialsTokenResponseClient();
        tokenResponseClient.setWebClient(
                builder
                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                        .build());

        // set the ReactiveOAuth2AccessTokenResponseClient with webclient configuration in the ReactiveOAuth2AuthorizedClientProvider
        ClientCredentialsReactiveOAuth2AuthorizedClientProvider authorizedClientProvider = new ClientCredentialsReactiveOAuth2AuthorizedClientProvider();
        authorizedClientProvider.setAccessTokenResponseClient(tokenResponseClient);

        var clientProperties=new OAuth2ClientPropertiesMapper(oauthProperties);

        var clientRepo= new InMemoryReactiveClientRegistrationRepository(new ArrayList<>(clientProperties.asClientRegistrations().values()));

        var oauthRepo=new InMemoryReactiveOAuth2AuthorizedClientService(clientRepo);


        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                        clientRepo, oauthRepo);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2Client.setDefaultClientRegistrationId(appProperties.getTransfer().getOauthRegistration());

        return builder
                .baseUrl(appProperties.getTransfer().getHost())
                .filter(oauth2Client)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

}
