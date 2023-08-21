package com.decathlon.github.kubernetesstatus.service.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpClientErrorException;

public class GitHubException extends RuntimeException {
    private final int statusCode;
    private final transient JsonNode response;

    public GitHubException(int statusCode, String message) {
        super();

        ObjectMapper objetMapper=new ObjectMapper();
        var temp=objetMapper.createObjectNode();
        temp.put("message",message);
        response=temp;
        this.statusCode = statusCode;
    }

    public GitHubException(HttpClientErrorException e) {
        super(e);
        this.response=processException(e);
        this.statusCode = e.getStatusCode().value();
    }

    private JsonNode processException(HttpClientErrorException e) {
        ObjectMapper objetMapper=new ObjectMapper();

        try{
            return objetMapper.readTree(e.getResponseBodyAsString());
        }catch(Exception notJson){
            var temp=objetMapper.createObjectNode();
            temp.put("message",e.getMessage());
            return temp;
        }
    }

    public int getStatusCode() {
        return statusCode;
    }

    public JsonNode getResponse() {
        return response;
    }
}
