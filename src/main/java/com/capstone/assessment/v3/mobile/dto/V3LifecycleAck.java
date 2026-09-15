package com.capstone.assessment.v3.mobile.dto;

import java.time.Instant;

public record V3LifecycleAck(String resultUuid,String resultStatus,long revision,int scoreVersion,
        String replacementResultUuid,String disposition,Instant acknowledgedAt) {
    public V3LifecycleAck replay(){return new V3LifecycleAck(resultUuid,resultStatus,revision,scoreVersion,replacementResultUuid,"replayed",acknowledgedAt);}
}
