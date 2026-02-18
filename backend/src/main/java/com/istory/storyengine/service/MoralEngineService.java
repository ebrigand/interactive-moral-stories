package com.istory.storyengine.service;

import org.springframework.stereotype.Service;

@Service
public class MoralEngineService {

    public int updateImmoralCount(boolean moral, int current) {
        return moral ? current : current + 1;
    }

    public boolean isFailureImminent(int immoralCount) {
        return immoralCount >= 2;
    }
}
