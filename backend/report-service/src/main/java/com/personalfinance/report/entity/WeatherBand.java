package com.personalfinance.report.entity;

/**
 * The three states of the F4 composite. {@link #wireName()} is what crosses the
 * event and HTTP boundary and what the frontend appends to its root state class,
 * so it is deliberately decoupled from the persisted enum name.
 */
public enum WeatherBand {

    CLEAR,
    GATHERING,
    STORM;

    public String wireName() {
        return name().toLowerCase();
    }
}
