package com.decathlon.github.kubernetesstatus.controller;

import com.decathlon.github.kubernetesstatus.data.io.TransferData;
import com.decathlon.github.kubernetesstatus.data.io.TransferResponse;
import com.decathlon.github.kubernetesstatus.data.properties.AppMode;
import com.decathlon.github.kubernetesstatus.data.properties.AppProperties;
import com.decathlon.github.kubernetesstatus.service.github.GitHubEnvironmentService;
import com.decathlon.github.kubernetesstatus.service.github.GitHubException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class TransferController {
    private AppProperties appProperties;
    private GitHubEnvironmentService gitHubEnvironmentService;

    @ExceptionHandler(GitHubException.class)
    public ResponseEntity<JsonNode> handleGitHubException(GitHubException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getResponse());
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(@RequestBody TransferData transferData) {
        // Native do not work well with conditional
        if (appProperties.getMode() != AppMode.PROCESS) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (transferData.repo()==null || transferData.ref()==null || transferData.status()==null
                || transferData.repo().getEnvironment()==null || transferData.repo().getName()==null){
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
        }
        var l=gitHubEnvironmentService.executeUpdate(
                transferData.repo(),
                transferData.ref(),
                transferData.env(),
                transferData.status(),
                transferData.payload());
        //OK
        return ResponseEntity.ok(new TransferResponse(l));
    }
}
