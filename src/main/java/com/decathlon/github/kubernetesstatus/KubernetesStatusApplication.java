package com.decathlon.github.kubernetesstatus;

import com.decathlon.github.kubernetesstatus.registration.OfficialKubeRegistration;
import com.decathlon.github.kubernetesstatus.registration.S2GNativeRegistration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ImportRuntimeHints({OfficialKubeRegistration.class, S2GNativeRegistration.class})
@EnableScheduling
public class KubernetesStatusApplication {

	public static void main(String[] args) {
		SpringApplication.run(KubernetesStatusApplication.class, args);
	}
}
