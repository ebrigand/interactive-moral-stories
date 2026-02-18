package com.istory.storyengine.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MoralEngineServiceTest {

    MoralEngineService service = new MoralEngineService();

    @Test
    void immoralIncrements() {
        Assertions.assertEquals(1, service.updateImmoralCount(false, 0));
    }
}
