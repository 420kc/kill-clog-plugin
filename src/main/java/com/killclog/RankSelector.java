package com.killclog;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

/** EDT-owned leaderboard views for the looked-up player. Cached service results stay untouched. */
final class RankSelector extends JPanel
{
	/** The row a board without this player shows: nothing. */
	private static final HiscoreResult NOTHING = new HiscoreResult(null, null, null, null, null, null, 0, 0, 0, -1);

	private final BiFunction<String, RankLeaderboard, CompletableFuture<HiscoreResult>> fetch;
	private final BiPredicate<String, RankLeaderboard> absent;
	private final Runnable changed;
	private final Set<RankLeaderboard> open = EnumSet.noneOf(RankLeaderboard.class);
	private final Map<RankLeaderboard, JButton> buttons = new EnumMap<>(RankLeaderboard.class);
	private final Map<HiscoreResult, HiscoreResult> views = new IdentityHashMap<>();
	private final Map<HiscoreResult, String> notices = new IdentityHashMap<>();
	private HiscoreResult blue;
	private String blueName;
	private RankLeaderboard selected;
	private boolean enabled;
	private int version;
	private int probes;

	RankSelector(BiFunction<String, RankLeaderboard, CompletableFuture<HiscoreResult>> fetch,
		BiPredicate<String, RankLeaderboard> absent, Runnable changed)
	{
		super(new FlowLayout(FlowLayout.CENTER, 4, 3));
		this.fetch = fetch;
		this.absent = absent;
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

	static ImageIcon icon(BufferedImage image, float alpha)
	{
		BufferedImage dimmed = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dimmed.createGraphics();
		g.setComposite(AlphaComposite.SrcOver.derive(alpha));
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return new ImageIcon(dimmed);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		// Continue the scrollbar track below the scroll pane without moving the buttons.
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRect(getWidth() - MinimalScrollBarUI.WIDTH, 0, MinimalScrollBarUI.WIDTH, getHeight());
	}

	void update(boolean enabled, String blueName, HiscoreResult blue)
	{
		if (this.enabled == enabled && this.blue == blue && Objects.equals(this.blueName, blueName))
		{
			return;
		}
		this.enabled = enabled;
		this.blue = blue;
		this.blueName = blueName;
		if (!enabled) selected = null;
		setVisible(enabled);
		probe();
		reload();
	}

	void reset()
	{
		version++;
		probes++;
		selected = null;
		blue = null;
		open.clear();
		views.clear();
		notices.clear();
		updateButtons();
	}

	/** A board gets its button once the player has a row on it, or Jagex didn't answer and a click can retry. */
	private void probe()
	{
		int probe = ++probes;
		open.clear();
		if (!enabled || blue == null) return;
		String name = blueName;
		open.add(RankLeaderboard.nativeOf(blue));
		for (RankLeaderboard table : RankLeaderboard.values())
		{
			fetch.apply(name, table).whenComplete((row, error) -> SwingUtilities.invokeLater(() ->
			{
				if (probe == probes && (row != null || !absent.test(name, table)) && open.add(table)) updateButtons();
			}));
		}
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

	/** Why a displayed board shows nothing, or null when it shows a row. */
	String blankNotice(HiscoreResult view)
	{
		for (Map.Entry<HiscoreResult, HiscoreResult> entry : views.entrySet())
		{
			if (entry.getValue() == view && view.getTotalLevel() == 0) return notices.get(entry.getKey());
		}
		return null;
	}

	private void reload()
	{
		int requestVersion = ++version;
		views.clear();
		notices.clear();
		if (enabled && blue != null)
		{
			load(blueName, blue, requestVersion);
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
		CompletableFuture<HiscoreResult> fetching = fetch.apply(name, table);
		if (fetching.isDone() && !fetching.isCompletedExceptionally())
		{
			settle(name, base, table, fetching.join());
			return;
		}
		views.put(base, base.withRow(NOTHING));
		notices.put(base, "Loading...");
		fetching.whenComplete((row, error) -> SwingUtilities.invokeLater(() ->
		{
			if (version != requestVersion) return;
			settle(name, base, table, error == null ? row : null);
			updateButtons();
			changed.run();
		}));
	}

	/** A board without this player's row shows nothing, never another board's stats. */
	private void settle(String name, HiscoreResult base, RankLeaderboard table, HiscoreResult row)
	{
		HiscoreResult view = base.withRow(row != null ? row : NOTHING);
		views.put(base, view);
		notices.put(base, row == null ? absent.test(name, table) ? "Not on this leaderboard" : "Unavailable; click to retry"
			: view.isFrozen() ? "Stats frozen on this leaderboard" : "");
	}

	private void updateButtons()
	{
		for (Map.Entry<RankLeaderboard, JButton> entry : buttons.entrySet())
		{
			boolean active = entry.getKey() == active();
			JButton button = entry.getValue();
			button.setVisible(active || open.contains(entry.getKey()));
			button.setEnabled(enabled && blue != null);
			button.setSelected(active);
			button.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0,
				active ? ColorScheme.BRAND_ORANGE : new Color(0, 0, 0, 0)));
			String hint = entry.getKey().label + " Hiscores";
			if (active)
			{
				hint += notice(blueName, blue);
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
