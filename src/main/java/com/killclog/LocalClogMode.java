package com.killclog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LocalClogMode
{
    OFF("Off"),
    LOCAL_ONLY("Local browsing only"),
    ALL("All lookups");

    private final String name;

    @Override
    public String toString()
    {
        return name;
    }
}
