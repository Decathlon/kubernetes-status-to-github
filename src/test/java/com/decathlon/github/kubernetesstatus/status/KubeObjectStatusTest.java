package com.decathlon.github.kubernetesstatus.status;

import com.decathlon.github.kubernetesstatus.service.kstatus.data.KubeObjectStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KubeObjectStatusTest {

    @Test
    void validChange(){
        assertThat(KubeObjectStatus.IN_PROGRESS.canMoveTo(KubeObjectStatus.FAILED)).isTrue();
        for (var k: KubeObjectStatus.values()){
            if (k!=KubeObjectStatus.UNKNOWN) {
                assertThat(KubeObjectStatus.UNKNOWN.canMoveTo(k)).isTrue();
            }
            assertThat(KubeObjectStatus.CURRENT.canMoveTo(k)).isFalse();
        }
    }
}
