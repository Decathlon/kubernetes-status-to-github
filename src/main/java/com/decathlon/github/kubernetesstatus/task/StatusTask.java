package com.decathlon.github.kubernetesstatus.task;

import com.decathlon.github.kubernetesstatus.data.properties.AppMode;
import com.decathlon.github.kubernetesstatus.data.properties.AppProperties;
import com.decathlon.github.kubernetesstatus.service.CaptureService;
import com.decathlon.github.kubernetesstatus.service.kubernetes.GHDService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@AllArgsConstructor
@Slf4j
public class StatusTask {
    private AppProperties appProperties;
    private GHDService ghdService;
    private CaptureService captureService;

    @Scheduled(fixedDelayString = "#{appProperties.kubernetes.getRefreshInSecond()}", timeUnit = TimeUnit.SECONDS)
    public void check(){
        if (appProperties.getMode()== AppMode.PROCESS){
            return;
        }
        log.info("Checking status of deployments");
        ghdService.listDeployment().forEach(deployment -> {
            try {
                log.info("[{}/{}] Checking status of GitHub deployment", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName());
                captureService.capture(deployment);
            }catch(RuntimeException e){
                log.error("[{}/{}] Error while checking status of deployment: {}", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName(), e.getMessage());
            }
        });
    }
}
