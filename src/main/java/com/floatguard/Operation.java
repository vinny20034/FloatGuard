package com.floatguard;

import com.floatguard.exception.InsufficientFloatException;

import java.time.Instant;


public interface Operation {

    void applyTo(Agent agent) throws InsufficientFloatException;

    String getType();

    String getProvider();

    double getAmount();

    Instant getTimestamp();

    double cashChange();

    double eFloatChange();
}
