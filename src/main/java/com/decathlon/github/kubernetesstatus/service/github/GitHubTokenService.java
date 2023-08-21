package com.decathlon.github.kubernetesstatus.service.github;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.decathlon.github.kubernetesstatus.data.properties.AppProperties;
import com.decathlon.github.kubernetesstatus.exception.TechnicalException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;


@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubTokenService {
    private static final Object LOCK=new Object();

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    private record TokenWrapper(Instant expire, String token){}
    private TokenWrapper token=new TokenWrapper(Instant.MIN, "");

    public String createToken() {
        var githubProperties=appProperties.getGithub();
        if (Strings.isNotBlank(githubProperties.getToken())){
            return githubProperties.getToken();
        }
        if (Instant.now().isBefore(token.expire())){
            return token.token();
        }
        synchronized(LOCK) {
            if (Instant.now().isBefore(token.expire())){
                return token.token();
            }

            var date = new Date();
            var jwt = generateJWT(date);

            var installationUrl = getInstallationUrl(jwt);

            var newToken=this.callForToken(jwt, installationUrl);
            token=newToken;
            return newToken.token();
        }
    }

    private TokenWrapper callForToken(String jwt, String installationUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept","application/vnd.github.machine-man-preview+json");
        headers.setBearerAuth(jwt);

        var atResponse=restTemplate.exchange(installationUrl,
                HttpMethod.POST, new HttpEntity<Void>(headers), JsonNode.class);

        if (!atResponse.getStatusCode().is2xxSuccessful()){
            throw new TechnicalException("Cannot get access token for github app: "+ atResponse);
        }

        var it=atResponse.getBody();

        assert it != null;
        var ldt = DateTimeFormatter.ISO_DATE_TIME.parse(it.get("expires_at").asText());
        log.info("Token expire at "+ DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ldt));

        return new TokenWrapper(Instant.from(ldt).minus(5, ChronoUnit.MINUTES), it.get("token").textValue());
    }

    private String getInstallationUrl(String jwt){
        var githubProperties=appProperties.getGithub();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept","application/vnd.github.machine-man-preview+json");
        headers.setBearerAuth(jwt);

        var installation=restTemplate.exchange(githubProperties.getGithubApi()+"/orgs/"+githubProperties.getOrgs()+"/installation",
                HttpMethod.GET, new HttpEntity<Void>(headers), JsonNode.class);

        if (!installation.getStatusCode().is2xxSuccessful()){
            throw new TechnicalException("Cannot get installation url: "+ installation);
        }

        var it=installation.getBody();
        assert it!=null;
        return it.get("access_tokens_url").textValue();
    }

    private RSAPrivateKey getPrivateKey() {
        var githubProperties=appProperties.getGithub();
        String secretKey=githubProperties.getApp().getPrivateKey();

        var pemParser = new PEMParser(new StringReader(secretKey));
        try {
            var keyObj=pemParser.readObject();
            if (keyObj==null){
                throw new IllegalArgumentException("GitHub private key is incorrect");
            }
            return convertToPrivateKey(keyObj);
        }catch(IOException e){
            throw new IllegalArgumentException("GitHub private key is not a PEM.", e);
        }
    }

    private RSAPrivateKey convertToPrivateKey(Object keyObj) throws PEMException {
        var converter = new JcaPEMKeyConverter();
        if (keyObj instanceof PEMKeyPair pkp){
            return (RSAPrivateKey)converter.getPrivateKey(pkp.getPrivateKeyInfo());
        }
        if (keyObj instanceof PrivateKeyInfo pki){
            return (RSAPrivateKey)converter.getPrivateKey(pki);
        }
        throw new IllegalArgumentException("GitHub Private key is not PKCS#1 nor PKCS#8");
    }

    private String generateJWT(Date date){
        var githubProperties=appProperties.getGithub();
        var exp=new Date(date.getTime()+10*60*1000); // 10 minutes

        var algorithm = Algorithm.RSA256(null, getPrivateKey());
        return JWT.create()
                .withIssuer(Long.toString(githubProperties.getApp().getId()))
                .withIssuedAt(date)
                .withExpiresAt(exp)
                .sign(algorithm);
    }


    @PostConstruct
    public void init(){
        var ghURI= URI.create(appProperties.getGithub().getGithubApi());
        var ghHost=ghURI.getHost();
        ClientHttpRequestInterceptor interceptor=(request, body, execution)->{
            if ( ghHost.equals(request.getURI().getHost()) &&
                    !request.getHeaders().containsKey("Authorization")) {
                request.getHeaders().set("Authorization","token "+this.createToken());
            }
            return execution.execute(request, body);
        };

        var lst=new ArrayList<>(restTemplate.getInterceptors());
        lst.add(interceptor);

        restTemplate.setInterceptors(lst);
    }
}
