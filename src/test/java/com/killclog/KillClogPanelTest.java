package com.killclog;

import org.junit.Test;
import static org.junit.Assert.*;

public class KillClogPanelTest
{
    @Test
    public void testClogTierBelowBronze()
    {
        assertNull(KillClogPanel.getClogTierName(0, 1700));
        assertNull(KillClogPanel.getClogTierName(99, 1700));
    }

    @Test
    public void testClogTierBronze()
    {
        assertEquals("bronze", KillClogPanel.getClogTierName(100, 1700));
        assertEquals("bronze", KillClogPanel.getClogTierName(200, 1700));
        assertEquals("bronze", KillClogPanel.getClogTierName(299, 1700));
    }

    @Test
    public void testClogTierIron()
    {
        assertEquals("iron", KillClogPanel.getClogTierName(300, 1700));
        assertEquals("iron", KillClogPanel.getClogTierName(499, 1700));
    }

    @Test
    public void testClogTierSteel()
    {
        assertEquals("steel", KillClogPanel.getClogTierName(500, 1700));
        assertEquals("steel", KillClogPanel.getClogTierName(699, 1700));
    }

    @Test
    public void testClogTierBlack()
    {
        assertEquals("black", KillClogPanel.getClogTierName(700, 1700));
        assertEquals("black", KillClogPanel.getClogTierName(899, 1700));
    }

    @Test
    public void testClogTierMithril()
    {
        assertEquals("mithril", KillClogPanel.getClogTierName(900, 1700));
        assertEquals("mithril", KillClogPanel.getClogTierName(999, 1700));
    }

    @Test
    public void testClogTierAdamant()
    {
        assertEquals("adamant", KillClogPanel.getClogTierName(1000, 1700));
        assertEquals("adamant", KillClogPanel.getClogTierName(1099, 1700));
    }

    @Test
    public void testClogTierRune()
    {
        assertEquals("rune", KillClogPanel.getClogTierName(1100, 1700));
        assertEquals("rune", KillClogPanel.getClogTierName(1199, 1700));
    }

    @Test
    public void testClogTierDragon()
    {
        assertEquals("dragon", KillClogPanel.getClogTierName(1200, 1700));
        assertEquals("dragon", KillClogPanel.getClogTierName(1299, 1700));
    }

    @Test
    public void testClogTierGilded()
    {
        // 1700 total slots: 90% = 1530, rounded down to nearest 25 = 1525
        assertEquals("gilded", KillClogPanel.getClogTierName(1525, 1700));
        assertEquals("gilded", KillClogPanel.getClogTierName(1600, 1700));
        assertEquals("gilded", KillClogPanel.getClogTierName(1700, 1700));
        // Just below gilded threshold
        assertEquals("dragon", KillClogPanel.getClogTierName(1524, 1700));
    }

    @Test
    public void testClogTierGildedScalesWithTotal()
    {
        // 1800 total slots: 90% = 1620, rounded down to nearest 25 = 1600
        assertEquals("gilded", KillClogPanel.getClogTierName(1600, 1800));
        assertEquals("dragon", KillClogPanel.getClogTierName(1599, 1800));

        // 2000 total slots: 90% = 1800, rounded down to nearest 25 = 1800
        assertEquals("gilded", KillClogPanel.getClogTierName(1800, 2000));
        assertEquals("dragon", KillClogPanel.getClogTierName(1799, 2000));
    }

    @Test
    public void testClogTierExactBoundaries()
    {
        // Every tier boundary exact
        assertEquals("bronze", KillClogPanel.getClogTierName(100, 1700));
        assertEquals("iron", KillClogPanel.getClogTierName(300, 1700));
        assertEquals("steel", KillClogPanel.getClogTierName(500, 1700));
        assertEquals("black", KillClogPanel.getClogTierName(700, 1700));
        assertEquals("mithril", KillClogPanel.getClogTierName(900, 1700));
        assertEquals("adamant", KillClogPanel.getClogTierName(1000, 1700));
        assertEquals("rune", KillClogPanel.getClogTierName(1100, 1700));
        assertEquals("dragon", KillClogPanel.getClogTierName(1200, 1700));
        assertEquals("gilded", KillClogPanel.getClogTierName(1525, 1700));
    }
}
