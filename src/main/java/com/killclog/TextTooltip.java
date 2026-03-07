package com.killclog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.ui.FontManager;

/**
 * Text tooltip with inline icon placeholders via {key} tokens.
 * Use {w} to switch to white, {c} at line start to center.
 */
public class TextTooltip extends NativeTooltip
{
    private static final int ICON_GAP = 2;
    private static final Pattern ICON_TOKEN = Pattern.compile("\\{([^}]+)\\}");

    private final Map<String, BufferedImage> iconMap = new HashMap<>();

    /**
     * Register an icon for use as {key} placeholder in tooltip text.
     */
    public void putIcon(String key, BufferedImage icon)
    {
        if (icon != null)
        {
            iconMap.put(key, icon);
        }
    }

    private static class Segment
    {
        final String text;
        final BufferedImage icon;
        final Color color;

        Segment(String text)
        {
            this.text = text;
            this.icon = null;
            this.color = null;
        }

        Segment(BufferedImage icon)
        {
            this.text = null;
            this.icon = icon;
            this.color = null;
        }

        Segment(Color color)
        {
            this.text = null;
            this.icon = null;
            this.color = color;
        }
    }

    private List<Segment> parseSegments(String line)
    {
        List<Segment> segments = new ArrayList<>();
        Matcher m = ICON_TOKEN.matcher(line);
        int lastEnd = 0;

        while (m.find())
        {
            if (m.start() > lastEnd)
            {
                segments.add(new Segment(line.substring(lastEnd, m.start())));
            }

            String key = m.group(1);
            if ("w".equals(key))
            {
                segments.add(new Segment(Color.WHITE));
            }
            else if ("c".equals(key))
            {
                // Center marker — handled during painting
            }
            else
            {
                BufferedImage icon = iconMap.get(key);
                if (icon != null)
                {
                    segments.add(new Segment(icon));
                }
                else
                {
                    segments.add(new Segment(m.group(0)));
                }
            }

            lastEnd = m.end();
        }

        if (lastEnd < line.length())
        {
            segments.add(new Segment(line.substring(lastEnd)));
        }

        return segments;
    }

    private int measureSegments(List<Segment> segments, FontMetrics fm)
    {
        int width = 0;
        for (Segment seg : segments)
        {
            if (seg.color != null)
            {
                // Color switch — no width
            }
            else if (seg.icon != null)
            {
                width += seg.icon.getWidth() + ICON_GAP;
            }
            else
            {
                width += fm.stringWidth(seg.text);
            }
        }
        return width;
    }

    @Override
    public Dimension getPreferredSize()
    {
        String text = getTipText();
        if (text == null || text.isEmpty())
        {
            return new Dimension(100, 30);
        }

        FontMetrics fm = getFontMetrics(FontManager.getRunescapeSmallFont());

        String[] lines = text.split("\n");
        int maxWidth = 0;
        for (String line : lines)
        {
            String measureLine = line.startsWith("{c}") ? line.substring(3) : line;
            List<Segment> segments = parseSegments(measureLine);
            maxWidth = Math.max(maxWidth, measureSegments(segments, fm));
        }

        int inset = getInset();
        int width = maxWidth + getHeaderRightPad() + inset * 2;
        int height = lines.length * LINE_HEIGHT + inset * 2;

        return new Dimension(width, height);
    }

    @Override
    protected void paintContent(Graphics2D g2, int w, int h)
    {
        g2.setFont(FontManager.getRunescapeSmallFont());
        g2.setColor(OSRS_ORANGE);

        String text = getTipText();
        if (text == null)
        {
            return;
        }

        FontMetrics fm = g2.getFontMetrics();
        int inset = getInset();
        String[] lines = text.split("\n");
        int y = inset + fm.getAscent();

        for (String line : lines)
        {
            boolean centered = line.startsWith("{c}");
            String renderLine = centered ? line.substring(3) : line;
            g2.setColor(OSRS_ORANGE);
            List<Segment> segments = parseSegments(renderLine);
            int lineWidth = measureSegments(segments, fm);
            int x = centered ? (w - lineWidth) / 2 : inset;

            for (Segment seg : segments)
            {
                if (seg.color != null)
                {
                    g2.setColor(seg.color);
                }
                else if (seg.icon != null)
                {
                    int iconY = y - fm.getAscent() + (LINE_HEIGHT - seg.icon.getHeight()) / 2;
                    g2.drawImage(seg.icon, x, iconY, null);
                    x += seg.icon.getWidth() + ICON_GAP;
                }
                else
                {
                    g2.drawString(seg.text, x, y);
                    x += fm.stringWidth(seg.text);
                }
            }

            y += LINE_HEIGHT;
        }
    }

}
