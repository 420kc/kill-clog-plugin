package com.killclog;

import java.awt.Color;

public enum AccountType
{
    REGULAR("Regular", Color.WHITE),
    IRONMAN("Ironman", new Color(160, 160, 160)),
    HARDCORE_IRONMAN("Hardcore Ironman", new Color(220, 50, 50)),
    ULTIMATE_IRONMAN("Ultimate Ironman", Color.WHITE),
    DE_IRONED("De-Ironed", new Color(130, 180, 255));

    private final String label;
    private final Color color;

    AccountType(String label, Color color)
    {
        this.label = label;
        this.color = color;
    }

    public String getLabel()
    {
        return label;
    }

    public Color getColor()
    {
        return color;
    }
}
