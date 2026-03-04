package com.killclog;

public enum FourTwentyMode
{
    OFF,
    GREEN_420S,    // KCs naturally at 420 turn green
    CAP_420,       // All KCs display min(kc, 420)
    ALL_420        // All KCs with kc > 0 display "420"
}
