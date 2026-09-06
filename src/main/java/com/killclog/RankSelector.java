package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

/** EDT-owned, rank-only views for both players. Cached service results stay untouched. */
final class RankSelector extends JPanel
{
	private final BiFunction<String, RankLeaderboard, CompletableFuture<HiscoreResult>> fetch;
	private final Runnable changed;
	private final Map<RankLeaderboard, JButton> buttons = new EnumMap<>(RankLeaderboard.class);
	private final Map<HiscoreResult, HiscoreResult> views = new IdentityHashMap<>();
	private final Map<HiscoreResult, String> notices = new IdentityHashMap<>();
	private HiscoreResult blue;
	private HiscoreResult red;
	private String blueName;
	private String redName;
	private RankLeaderboard selected;
	private boolean enabled;
	private int version;

	RankSelector(BiFunction<String, RankLeaderboard, CompletableFuture<HiscoreResult>> fetch, Runnable changed)
	{
		super(new FlowLayout(FlowLayout.CENTER, 4, 3));
		this.fetch = fetch;
		this.changed = changed;
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (RankLeaderboard table : RankLeaderboard.values())
		{
			BufferedImage image = ImageUtil.loadImageResource(HiscorePanel.class, table.icon);
			JButton button = new JButton(icon(image, 0.45f));
			button.setRolloverIcon(icon(image, 0.8f));
			button.setSelectedIcon(icon(image, 1f));
			button.setDisabledIcon(icon(image, 0.2f));
			button.setContentAreaFilled(false);
			button.setFocusPainted(false);
			button.setPreferredSize(new Dimension(26, 26));
			button.getAccessibleContext().setAccessibleName(table.label + " Hiscores");
			button.addActionListener(event -> select(table));
			buttons.put(table, button);
			add(button);
		}
		updateButtons();
		setVisible(false);
	}

	private static ImageIcon icon(BufferedImage image, float alpha)
	{
		BufferedImage dimmed = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dimmed.createGraphics();
		g.setComposite(AlphaComposite.SrcOver.derive(alpha));
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return new ImageIcon(dimmed);
	}

	void update(boolean enabled, String blueName, HiscoreResult blue, String redName, HiscoreResult red)
	{
		if (this.enabled == enabled && this.blue == blue && this.red == red
			&& Objects.equals(this.blueName, blueName) && Objects.equals(this.redName, redName))
		{
			return;
		}
		this.enabled = enabled;
		this.blue = blue;
		this.red = red;
		this.blueName = blueName;
		this.redName = redName;
		if (!enabled) selected = null;
		setVisible(enabled);
		reload();
	}

	void reset()
	{
		version++;
		selected = null;
		blue = null;
		red = null;
		views.clear();
		notices.clear();
		updateButtons();
	}

	void select(RankLeaderboard table)
	{
		if (!enabled || blue == null) return;
		selected = table;
		reload();
	}

	RankLeaderboard active()
	{
		return selected != null ? selected : blue != null ? RankLeaderboard.nativeOf(blue) : null;
	}

	HiscoreResult view(HiscoreResult result)
	{
		if (!enabled || result == null) return result;
		HiscoreResult view = views.get(result);
		return view != null ? view : result.withRanks(null);
	}

	private void reload()
	{
		int requestVersion = ++version;
		views.clear();
		notices.clear();
		if (enabled && blue != null)
		{
			load(blueName, blue, requestVersion);
			if (red != null && red != blue) load(redName, red, requestVersion);
		}
		updateButtons();
		changed.run();
	}

	private void load(String name, HiscoreResult base, int requestVersion)
	{
		RankLeaderboard table = active();
		if (table == RankLeaderboard.nativeOf(base))
		{
			views.put(base, base);
			return;
		}
		views.put(base, base.withRanks(null));
		notices.put(base, "Loading ranks...");
		fetch.apply(name, table).whenComplete((ranks, error) -> SwingUtilities.invokeLater(() ->
		{
			if (version != requestVersion) return;
			views.put(base, base.withRanks(error == null ? ranks : null));
			notices.put(base, error != null || ranks == null ? "Ranks unavailable; click to retry"
				: ranks.getTotalXp() < base.getTotalXp() ? "Historical ranks; current stats unchanged" : "");
			updateButtons();
			changed.run();
		}));
	}

	private void updateButtons()
	{
		for (Map.Entry<RankLeaderboard, JButton> entry : buttons.entrySet())
		{
			boolean active = entry.getKey() == active();
			JButton button = entry.getValue();
			button.setEnabled(enabled && blue != null);
			button.setSelected(active);
			button.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0,
				active ? ColorScheme.BRAND_ORANGE : new Color(0, 0, 0, 0)));
			String hint = entry.getKey().label + " Hiscores";
			if (active)
			{
				hint += notice(blueName, blue) + notice(redName, red);
			}
			button.setToolTipText(hint);
		}
	}

	private String notice(String name, HiscoreResult result)
	{
		String notice = notices.get(result);
		return notice == null || notice.isEmpty() ? "" : " - " + name + ": " + notice;
	}
}
