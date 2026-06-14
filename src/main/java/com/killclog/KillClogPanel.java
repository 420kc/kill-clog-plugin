package com.killclog;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

@Slf4j
public class KillClogPanel extends PluginPanel
	implements LookupSession.Listener, ComparisonController.Listener,
	ComparisonController.CellRenderTarget
{
	private static final Color TEXT_DIM = new Color(160, 160, 160);
	private static final Color NOT_FOUND = new Color(0x81, 0x09, 0x09);
	static final Color KC_COLOR = new Color(215, 215, 215);
	private static final Color HAMBURGER_COLOR = new Color(70, 70, 70);
	private static final Color HAMBURGER_HOVER_COLOR = new Color(96, 96, 96);
	private static final String SYNC_NOTICE = "Open Collection Log and click";

	// Search-glass hit region (bar coords): the 24px glyph sits 8px in, so x <= 32 is the icon.
	private static final int SEARCH_ICON_HIT_X = 32;
	private static final int SYNC_ICON_SIZE = 12;
	private static final int ITEM_NAME_RESOLVE_BATCH_SIZE = 48;

	/** Info bar text color - only applies when highlighter is active AND clog data exists. */
	@Override
	public Color getInfoColor()
	{
		return config.completionistHighlighter() && lookupSession.getClogResult() != null
			? config.infoBarColor() : KC_COLOR;
	}

	private BufferedImage getSyncIcon()
	{
		if (syncNoticeIcon == null)
		{
			BufferedImage raw = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
			syncNoticeIcon = ImageUtil.resizeImage(raw, SYNC_ICON_SIZE, SYNC_ICON_SIZE);
		}
		return syncNoticeIcon;
	}

	private final HiscoreService hiscoreService;
	private final ClogService clogService;
	private final RuneProfileService runeProfileService;
	private final KillClogConfig config;
	private final ConfigManager configManager;
	private final SpriteManager spriteManager;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final Client client;
	private final AccountBadgeResolver accountBadges;
	private final TooltipDataBuilder tooltipDataBuilder;
	private ProgressHighlighter highlighter;
	private JPanel infoRow;

	private final JLabel searchStatus = new JLabel(" ");
	private final IconTextField searchBar = new IconTextField();
	private final Map<CombatAchievementReward, BufferedImage> caRewardCache = new EnumMap<>(CombatAchievementReward.class);
	private final Set<CombatAchievementReward> caRewardLoading = EnumSet.noneOf(CombatAchievementReward.class);
	private final Set<ClogResult> itemNameResolutions = Collections.synchronizedSet(
		Collections.newSetFromMap(new IdentityHashMap<>()));
	private final JLabel playerName = new JLabel(" ")
	{
		@Override
		public JToolTip createToolTip()
		{
			if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
			{
				return buildComparePlayerSummary(this);
			}
			// Single player: standard player summary
			SummaryTooltip tip = new SummaryTooltip();
			tip.setComponent(this);
			String name = playerName.getText().trim();
			AccountType type = displayAccountType(lookupSession.getHiscoreResult(),
				lookupSession.getClogResult(), lookupSession.getCurrentLookupRsn());
			tip.setData(
				name.isEmpty() ? "Player" : name,
				lookupSession.getHiscoreResult() != null ? lookupSession.getHiscoreResult().getOverallRank() : -1,
				getCapeImage(),
				accountBadge(type),
				accountLabel(type),
				LookupQueries.getPrestige(lookupSession.getHiscoreResult())
			);
			if (lookupSession.getClogResult() != null)
			{
				List<Integer> allPets = lookupSession.getClogResult().getCategoryItems().get("all_pets");
				Set<Integer> obtainedPets = LookupQueries.getObtainedPetIds(lookupSession.getClogResult());
				tip.setPets(allPets, obtainedPets, itemManager);
			}
			return tip;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			paintUnderline(this, g, true);
		}
	};
	private final JLabel clogInfoLabel = new JLabel()
	{
		@Override
		public JToolTip createToolTip()
		{
			// Comparison mode: this label is the red RSN.
			if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
			{
				return buildComparePlayerSummary(this);
			}

			// Single player: standard clog summary
			ClogSummaryTooltip tip = new ClogSummaryTooltip();
			tip.setComponent(this);
			if (lookupSession.getClogResult() != null)
			{
				int[] totals = ClogHelper.sumClogTotals(lookupSession.getClogResult());
				Map<String, BufferedImage> icons = new LinkedHashMap<>();
				for (Map.Entry<String, ImageIcon> entry : clogTierIcons.entrySet())
				{
					icons.put(entry.getKey(), ClogHelper.iconToImage(entry.getValue()));
				}
				tip.setTierData(totals[0], totals[1], icons);
				if (lookupSession.getHiscoreResult() != null)
				{
					int clogRank = lookupSession.getHiscoreResult().getActivityRank("Collections Logged");
					tip.setRank(clogRank);
				}
				boolean stale = LookupQueries.isSyncStale(lookupSession.getClogLastChanged(), 90);
				String sync = LookupQueries.syncLine(lookupSession.getClogLastChanged(), stale);
				if (sync != null) tip.setSyncData(sync, stale);
				tip.setRecentItems(LookupQueries.getRecentItems(lookupSession.getClogResult(), 4), itemManager);
			}
			else
			{
				boolean isSelf = localRsn != null && rsn != null
					&& localRsn.equalsIgnoreCase(rsn);
				if (isSelf)
				{
					tip.setNotice(SYNC_NOTICE, getSyncIcon());
				}
				else
				{
					tip.setNotice(lookupSession.getHiscoreResult() != null
						? noClogNotice(rsn) : "Nothing to see here! (Search for a player)");
				}
			}
			return tip;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			paintUnderline(this, g, false);
		}
	};
	private final JLabel clogNotice = new JLabel();
	private BufferedImage maxCapeTip;
	private BufferedImage infernalCapeTip;
	private BufferedImage infernalMaxCapeTip;
	private BufferedImage riftsClosedIcon;
	private final Map<String, ImageIcon> clogTierIcons = new LinkedHashMap<>();

	private JLabel compareLabel;
	private ImageIcon compareIconDim;
	private ImageIcon compareIconBright;
	private ImageIcon searchIconDim;
	private ImageIcon searchIconBright;
	private boolean compareEntryMode;
	private boolean compareIconHover;
	private boolean searchRowHover;
	private JTextField searchTextField;
	private JPanel searchRow;
	private JPanel clogTotalsBar;
	private JLabel blueClogTotal;
	private JLabel redClogTotal;
	private BufferedImage syncNoticeIcon;
	// Activities tray
	private JLabel combatCell;
	private JLabel totalLvlCell;
	private ActivitiesTray activitiesTray;

	// Current lookup state lives on lookupSession; rsn here is a separate
	// display-name field set by fetchRsn for the playerName label.
	private String rsn;
	private String localRsn;
	private AccountType localAccountType;


	private final TooltipController tooltipController;
	private final LookupSession lookupSession;
	private final ComparisonController comparison;
	private final Cells cells;

	// Comparison mode widgets (state fields all live on the controller)

	// 420 mode - unlocked when the 420 KC plugin is loaded
	private NameAutocompleter nameAutocompleter;
	private FourTwentyMode fourTwentyMode = FourTwentyMode.OFF;
	private boolean has420Plugin;

	// No-data copy stays quiet: another player simply has no synced collection log.
	// No provider names, no "missing data" framing - just a calm statement.
	private static String noClogNotice(String rsn)
	{
		return rsn != null && !rsn.isEmpty()
			? rsn + " hasn't synced a collection log"
			: "No collection log synced";
	}

	@Inject
	public KillClogPanel(HiscoreService hiscoreService, ClogService clogService,
		RuneProfileService runeProfileService,
		KillClogConfig config, ConfigManager configManager,
		SpriteManager spriteManager,
		ItemManager itemManager, ClientThread clientThread,
		SkillIconManager skillIconManager, Client client)
	{
		super(true); // wrap in JScrollPane
		this.hiscoreService = hiscoreService;
		this.clogService = clogService;
		this.runeProfileService = runeProfileService;
		this.config = config;
		this.configManager = configManager;
		this.spriteManager = spriteManager;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.client = client;
		this.accountBadges = new AccountBadgeResolver(client);
		this.tooltipDataBuilder = new TooltipDataBuilder(itemManager);
		this.tooltipController = new TooltipController(config);
		this.lookupSession = new LookupSession(hiscoreService, clogService, runeProfileService, config, null, this);
		this.comparison = new ComparisonController(hiscoreService, clogService, runeProfileService,
			lookupSession, itemManager, config, tooltipController, tooltipDataBuilder, this);
		this.comparison.setRenderTarget(this);
		this.cells = new Cells(spriteManager, itemManager, tooltipController, comparison, tooltipDataBuilder, lookupSession, clogService);
		this.comparison.setCells(this.cells);
		this.cells.setSinglePlayerTooltipBuilder(new Cells.SinglePlayerTooltipBuilder()
		{
			@Override
			public JToolTip build(JLabel owner, TooltipData data, int gridCols, String name)
			{
				return makeSpriteTooltip(owner, data, gridCols, name);
			}

			@Override
			public JToolTip build(JLabel owner, TooltipData data, int gridCols, String name, boolean compact)
			{
				return makeSpriteTooltip(owner, data, gridCols, name, compact);
			}
		});

		reloadTooltipSprites();
		SkillsTooltip.loadIcons(skillIconManager);


		setBorder(new EmptyBorder(10, 10, 0, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 0;
		c.insets = new Insets(0, 0, 5, 0);
		add(buildSearchPanel(), c);

		// Activities tray with slide animation
		c.gridy++;
		c.insets = new Insets(0, 0, 0, 0);
		activitiesTray = new ActivitiesTray(buildActivitiesGrid(), config.activitiesExpanded(),
			expanded -> configManager.setConfiguration("killclog", "activitiesExpanded", expanded));
		add(activitiesTray.getClip(), c);

		// Separator between activities and boss grid (also the click target to toggle the tray)
		c.gridy++;
		add(activitiesTray.getSeparator(), c);

		c.gridy++;
		add(cells.buildBossGrid(), c);
		// 420 mode easter egg: secret cycle on Thermonuclear Smoke Devil click.
		JLabel thermoLabel = cells.getBossLabel(HiscoreSkill.THERMONUCLEAR_SMOKE_DEVIL);
		if (thermoLabel != null)
		{
			thermoLabel.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (has420Plugin && !comparison.isComparisonMode())
					{
						cycleFourTwentyMode();
					}
				}
			});
		}

		// Collection log sync notice below boss grid
		c.gridy++;
		c.insets = new Insets(5, 0, 0, 0);
		clogNotice.setFont(FontManager.getRunescapeSmallFont());
		clogNotice.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		clogNotice.setHorizontalAlignment(JLabel.CENTER);
		clogNotice.setText(" ");
		clogNotice.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		add(clogNotice, c);

		// Compare entry controls live in the search row.

		JScrollPane sp = getScrollPane();
		if (sp != null)
		{
			sp.setBorder(null);
			sp.setViewportBorder(null);
			sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
			sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
			sp.getVerticalScrollBar().setUI(new MinimalScrollBarUI());
			sp.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
			sp.getVerticalScrollBar().setUnitIncrement(16);
		}

		highlighter = new ProgressHighlighter(
			cells.getBossLabels(), cells.getActivityLabels(), cells.getClueTierLabels(),
			PanelData.NAME_OVERRIDES, PanelData.CLUE_CATEGORIES, config);
	}

	// Panel construction.

	private JPanel buildSearchPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setBorder(null);

		// Search status lives above the search bar, never in the summary bar.
		searchStatus.setFont(FontManager.getRunescapeSmallFont());
		searchStatus.setForeground(TEXT_DIM);
		searchStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchStatus.setBorder(new EmptyBorder(0, 4, 2, 0));
		searchStatus.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		panel.add(searchStatus);

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setPreferredSize(new Dimension(0, 30));
		searchBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchBar.addActionListener(e ->
		{
			if (compareEntryMode)
			{
				doCompareLookupFromSearchBar();
			}
			else
			{
				doLookup();
			}
		});

		ClogHelper.styleSearchBar(searchBar);
		for (Component c : searchBar.getComponents())
		{
			if (c instanceof FlatTextField)
			{
				JTextField tf =
					((FlatTextField) c).getTextField();
				searchTextField = tf;
				tf.setFont(FontManager.getRunescapeFont());
				tf.setForeground(Color.WHITE);
				tf.setCaretColor(Color.WHITE);
				tf.putClientProperty(
					RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			}
			else if (c instanceof Container)
			{
				ClogHelper.styleSearchBar((Container) c);
			}
		}
		// Compare icon sits beside the search field and shares its active background.
		compareLabel = new JLabel();
		compareIconDim = new ImageIcon(ClogHelper.makeCompareIcon(
			ComparisonController.COMPARE_BLUE, ComparisonController.COMPARE_RED, 0.55f));
		compareIconBright = new ImageIcon(ClogHelper.makeCompareIcon(
			ComparisonController.COMPARE_BLUE, ComparisonController.COMPARE_RED, 1.0f));
		searchIconDim = new ImageIcon(ClogHelper.makeSearchIcon(TEXT_DIM, 0.70f));
		searchIconBright = new ImageIcon(ClogHelper.makeSearchIcon(TEXT_DIM, 1.0f));
		compareLabel.setIcon(compareIconDim);
		compareLabel.setHorizontalAlignment(JLabel.CENTER);
		compareLabel.setVerticalAlignment(JLabel.CENTER);
		compareLabel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		compareLabel.setOpaque(true);
		// Right inset mirrors the search glass's left inset: the 24px glass glyph
		// sits 8px in from the bar's left edge, so the compare glyph sits 8px in from the right.
		compareLabel.setBorder(new EmptyBorder(0, 0, 0, 7));
		compareLabel.setVisible(false);
		compareLabel.setPreferredSize(new Dimension(22, 30));

		// searchRow: [searchBar (fill)] [compareLabel (fixed right, flush)]
		searchRow = new JPanel(null)
		{
			@Override
			public void doLayout()
			{
				int w = getWidth(), h = getHeight();
				boolean showCompare = compareLabel.isVisible();
				int compareW = showCompare ? 22 : 0;
				searchBar.setBounds(0, 0, w - compareW, h);
				compareLabel.setBounds(w - compareW, 0, compareW, h);
			}
		};
		searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchRow.setPreferredSize(new Dimension(0, 30));
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		searchRow.setOpaque(false);
		searchRow.add(compareLabel);
		searchRow.add(searchBar);
		MouseAdapter searchRowHoverSync = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setSearchRowHover(true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (!isMouseInsideSearchRow())
					{
						setSearchRowHover(false);
					}
				});
			}
		};
		installMouseListenerDeep(searchRow, searchRowHoverSync);

		// Double-click the magnifying-glass icon -> look up self. searchBar is an
		// IconTextField composite, so clicks land on its child text field/icon, never
		// the panel itself; install on the whole tree and test the click against the
		// bar's icon region in shared coordinates.
		installMouseListenerDeep(searchBar, new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2)
				{
					return;
				}
				Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), searchBar);
				if (p.x <= SEARCH_ICON_HIT_X)
				{
					lookupSelfFromSearchIcon();
				}
			}
		});

		// compareLabel: always-visible dimmed icon, brightens on hover, click toggles compare entry
		compareLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggleCompareEntry();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				compareIconHover = true;
				setSearchRowHover(true);
				updateCompareIcon();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				compareIconHover = false;
				updateCompareIcon();
				SwingUtilities.invokeLater(() ->
				{
					if (!isMouseInsideSearchRow())
					{
						setSearchRowHover(false);
					}
				});
			}
		});

		// Comparison clog totals sit above the search input. Search status stays separate.
		// Blue player left, red player right, each with a tier icon.
		clogTotalsBar = new JPanel(new GridBagLayout())
		{
			@Override
			public JToolTip createToolTip()
			{
				if (!comparison.isComparisonMode())
				{
					return super.createToolTip();
				}
				return buildCompareClogSummary(this);
			}
		};
		clogTotalsBar.setOpaque(false);
		clogTotalsBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		clogTotalsBar.setVisible(false);
		blueClogTotal = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				if (!comparison.isComparisonMode())
				{
					return super.createToolTip();
				}
				return buildCompareClogSummary(clogTotalsBar);
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				paintUnderline(this, g, true);
			}
		};
		blueClogTotal.setFont(FontManager.getRunescapeSmallFont());
		blueClogTotal.setForeground(ComparisonController.COMPARE_BLUE);
		blueClogTotal.setHorizontalAlignment(JLabel.LEFT);
		blueClogTotal.setIconTextGap(3);
		blueClogTotal.setBorder(new EmptyBorder(0, 0, 2, 0));
		blueClogTotal.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		redClogTotal = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				if (!comparison.isComparisonMode())
				{
					return super.createToolTip();
				}
				return buildCompareClogSummary(clogTotalsBar);
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				paintUnderline(this, g, false);
			}
		};
		redClogTotal.setFont(FontManager.getRunescapeSmallFont());
		redClogTotal.setForeground(ComparisonController.COMPARE_RED);
		redClogTotal.setHorizontalAlignment(JLabel.RIGHT);
		redClogTotal.setIconTextGap(3);
		redClogTotal.setBorder(new EmptyBorder(0, 0, 2, 0));
		redClogTotal.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		GridBagConstraints ctbc = new GridBagConstraints();
		ctbc.gridy = 0;
		ctbc.fill = GridBagConstraints.HORIZONTAL;
		ctbc.gridx = 0;
		ctbc.weightx = 1.0;
		ctbc.anchor = GridBagConstraints.WEST;
		clogTotalsBar.add(blueClogTotal, ctbc);
		ctbc.gridx = 1;
		ctbc.anchor = GridBagConstraints.EAST;
		clogTotalsBar.add(redClogTotal, ctbc);
		clogTotalsBar.setToolTipText(" ");
		blueClogTotal.setToolTipText(" ");
		redClogTotal.setToolTipText(" ");
		MouseAdapter clogTotalsClickHandler = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (config.tooltipMode() == TooltipMode.CLICK && clogTotalsBar.getToolTipText() != null)
				{
					tooltipController.showClickTooltip(clogTotalsBar, clogTotalsBar);
				}
			}
		};
		clogTotalsBar.addMouseListener(clogTotalsClickHandler);
		blueClogTotal.addMouseListener(clogTotalsClickHandler);
		redClogTotal.addMouseListener(clogTotalsClickHandler);
		installUnderlineHover(blueClogTotal);
		installUnderlineHover(redClogTotal);
		panel.add(clogTotalsBar);

		panel.add(searchRow);
		panel.add(Box.createVerticalStrut(4));

		// Info bar: [badge+name LEFT] [hamburger CENTER] [tierIcon+clogCount RIGHT]
		infoRow = new JPanel(null)
		{
			@Override
			public void doLayout()
			{
				if (getComponentCount() < 3)
				{
					return;
				}

				int width = getWidth();
				int height = getHeight();
				int gap = 4;
				Component left = getComponent(0);
				Component center = getComponent(1);
				Component right = getComponent(2);
				Dimension centerSize = center.getPreferredSize();
				int centerW = centerSize.width;
				int centerH = Math.min(height, centerSize.height);
				int centerX = Math.max(0, (width - centerW) / 2);
				int centerY = Math.max(0, (height - centerH) / 2);
				int rightX = Math.min(width, centerX + centerW + gap);

				left.setBounds(0, 0, Math.max(0, centerX - gap), height);
				center.setBounds(centerX, centerY, centerW, centerH);
				right.setBounds(rightX, 0, Math.max(0, width - rightX), height);
			}
		};
		infoRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		infoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		infoRow.setPreferredSize(new Dimension(0, 18));

		configureBarLabel(playerName, JLabel.LEFT);
		playerName.setBorder(new EmptyBorder(0, 4, 0, 0));
		playerName.setMinimumSize(new Dimension(0, 0));

		configureBarLabel(clogInfoLabel, JLabel.RIGHT);
		clogInfoLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
		clogInfoLabel.setMinimumSize(new Dimension(0, 0));

		// Info bar labels use click-to-show in click mode (same as boss cells).
		// No special hover override - avoids ToolTipManager showImmediately cascade.
		MouseAdapter infoBarClickHandler = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (config.tooltipMode() == TooltipMode.CLICK)
				{
					JLabel label = (JLabel) e.getSource();
					if (label.getToolTipText() != null)
					{
						tooltipController.showClickTooltip(label, infoRow);
					}
				}
			}
		};
		playerName.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				// In comparison mode, show the comparison tooltip instead of exiting
				if (comparison.isComparisonMode())
				{
					if (config.tooltipMode() == TooltipMode.CLICK && playerName.getToolTipText() != null)
					{
						tooltipController.showClickTooltip(playerName, infoRow);
					}
					return;
				}
				infoBarClickHandler.mousePressed(e);
			}
		});
		clogInfoLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				// In comparison mode, show the comparison tooltip instead of swapping
				if (comparison.isComparisonMode())
				{
					if (config.tooltipMode() == TooltipMode.CLICK && clogInfoLabel.getToolTipText() != null)
					{
						tooltipController.showClickTooltip(clogInfoLabel, infoRow);
					}
					return;
				}
				infoBarClickHandler.mousePressed(e);
			}
		});

		for (JLabel barLabel : new JLabel[]{playerName, clogInfoLabel})
		{
			installUnderlineHover(barLabel);
		}

		JLabel trayToggle = new JLabel();
		ImageIcon hamburgerIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(HAMBURGER_COLOR));
		ImageIcon hamburgerHoverIcon = new ImageIcon(ClogHelper.makeHamburgerIcon(HAMBURGER_HOVER_COLOR));
		trayToggle.setIcon(hamburgerIcon);
		trayToggle.setHorizontalAlignment(JLabel.CENTER);
		trayToggle.setVerticalAlignment(JLabel.CENTER);
		trayToggle.setPreferredSize(new Dimension(18, 18));
		trayToggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				activitiesTray.toggle();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				trayToggle.setIcon(hamburgerHoverIcon);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				trayToggle.setIcon(hamburgerIcon);
			}
		});

		infoRow.add(playerName);
		infoRow.add(trayToggle);
		infoRow.add(clogInfoLabel);

		panel.add(infoRow);

		loadRuntimeIcons();
		return panel;
	}

	private void installMouseListenerDeep(Component component, MouseAdapter adapter)
	{
		component.addMouseListener(adapter);
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				installMouseListenerDeep(child, adapter);
			}
		}
	}

	private boolean isMouseInsideSearchRow()
	{
		return searchRow != null && searchRow.getMousePosition(true) != null;
	}

	private void setSearchRowHover(boolean hover)
	{
		searchRowHover = hover;
		Color background = hover ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR;
		searchBar.setBackground(background);
		if (compareLabel != null)
		{
			compareLabel.setBackground(background);
		}
	}

	private void updateCompareIcon()
	{
		if (compareLabel == null)
		{
			return;
		}

		boolean active = compareIconHover || compareEntryMode || comparison.isComparisonMode();
		boolean comparing = compareEntryMode || comparison.isComparisonMode();
		compareLabel.setIcon(comparing
			? (compareIconHover ? searchIconBright : searchIconDim)
			: (active ? compareIconBright : compareIconDim));
		compareLabel.setBackground(searchRowHover
			? ColorScheme.DARK_GRAY_HOVER_COLOR
			: ColorScheme.DARKER_GRAY_COLOR);
		if (!comparison.isCompareLookupInFlight())
		{
			if (comparing)
			{
				searchBar.setIcon(compareIconBright);
			}
			else
			{
				searchBar.setIcon(IconTextField.Icon.SEARCH);
			}
		}
	}

	private void configureBarLabel(JLabel label, int alignment)
	{
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setHorizontalAlignment(alignment);
		label.setVerticalAlignment(JLabel.CENTER);
		label.setVerticalTextPosition(JLabel.CENTER);
		label.setIconTextGap(3);
		label.putClientProperty(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	private void installUnderlineHover(JLabel barLabel)
	{
		barLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (barLabel.getToolTipText() != null
					|| comparison.isComparisonMode())
				{
					barLabel.putClientProperty("underlined", true);
					barLabel.repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				barLabel.putClientProperty("underlined", null);
				barLabel.repaint();
			}
		});
	}

	private void loadRuntimeIcons()
	{
		// Cape, CA reward, and clog tier icons via ItemManager.
		clientThread.invokeLater(() ->
		{
			// Tooltip cape icons - native aspect ratio, taller
			loadItemImage(13280, img -> maxCapeTip = img);
			loadItemImage(21295, img -> infernalCapeTip = img);
			loadItemImage(21284, img -> infernalMaxCapeTip = img);

			for (CombatAchievementReward reward : CombatAchievementReward.values())
			{
				loadItemImage(reward.itemId(), img ->
				{
					caRewardCache.put(reward, img);
					repaint();
				});
			}

			for (int i = 0; i < ClogHelper.CLOG_TIERS.length; i++)
			{
				final String tier = ClogHelper.CLOG_TIERS[i];
				final int itemId = PanelData.CLOG_TIER_ITEM_IDS[i];
				loadItemIcon(itemId, 13, 13, icon ->
					clogTierIcons.put(tier, icon));
			}

			// Clue summary tooltip icons: 1-6=tier scrolls, 7=Mimic (0=All loaded via spriteManager below)
			for (int i = 0; i < PanelData.CLUE_TIER_ITEM_IDS.length; i++)
			{
				final int idx = i + 1;
				loadItemImage(PanelData.CLUE_TIER_ITEM_IDS[i], img ->
					cells.getClueIcons()[idx] = ImageUtil.resizeImage(
						ImageUtil.resizeCanvas(img, 25, 25), 13, 13));
			}
			loadItemImage(23184, img ->
				cells.getClueIcons()[7] = ImageUtil.resizeImage(
					ImageUtil.resizeCanvas(img, 25, 25), 13, 13));
		});

		// Clue All icon via spriteManager (game sprite, not item)
		int allSpriteId = HiscoreSkill.CLUE_SCROLL_ALL.getSpriteId();
		if (allSpriteId != -1)
		{
			spriteManager.getSpriteAsync(allSpriteId, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					if (sprite != null)
					{
						cells.getClueIcons()[0] = ImageUtil.resizeImage(
							ImageUtil.resizeCanvas(sprite, 25, 25), 13, 13);
					}
				}));
		}

		int riftsSpriteId = HiscoreSkill.RIFTS_CLOSED.getSpriteId();
		if (riftsSpriteId != -1)
		{
			spriteManager.getSpriteAsync(riftsSpriteId, 0, sprite ->
				SwingUtilities.invokeLater(() ->
				{
					if (sprite != null)
					{
						riftsClosedIcon = ImageUtil.resizeImage(
							ImageUtil.resizeCanvas(sprite, 25, 25), 13, 13);
					}
				}));
		}
	}

	private void loadItemIcon(int itemId, int w, int h, Consumer<ImageIcon> setter)
	{
		BufferedImage img = itemManager.getImage(itemId, 1, false);
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() ->
				SwingUtilities.invokeLater(() ->
					setter.accept(new ImageIcon(ImageUtil.resizeImage(img, w, h)))));
		}
		else
		{
			SwingUtilities.invokeLater(() ->
				setter.accept(new ImageIcon(ImageUtil.resizeImage(img, w, h))));
		}
	}

	private void loadItemImage(int itemId, Consumer<BufferedImage> setter)
	{
		BufferedImage img = itemManager.getImage(itemId, 1, false);
		if (img instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) img).onLoaded(() ->
				SwingUtilities.invokeLater(() -> setter.accept(img)));
		}
		else
		{
			SwingUtilities.invokeLater(() -> setter.accept(img));
		}
	}

	// Activities tray.

	private JPanel buildActivitiesGrid()
	{
		JPanel grid = new JPanel();
		grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Row 0: [Combat Level] [Total Level]
		JPanel statsRow = new JPanel(new GridLayout(1, 3));
		statsRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsRow.setAlignmentX(0f);

		combatCell = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				JPanel parentCell = (JPanel) this.getParent();

				// Comparison mode: side-by-side PvM summary
				if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
				{
					ComparePvmSummaryTooltip cmp = new ComparePvmSummaryTooltip();
					cmp.setComponent(this);
					HiscoreResult blueHs = lookupSession.getHiscoreResult();
					HiscoreResult redHs = comparison.getCompareHiscoreResult();
					ClogResult blueClog = lookupSession.getClogResult();
					ClogResult redClog = comparison.getCompareClogResult();
					String blueName = rsn != null ? rsn : "--";
					String redName = comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--";

					cmp.setBlueData(blueName,
						blueHs != null ? blueHs.getCombatLevel() : 0,
						LookupQueries.sumBossKills(blueHs),
						LookupQueries.countBossesWithKc(blueHs),
						PanelData.BOSSES.length,
						LookupQueries.getMostKilledBoss(blueHs),
						LookupQueries.getMostKilledKc(blueHs));
					cmp.setRedData(redName,
						redHs.getCombatLevel(),
						LookupQueries.sumBossKills(redHs),
						LookupQueries.countBossesWithKc(redHs),
						PanelData.BOSSES.length,
						LookupQueries.getMostKilledBoss(redHs),
						LookupQueries.getMostKilledKc(redHs));

					if (blueClog != null)
					{
						cmp.setBlueCompletion(
							LookupQueries.countBossesCompleted(cells.getTooltipDataMap(), cells.getBossLabels().keySet()),
							LookupQueries.countBossesWithClog(cells.getTooltipDataMap(), cells.getBossLabels().keySet()));
					}
					if (redClog != null)
					{
						cmp.setRedCompletion(
							LookupQueries.countBossesCompleted(comparison.getCompareTooltipDataMap(), cells.getBossLabels().keySet()),
							LookupQueries.countBossesWithClog(comparison.getCompareTooltipDataMap(), cells.getBossLabels().keySet()));
					}

					CombatAchievementResult blueCa = lookupSession.getCaResult();
					if (blueCa != null)
					{
						cmp.setBlueCa(blueCa, caRewardSprite(blueCa.getReward(), 14));
					}
					CombatAchievementResult redCa = comparison.getCompareCaResult();
					if (redCa != null)
					{
						cmp.setRedCa(redCa, caRewardSprite(redCa.getReward(), 14));
					}

					cmp.setBlueMegarares(
						LookupQueries.getClogItemCount(blueClog, "chambers_of_xeric", 20997),
						LookupQueries.getClogItemCount(blueClog, "theatre_of_blood", 22486),
						LookupQueries.getClogItemCount(blueClog, "tombs_of_amascut", 27277),
						itemManager);
					cmp.setRedMegarares(
						LookupQueries.getClogItemCount(redClog, "chambers_of_xeric", 20997),
						LookupQueries.getClogItemCount(redClog, "theatre_of_blood", 22486),
						LookupQueries.getClogItemCount(redClog, "tombs_of_amascut", 27277),
						itemManager);

					if (blueHs != null) cmp.setBlueRaids(blueHs, blueClog);
					cmp.setRedRaids(redHs, redClog);

					tooltipController.keepTooltipOnHover(cmp, parentCell);
					return cmp;
				}

				// Single player: standard PvM summary
				PvmSummaryTooltip tip = new PvmSummaryTooltip();
				tip.setComponent(this);
				tip.setData(
					lookupSession.getHiscoreResult() != null ? lookupSession.getHiscoreResult().getCombatLevel() : 0,
					LookupQueries.sumBossKills(lookupSession.getHiscoreResult()),
					LookupQueries.countBossesWithKc(lookupSession.getHiscoreResult()),
					PanelData.BOSSES.length,
					LookupQueries.getMostKilledBoss(lookupSession.getHiscoreResult()),
					LookupQueries.getMostKilledKc(lookupSession.getHiscoreResult())
				);
				if (lookupSession.getClogResult() != null)
				{
					tip.setCompletion(
						LookupQueries.countBossesCompleted(cells.getTooltipDataMap(), cells.getBossLabels().keySet()),
						LookupQueries.countBossesWithClog(cells.getTooltipDataMap(), cells.getBossLabels().keySet()));
				}
				tip.setMegarares(
					LookupQueries.getClogItemCount(lookupSession.getClogResult(), "chambers_of_xeric", 20997),
					LookupQueries.getClogItemCount(lookupSession.getClogResult(), "theatre_of_blood", 22486),
					LookupQueries.getClogItemCount(lookupSession.getClogResult(), "tombs_of_amascut", 27277),
					itemManager
				);
				if (lookupSession.getHiscoreResult() != null)
				{
					tip.setRaids(lookupSession.getHiscoreResult(), lookupSession.getClogResult());
				}
				CombatAchievementResult ca = lookupSession.getCaResult();
				if (ca != null)
				{
					tip.setCombatAchievements(ca, caRewardSprite(ca.getReward(), 16));
				}
				tooltipController.keepTooltipOnHover(tip, parentCell);
				return tip;
			}
		};
		Cells.styleLabel(combatCell, "Combat");
		spriteManager.getSpriteAsync(168, 0, sprite ->
			SwingUtilities.invokeLater(() ->
			{
				if (sprite != null)
				{
					combatCell.setIcon(new ImageIcon(ImageUtil.resizeImage(
						ImageUtil.resizeCanvas(sprite, 25, 25), 20, 20)));
				}
			}));
		statsRow.add(cells.wrapInCell(combatCell));

		totalLvlCell = new JLabel()
		{
			@Override
			public JToolTip createToolTip()
			{
				if (comparison.isComparisonMode() && comparison.getCompareHiscoreResult() != null)
				{
					CompareSkillSummaryTooltip cmp = new CompareSkillSummaryTooltip();
					cmp.setComponent(this);
					String blueName = rsn != null ? rsn : "--";
					String redName = comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--";
					cmp.setData(blueName, lookupSession.getHiscoreResult(),
						redName, comparison.getCompareHiscoreResult());
					cmp.setGotr(lookupSession.getClogResult(),
						comparison.getCompareClogResult(), riftsClosedIcon);
					return cmp;
				}
				SkillsTooltip tip = new SkillsTooltip();
				tip.setComponent(this);
				HiscoreResult result = lookupSession.getHiscoreResult();
				tip.setData(result);
				int rifts = result != null
					? result.getActivityScore(PanelData.RIFTS_CLOSED_ACTIVITY) : -1;
				tip.setGotr(lookupSession.getClogResult(), riftsClosedIcon, rifts);
				return tip;
			}
		};
		Cells.styleLabel(totalLvlCell, "Total");
		try
		{
			BufferedImage img = ImageUtil.loadImageResource(HiscorePanel.class, "overall.png");
			totalLvlCell.setIcon(new ImageIcon(ImageUtil.resizeImage(
				ImageUtil.resizeCanvas(img, 25, 25), 20, 20)));
		}
		catch (Exception e)
		{
			// overall.png not available
		}
		statsRow.add(cells.wrapInCell(totalLvlCell));
		statsRow.add(cells.buildPvpSummaryCell());
		grid.add(statsRow);

		JPanel statsSep = new JPanel();
		statsSep.setBackground(ColorScheme.DARK_GRAY_COLOR);
		statsSep.setPreferredSize(new Dimension(0, 7));
		statsSep.setAlignmentX(0f);
		grid.add(statsSep);

		// Clue row 1: [3rd Age] [Clue Summary] [Gilded]
		JPanel row1 = new JPanel(new GridLayout(1, 3));
		row1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row1.setAlignmentX(0f);
		row1.add(cells.buildClueRareCell("3rd Age", PanelData.THIRD_AGE_ITEM_ID, PanelData.CLOG_THIRD_AGE, true));
		row1.add(cells.buildActivityCell(HiscoreSkill.CLUE_SCROLL_ALL));
		row1.add(cells.buildClueRareCell("Gilded", PanelData.GILDED_ITEM_ID, PanelData.CLOG_GILDED, false));
		grid.add(row1);

		// Clue row 2: Custom rare cells (casket icons)
		JPanel rareRow = new JPanel(new GridLayout(1, 3));
		rareRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		rareRow.setAlignmentX(0f);
		rareRow.add(cells.buildCustomRareCell("Hard Treasure (Rare)", 20544, PanelData.RARE_HARD, PanelData.HARD_RARE_ITEMS));
		rareRow.add(cells.buildCustomRareCell("Elite Treasure (Rare)", 20543, PanelData.RARE_ELITE, PanelData.ELITE_RARE_ITEMS));
		rareRow.add(cells.buildCustomRareCell("Master Treasure (Rare)", 19836, PanelData.RARE_MASTER, PanelData.MASTER_RARE_ITEMS));
		grid.add(rareRow);

		// Clue rows 3-4: Clue tiers
		JPanel clueRow1 = new JPanel(new GridLayout(1, 3));
		clueRow1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow1.setAlignmentX(0f);
		clueRow1.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[0], PanelData.CLUE_TIER_ITEM_IDS[0], false));
		clueRow1.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[1], PanelData.CLUE_TIER_ITEM_IDS[1], true));
		clueRow1.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[2], PanelData.CLUE_TIER_ITEM_IDS[2], true));
		grid.add(clueRow1);

		JPanel clueRow2 = new JPanel(new GridLayout(1, 3));
		clueRow2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		clueRow2.setAlignmentX(0f);
		clueRow2.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[3], PanelData.CLUE_TIER_ITEM_IDS[3], true));
		clueRow2.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[4], PanelData.CLUE_TIER_ITEM_IDS[4], false));
		clueRow2.add(cells.buildClueTierCell(PanelData.CLUE_TIERS[5], PanelData.CLUE_TIER_ITEM_IDS[5], false));
		grid.add(clueRow2);

		return grid;
	}

	// Cell factory helpers.

	/**
	 * Build a sprite tooltip for a cell - always ImgTooltip, with contextual notice when no data.
	 *
	 * @param owner     the label whose parent cell drives hover tint
	 * @param data      tooltip data, or null for notice-only display
	 * @param gridCols  min columns for the sprite grid
	 * @param name      display name shown as title when data is null
	 */
	private JToolTip makeSpriteTooltip(JLabel owner, TooltipData data, int gridCols, String name)
	{
		return makeSpriteTooltip(owner, data, gridCols, name, false);
	}

	private JToolTip makeSpriteTooltip(JLabel owner, TooltipData data, int gridCols,
										String name, boolean compact)
	{
		JPanel parentCell = (JPanel) owner.getParent();
		ImgTooltip tip = compact ? new ImgTooltip(gridCols, 15) : new ImgTooltip(gridCols);
		tip.setComponent(owner);

		if (data != null)
		{
			tip.setTitle(data.name);
			tip.setObtained(data.obtainedCount, data.totalItems);
			if (data.rankTracked && data.rank > 0)
			{
				tip.setRank(data.rank);
			}
			tip.setItems(data.totalItems, data.allItemIds, data.obtainedIds,
				data.obtainedCounts, data.itemNames, itemManager);
		}
		else
		{
			tip.setTitle(name);
			boolean isSelfNoCache = lookupSession.getHiscoreResult() != null && localRsn != null
				&& localRsn.equalsIgnoreCase(lookupSession.getCurrentLookupRsn());
			if (isSelfNoCache)
			{
				tip.setNotice(SYNC_NOTICE, getSyncIcon());
			}
			else
			{
				tip.setNotice(lookupSession.getHiscoreResult() != null
					? noClogNotice(lookupSession.getCurrentLookupRsn())
					: "Nothing to see here! (Search for a player)");
			}
		}

		tooltipController.keepTooltipOnHover(tip, parentCell);
		return tip;
	}

	// Comparison mode.

	/**
	 * Toggle the main search bar between normal lookup mode and comparison entry mode.
	 * In compare entry mode the search row stays fixed-size and the icons carry the mode.
	 * Enter triggers comparison lookup instead of a normal lookup.
	 */
	private void toggleCompareEntry()
	{
		if (compareEntryMode || comparison.isComparisonMode())
		{
			exitCompareEntry();
		}
		else
		{
			enterCompareEntry();
		}
	}

	private void enterCompareEntry()
	{
		compareEntryMode = true;
		updateCompareIcon();

		if (searchTextField != null)
		{
			searchTextField.setText("");
			searchTextField.setForeground(ComparisonController.COMPARE_RED);
			searchTextField.setCaretColor(ComparisonController.COMPARE_RED);
			searchTextField.addKeyListener(compareEntryKeyListener);
			searchTextField.requestFocusInWindow();
		}

		setSearchStatus(" ", TEXT_DIM);
		revalidate();
	}

	private void exitCompareEntry()
	{
		compareEntryMode = false;
		if (comparison.isComparisonMode())
		{
			comparison.exit();
			return; // exit() triggers onComparisonExit which handles cleanup
		}
		if (searchTextField != null)
		{
			searchTextField.removeKeyListener(compareEntryKeyListener);
			searchTextField.setForeground(Color.WHITE);
			searchTextField.setCaretColor(Color.WHITE);
		}
		searchBar.setText("");
		updateCompareIcon();
		clogTotalsBar.setVisible(false);
		setSearchStatus(" ", TEXT_DIM);
		revalidate();
	}

	private final KeyListener compareEntryKeyListener = new KeyAdapter()
	{
		@Override
		public void keyPressed(KeyEvent e)
		{
			if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
			{
				exitCompareEntry();
			}
		}
	};

	/** Redirect the search bar action to the comparison controller when in compare entry mode. */
	private void doCompareLookupFromSearchBar()
	{
		String player = searchBar.getText().trim();
		if (player.isEmpty())
		{
			return;
		}
		// Spinner must go up BEFORE the lookup: a same-player compare resolves
		// synchronously and fires onComparisonEnter, which resets the active icon.
		// Setting LOADING afterward would strand the spinner with no callback to clear it.
		searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);
		comparison.doCompareLookup(player, localRsn);
	}

	/** Update the clog totals bar above the search bar for both players. */
	private void updateClogTotalsBar()
	{
		ClogResult blueClog = lookupSession.getClogResult();
		ClogResult redClog = comparison.getCompareClogResult();

		if (blueClog != null)
		{
			int[] bt = ClogHelper.sumClogTotals(blueClog);
			String tierName = ClogHelper.getClogTierName(bt[0], bt[1]);
			ImageIcon icon = tierName != null ? clogTierIcons.get(tierName) : null;
			blueClogTotal.setIcon(icon);
			blueClogTotal.setText(String.valueOf(bt[0]));
		}
		else
		{
			blueClogTotal.setIcon(null);
			blueClogTotal.setText("--");
		}

		if (redClog != null)
		{
			int[] rt = ClogHelper.sumClogTotals(redClog);
			String tierName = ClogHelper.getClogTierName(rt[0], rt[1]);
			ImageIcon icon = tierName != null ? clogTierIcons.get(tierName) : null;
			redClogTotal.setIcon(icon);
			redClogTotal.setText(String.valueOf(rt[0]));
		}
		else
		{
			redClogTotal.setIcon(null);
			redClogTotal.setText("--");
		}

		clogTotalsBar.setVisible(true);
		clogTotalsBar.revalidate();
	}

	@Override
	public void onComparisonExit()
	{
		searchBar.setText("");
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		if (searchTextField != null)
		{
			searchTextField.setForeground(Color.WHITE);
			searchTextField.setCaretColor(Color.WHITE);
			searchTextField.removeKeyListener(compareEntryKeyListener);
		}
		compareEntryMode = false;
		updateCompareIcon();
		clogTotalsBar.setVisible(false);
		setSearchStatus(" ", TEXT_DIM);
		comparison.updateAllCells();
		comparison.updateInfoBar();
		toggleHighlighter(config.completionistHighlighter());
		searchRow.revalidate();
	}

	@Override
	public void onSwapToRedPlayer(String newPrimaryRsn)
	{
		rsn = newPrimaryRsn;
		HiscoreResult swapHiscore = lookupSession.getHiscoreResult();
		ClogResult swapClog = lookupSession.getClogResult();
		AccountType swapAccountType = displayAccountType(swapHiscore, swapClog, newPrimaryRsn);

		playerName.setText(newPrimaryRsn != null ? newPrimaryRsn : "");
		playerName.setForeground(getInfoColor());
		updateInfoIcon(swapAccountType);
		if (swapHiscore != null)
		{
			int combatLevel = swapHiscore.getCombatLevel();
			if (combatLevel > 0)
			{
				combatCell.setText(ClogHelper.pad(String.valueOf(combatLevel)));
				combatCell.setForeground(getInfoColor());
			}
			int totalLevel = swapHiscore.getTotalLevel();
			if (totalLevel > 0)
			{
				totalLvlCell.setText(ClogHelper.pad(String.valueOf(totalLevel)));
				totalLvlCell.setForeground(getInfoColor());
				totalLvlCell.setToolTipText(" ");
			}
		}
		if (swapClog != null)
		{
			lookupItemNames(swapClog);
			cells.renderClog(swapClog, config);
			updateClogCell(swapClog);
		}

		compareLabel.setVisible(true);
		updateCompareIcon();
		searchRow.revalidate();
		setSearchStatus(" ", TEXT_DIM);
		toggleHighlighter(config.completionistHighlighter());
		cells.rebuildPrimaryTooltips(localRsn);
	}


	@Override
	public void onComparisonEnter(String redRsn)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setText("");
		setSearchStatus(" ", TEXT_DIM);
		updateClogTotalsBar();
		cells.rebuildPrimaryTooltips(localRsn);
		comparison.updateAllCells();
		comparison.updateInfoBar();
		updateCompareIcon();
	}


	/** Apply account type badge to any label. */
	@Override
	public void applyBadge(JLabel label, AccountType type)
	{
		BufferedImage badge = accountBadgeImage(type);
		label.setIcon(badge != null ? new ImageIcon(badge) : null);
	}

	@Nullable
	private BufferedImage accountBadgeImage(@Nullable AccountType type)
	{
		if (type == null)
		{
			return null;
		}
		BufferedImage badge = accountBadges.badge(type);
		if (badge == null)
		{
			return null;
		}
		return type.isGroupIronman() ? badge : resizeAccountBadge(badge, 15);
	}

	private static BufferedImage resizeAccountBadge(BufferedImage badge, int height)
	{
		int width = (int) Math.round((double) badge.getWidth() / badge.getHeight() * height);
		return ImageUtil.resizeImage(badge, width, height);
	}

	// Lookup flow.

	/**
	 * Single point of control for the search-status text.
	 */
	private void setSearchStatus(String text, Color color)
	{
		searchStatus.setIcon(null);
		searchStatus.setText(text);
		searchStatus.setForeground(color);
	}

	private static void recolorClearButton(Container container, Color color)
	{
		for (Component c : container.getComponents())
		{
			if (c instanceof AbstractButton)
			{
				AbstractButton btn = (AbstractButton) c;
				if (btn.getIcon() instanceof ImageIcon)
				{
					ImageIcon icon = (ImageIcon) btn.getIcon();
					btn.setIcon(new ImageIcon(ImageUtil.recolorImage(icon.getImage(), color)));
				}
			}
			else if (c instanceof Container)
			{
				recolorClearButton((Container) c, color);
			}
		}
	}

	private String selfSearchMessage(String player)
	{
		if (!config.seenSelfGreeting())
		{
			configManager.setConfiguration("killclog", "seenSelfGreeting", true);
			return "Hey, that's you!";
		}

		int roll = ThreadLocalRandom.current().nextInt(100);
		String[] pool;
		if (roll < 1)
		{
			pool = SearchMessages.SELF_ULTRA;
		}
		else if (roll < 10)
		{
			pool = SearchMessages.SELF_RARE;
		}
		else
		{
			pool = SearchMessages.SELF;
		}
		return String.format(pool[ThreadLocalRandom.current().nextInt(pool.length)], player);
	}

	public void doLookup()
	{
		String player = searchBar.getText().trim();
		if (player.isEmpty() || lookupSession.isLookupInFlight())
		{
			if (!lookupSession.isLookupInFlight())
			{
				setSearchStatus("Enter RSN", TEXT_DIM);
			}
			return;
		}
		lookupSession.start(player, localRsn, localAccountType);
	}

	private void lookupSelfFromSearchIcon()
	{
		if (localRsn == null || localRsn.isEmpty())
		{
			return;
		}
		if (compareEntryMode || comparison.isComparisonMode())
		{
			exitCompareEntry();
		}
		searchBar.setText(localRsn);
		doLookup();
	}

	/**
	 * Reset all labels, maps, and fields to their pre-lookup state.
	 * Called at the start of every lookup to ensure a clean slate.
	 */
	private void resetAllLabels()
	{
		tooltipController.hideClickTooltip();
		if (comparison.isComparisonMode()) comparison.exit();
		if (compareEntryMode) exitCompareEntry();
		rsn = null;
		clogNotice.setText(" ");
		clogNotice.setIcon(null);
		cells.getTooltipDataMap().clear();
		cells.getRareTooltips().clear();
		for (Map.Entry<HiscoreSkill, JLabel> entry : cells.getBossLabels().entrySet())
		{
			JLabel label = entry.getValue();
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setToolTipText(" ");
			ImageIcon orig = cells.getOriginalIcons().get(entry.getKey());
			if (orig != null) label.setIcon(orig);
		}

		resetLabelMap(cells.getActivityLabels());

		playerName.setText(" ");
		playerName.setIcon(null);
		playerName.setToolTipText(null);
		compareLabel.setVisible(false);
		updateCompareIcon();
		searchRow.revalidate();

		clogInfoLabel.setIcon(null);
		clogInfoLabel.setText("");
		clogInfoLabel.setToolTipText(null);

		combatCell.setText(ClogHelper.pad("--"));
		combatCell.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalLvlCell.setText(ClogHelper.pad("--"));
		totalLvlCell.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalLvlCell.setToolTipText(null);
		if (cells.getPvpSummaryCell() != null)
		{
			cells.getPvpSummaryCell().setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}

		resetLabelMap(cells.getClueTierLabels());

		resetRareCell(cells.getThirdAgeCell(), "3rd Age");
		resetRareCell(cells.getGildedCell(), "Gilded");
		resetRareCell(cells.getHardRare(), "Hard Treasure (Rare)");
		resetRareCell(cells.getEliteRare(), "Elite Treasure (Rare)");
		resetRareCell(cells.getMasterRare(), "Master Treasure (Rare)");
		cells.getRareTooltips().remove(PanelData.CLOG_THIRD_AGE);
		cells.getRareTooltips().remove(PanelData.CLOG_GILDED);
	}

	/**
	 * Render hiscore data to the panel (extracted for cache/SWR reuse).
	 */
	private void renderHiscoreResult(HiscoreResult result, String player,
		boolean isSelf, AccountType knownType)
	{
		setSearchStatus(" ", TEXT_DIM);
		playerName.setText(rsn != null ? rsn : player);
		playerName.setForeground(getInfoColor());
		updateInfoIcon(currentInfoAccountType(knownType != null ? knownType : result.getAccountType()));
		compareLabel.setVisible(true);
		updateCompareIcon();
		searchRow.revalidate();

		int combatLevel = result.getCombatLevel();
		if (combatLevel > 0)
		{
			combatCell.setText(ClogHelper.pad(String.valueOf(combatLevel)));
			combatCell.setForeground(getInfoColor());
		}

		int totalLevel = result.getTotalLevel();
		if (totalLevel > 0)
		{
			totalLvlCell.setText(ClogHelper.pad(String.valueOf(totalLevel)));
			totalLvlCell.setForeground(getInfoColor());
			totalLvlCell.setToolTipText(" ");
		}

		searchBar.setText("");
		toggleHighlighter(config.completionistHighlighter());
		cells.rebuildPrimaryTooltips(localRsn);
	}

	/**
	 * Render clog data to the panel (extracted for SWR reuse).
	 */
	private void renderClogResult(ClogResult result, boolean isSelf, int thisLookup)
	{
		String name = result.getPlayerName();
		if (name != null && !name.isEmpty())
		{
			rsn = name;
			if (lookupSession.getHiscoreResult() != null)
			{
				playerName.setText(name);
			}
		}
		AccountType providerType = result.getProviderAccountType();
		if (providerType != null && providerType.isGroupIronman())
		{
			updateDisplayedInfoIcon(providerType);
		}
		lookupItemNames(result);
		if (lookupSession.getHiscoreResult() != null)
		{
			cells.renderClog(result, config);
			updateClogCell(result);
		}
		toggleHighlighter(config.completionistHighlighter());
		cells.rebuildPrimaryTooltips(localRsn);
	}

	private static void resetRareCell(JLabel label, String name)
	{
		if (label != null)
		{
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setToolTipText(name);
		}
	}

	private static void resetLabelMap(Map<HiscoreSkill, JLabel> labels)
	{
		for (Map.Entry<HiscoreSkill, JLabel> entry : labels.entrySet())
		{
			JLabel label = entry.getValue();
			label.setText(ClogHelper.pad("--"));
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setToolTipText(entry.getKey().getName());
		}
	}

	private void fetchRsn(String player, int thisLookup)
	{
		clogService.lookupRsn(player).thenAccept(name ->
			SwingUtilities.invokeLater(() ->
			{
				if (thisLookup != lookupSession.getLookupVersion()) return;
				if (name != null && !name.isEmpty())
				{
					rsn = name;
					if (lookupSession.getHiscoreResult() != null)
					{
						playerName.setText(name);
					}
				}
			})
		);
	}

	// Label update methods.

	private void updateClogCell(ClogResult result)
	{
		int[] totals = ClogHelper.sumClogTotals(result);
		if (totals[0] > 0)
		{
			String tierName = ClogHelper.getClogTierName(totals[0], totals[1]);
			ImageIcon icon = tierName != null ? clogTierIcons.get(tierName) : null;
			if (icon != null)
			{
				clogInfoLabel.setIcon(icon);
			}
			clogInfoLabel.setText(ClogHelper.pad(ClogHelper.formatKc(totals[0])));
			clogInfoLabel.setForeground(getInfoColor());

			clogInfoLabel.setToolTipText(" ");
		}
	}

	// Progress highlighter.

	private void toggleHighlighter(boolean enabled)
	{
		if (lookupSession.getHiscoreResult() == null) return;
		tooltipController.clearHoveredCell();

		// Info bar follows highlighter state
		Color infoColor = getInfoColor();
		playerName.setForeground(infoColor);
		clogInfoLabel.setForeground(infoColor);
		if (lookupSession.getHiscoreResult().getCombatLevel() > 0)
		{
			combatCell.setForeground(infoColor);
		}
		if (lookupSession.getHiscoreResult().getTotalLevel() > 0)
		{
			totalLvlCell.setForeground(infoColor);
		}
		if (cells.getPvpSummaryCell() != null)
		{
			int bhTotal = Math.max(0, lookupSession.getHiscoreResult().getActivityScore("Bounty Hunter - Hunter"))
				+ Math.max(0, lookupSession.getHiscoreResult().getActivityScore("Bounty Hunter - Rogue"));
			if (bhTotal > 0)
			{
				cells.getPvpSummaryCell().setForeground(infoColor);
			}
		}

		cells.renderHiscore(lookupSession.getHiscoreResult(), fourTwentyMode);
		if (lookupSession.getClogResult() != null)
		{
			cells.renderClog(lookupSession.getClogResult(), config);
			if (enabled)
			{
				Map<String, JLabel> rareCells = new LinkedHashMap<>();
				rareCells.put(PanelData.CLOG_THIRD_AGE, cells.getThirdAgeCell());
				rareCells.put(PanelData.CLOG_GILDED, cells.getGildedCell());
				rareCells.put(PanelData.RARE_HARD, cells.getHardRare());
				rareCells.put(PanelData.RARE_ELITE, cells.getEliteRare());
				rareCells.put(PanelData.RARE_MASTER, cells.getMasterRare());
				highlighter.colorCellsByCompletion(lookupSession.getHiscoreResult(), lookupSession.getClogResult(),
					cells.getRareTooltips(), rareCells, fourTwentyMode, FourTwentyMode.GREEN);
				highlighter.colorEmptyCells();
			}
		}

		if (comparison.isComparisonMode())
		{
			comparison.updateAllCells();
			comparison.updateInfoBar();
		}
	}

	// Public interface.

	public void setPlayerName(String name)
	{
		searchBar.setText(name);
	}

	public void setLoggedInPlayer(String name, AccountType accountType)
	{
		this.localRsn = name;
		this.localAccountType = accountType;
	}

	/** Returns the RSN currently displayed in the panel, or null if no lookup is shown. */
	public String getDisplayedRsn()
	{
		return rsn;
	}

	public void onBulkCaptureComplete(String name)
	{
		searchBar.setText(name);
		// Current lookup will finish soon - version it out and start fresh
		lookupSession.cancelInFlight();
		doLookup();
	}

	public void setNameAutocompleter(NameAutocompleter autocompleter)
	{
		this.nameAutocompleter = autocompleter;
		lookupSession.setNameAutocompleter(autocompleter);
		for (Component c : searchBar.getComponents())
		{
			if (c instanceof FlatTextField)
			{
				JTextField textField = ((FlatTextField) c).getTextField();
				// Idempotent: strip any prior NameAutocompleter before attaching.
				// Plugin reload (toggle off/on, hub auto-update) re-runs startUp on the
				// same panel singleton; without this, listeners stack and each keystroke
				// fires the autocomplete twice - suggestion gets inserted as both
				// highlighted suggestion AND committed text.
				for (KeyListener kl : textField.getKeyListeners())
				{
					if (kl instanceof NameAutocompleter)
					{
						textField.removeKeyListener(kl);
					}
				}
				textField.addKeyListener(autocompleter);
				break;
			}
		}
	}

	public void setPluginManager(PluginManager pluginManager)
	{
		has420Plugin = pluginManager.getPlugins().stream()
			.anyMatch(p -> p.getClass().getSimpleName().equals("FourTwentyKcPlugin"));
	}

	public void setFourTwentyVisible(boolean visible)
	{
		has420Plugin = visible;
		if (!visible)
		{
			fourTwentyMode = FourTwentyMode.OFF;
			if (lookupSession.getHiscoreResult() != null) toggleHighlighter(config.completionistHighlighter());
		}
	}

	public void onConfigChanged(String key)
	{
		switch (key)
		{
			case "completionistHighlighter":
			case "completedClogColor":
			case "missing1Color":
			case "inProgressClogColor":
			case "emptyClogColor":
			case "infoBarColor":
				toggleHighlighter(config.completionistHighlighter());
				cells.rebuildPrimaryTooltips(localRsn);
				break;
			case "hoverStyle":
				tooltipController.hideClickTooltip();
				tooltipController.clearHoveredCell();
				break;
			case "tooltipMode":
				tooltipController.restoreDefaults();
				tooltipController.captureDefaults(this);
				tooltipController.hideClickTooltip();
				tooltipController.clearHoveredCell();
				break;
		}
	}

	public void reloadTooltipSprites()
	{
		clientThread.invokeLater(() -> NativeTooltip.loadSprites(client, spriteManager));
	}

	@Override
	public void onActivate()
	{
		tooltipController.captureDefaults(this);
	}

	@Override
	public void onDeactivate()
	{
		tooltipController.restoreDefaults();
	}

	/** Safety net - restores tooltip delay if plugin is disabled while panel is active. */
	public void shutdown()
	{
		tooltipController.restoreDefaults();
	}

	@Override
	public void removeNotify()
	{
		super.removeNotify();
		tooltipController.hideClickTooltip();
	}

	private void cycleFourTwentyMode()
	{
		FourTwentyMode[] modes = FourTwentyMode.values();
		fourTwentyMode = modes[(fourTwentyMode.ordinal() + 1) % modes.length];
		toggleHighlighter(config.completionistHighlighter());
	}

	private BufferedImage getCapeImage()
	{
		return getCapeImage(lookupSession.getHiscoreResult());
	}

	private BufferedImage getCapeImage(@Nullable HiscoreResult result)
	{
		if (result == null) return null;
		boolean maxed = result.getTotalLevel() >= PanelData.MAX_TOTAL_LEVEL;
		boolean infernal = result.getKc("TzKal-Zuk") > 0;
		if (maxed && infernal) return infernalMaxCapeTip;
		if (maxed) return maxCapeTip;
		if (infernal) return infernalCapeTip;
		return null;
	}

	@Override
	public void updateInfoIcon(AccountType type)
	{
		applyBadge(playerName, type);
		playerName.setToolTipText(" ");
	}

	@Override
	public void preloadCaReward(@Nullable CombatAchievementResult ca)
	{
		if (ca != null)
		{
			requestCaRewardSprite(ca.getReward());
		}
	}

	private void updateDisplayedInfoIcon()
	{
		updateDisplayedInfoIcon(currentInfoAccountType());
	}

	private void updateDisplayedInfoIcon(@Nullable AccountType type)
	{
		if (isIdentityRowShowing())
		{
			updateInfoIcon(type);
		}
	}

	private boolean isIdentityRowShowing()
	{
		String text = playerName.getText();
		return lookupSession.getHiscoreResult() != null
			&& text != null
			&& !text.trim().isEmpty();
	}

	/** Build a ComparePlayerSummaryTooltip for comparison mode (used by both name labels). */
	private ComparePlayerSummaryTooltip buildComparePlayerSummary(JLabel owner)
	{
		ComparePlayerSummaryTooltip cmp = new ComparePlayerSummaryTooltip();
		cmp.setComponent(owner);
		HiscoreResult blueHs = lookupSession.getHiscoreResult();
		HiscoreResult redHs = comparison.getCompareHiscoreResult();
		ClogResult blueClog = lookupSession.getClogResult();
		ClogResult redClog = comparison.getCompareClogResult();
		String blueName = rsn != null ? rsn : "--";
		String redName = comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--";
		AccountType blueType = displayAccountType(blueHs, blueClog, blueName);
		AccountType redType = displayAccountType(redHs, redClog, redName);

		cmp.setBlueData(blueName,
			blueHs != null ? blueHs.getOverallRank() : -1,
			accountBadge(blueType),
			accountLabel(blueType),
			LookupQueries.getPrestige(blueHs),
			getCapeImage(blueHs));
		cmp.setRedData(redName,
			redHs.getOverallRank(),
			accountBadge(redType),
			accountLabel(redType),
			LookupQueries.getPrestige(redHs),
			getCapeImage(redHs));

		if (blueClog != null)
		{
			List<Integer> allPets = blueClog.getCategoryItems().get("all_pets");
			Set<Integer> obtainedPets = LookupQueries.getObtainedPetIds(blueClog);
			cmp.setBluePets(allPets, obtainedPets, itemManager);
		}
		if (redClog != null)
		{
			List<Integer> allPets = redClog.getCategoryItems().get("all_pets");
			Set<Integer> obtainedPets = LookupQueries.getObtainedPetIds(redClog);
			cmp.setRedPets(allPets, obtainedPets, itemManager);
		}
		return cmp;
	}

	private CompareClogSummaryTooltip buildCompareClogSummary(JComponent owner)
	{
		CompareClogSummaryTooltip cmp = new CompareClogSummaryTooltip();
		cmp.setComponent(owner);
		ClogResult blueClog = lookupSession.getClogResult();
		ClogResult redClog = comparison.getCompareClogResult();
		String blueName = rsn != null ? rsn : "--";
		String redName = comparison.getCompareRsn() != null ? comparison.getCompareRsn() : "--";
		Map<String, BufferedImage> icons = new LinkedHashMap<>();
		for (Map.Entry<String, ImageIcon> entry : clogTierIcons.entrySet())
		{
			icons.put(entry.getKey(), ClogHelper.iconToImage(entry.getValue()));
		}

		if (blueClog != null)
		{
			int[] bt = ClogHelper.sumClogTotals(blueClog);
			cmp.setBlueData(blueName, bt[0], bt[1], icons);
			boolean stale = LookupQueries.isSyncStale(lookupSession.getClogLastChanged(), 90);
			String sync = LookupQueries.syncLine(lookupSession.getClogLastChanged(), stale);
			if (sync != null) cmp.setBlueSync(sync, stale);
			cmp.setBlueRecent(LookupQueries.getRecentItems(blueClog, 4), itemManager);
		}
		else
		{
			cmp.setBlueData(blueName, 0, 0, icons);
			cmp.setBlueNotice("--");
		}

		if (redClog != null)
		{
			int[] rt = ClogHelper.sumClogTotals(redClog);
			cmp.setRedData(redName, rt[0], rt[1], icons);
			boolean stale = LookupQueries.isSyncStale(redClog.getLastChanged(), 90);
			String sync = LookupQueries.syncLine(redClog.getLastChanged(), stale);
			if (sync != null) cmp.setRedSync(sync, stale);
			cmp.setRedRecent(LookupQueries.getRecentItems(redClog, 4), itemManager);
		}
		else
		{
			cmp.setRedData(redName, 0, 0, icons);
			cmp.setRedNotice("--");
		}

		return cmp;
	}

	private BufferedImage caRewardSprite(@Nullable CombatAchievementReward reward, int height)
	{
		return reward != null ? caItemSprite(reward, height) : null;
	}

	/** Load and height-scale a CA reward item sprite, aspect preserved. */
	private BufferedImage caItemSprite(CombatAchievementReward reward, int height)
	{
		BufferedImage img = caRewardCache.get(reward);
		if (img == null)
		{
			requestCaRewardSprite(reward);
			return null;
		}
		if (img.getHeight() <= 0)
		{
			return null;
		}
		int w = (int) Math.round((double) img.getWidth() / img.getHeight() * height);
		return ImageUtil.resizeImage(img, w, height);
	}

	private void requestCaRewardSprite(CombatAchievementReward reward)
	{
		if (reward == null || caRewardCache.containsKey(reward) || !caRewardLoading.add(reward))
		{
			return;
		}
		clientThread.invokeLater(() ->
			loadItemImage(reward.itemId(), img ->
			{
				if (img != null)
				{
					caRewardCache.put(reward, img);
				}
				caRewardLoading.remove(reward);
				repaint();
			}));
	}

	private AccountType currentInfoAccountType()
	{
		HiscoreResult hiscore = lookupSession.getHiscoreResult();
		return currentInfoAccountType(hiscore != null ? hiscore.getAccountType() : null);
	}

	private AccountType currentInfoAccountType(@Nullable AccountType fallback)
	{
		ClogResult clog = lookupSession.getClogResult();
		if (clog != null && clog.getProviderAccountType() != null && clog.getProviderAccountType().isGroupIronman())
		{
			return clog.getProviderAccountType();
		}
		AccountType cachedProviderType = currentRuneProfileAccountType();
		if (cachedProviderType != null)
		{
			return cachedProviderType;
		}
		return fallback;
	}

	@Nullable
	private AccountType displayAccountType(@Nullable HiscoreResult hiscore,
		@Nullable ClogResult clog, @Nullable String player)
	{
		return AccountType.displayType(LookupQueries.accountType(hiscore, clog),
			runeProfileGroupAccountType(player));
	}

	@Nullable
	private BufferedImage accountBadge(@Nullable AccountType type)
	{
		return accountBadges.badge(type);
	}

	@Nullable
	private static String accountLabel(@Nullable AccountType type)
	{
		return type != null ? ClogHelper.accountLabel(type) : null;
	}

	@Nullable
	private AccountType currentRuneProfileAccountType()
	{
		return runeProfileGroupAccountType(lookupSession.getCurrentLookupRsn());
	}

	@Nullable
	private AccountType runeProfileGroupAccountType(@Nullable String player)
	{
		if (player == null || player.isBlank() || "--".equals(player))
		{
			return null;
		}
		AccountType type = runeProfileService.getCachedAccountType(player);
		return type != null && type.isGroupIronman() ? type : null;
	}

	private void lookupItemNames(ClogResult result)
	{
		Set<Integer> allIds = new HashSet<>();
		for (List<ClogResult.ClogItem> items : result.getObtainedItems().values())
		{
			for (ClogResult.ClogItem item : items) allIds.add(item.getId());
		}
		for (List<Integer> ids : result.getCategoryItems().values()) allIds.addAll(ids);

		List<Integer> missing = new ArrayList<>();
		for (int id : allIds)
		{
			if (!result.isItemResolved(id)) missing.add(id);
		}
		if (missing.isEmpty()) return;
		if (!itemNameResolutions.add(result)) return;

		log.debug("Resolving {} untradeable item names via game cache", missing.size());
		resolveItemNames(result, missing, 0);
	}

	private void resolveItemNames(ClogResult result, List<Integer> missing, int start)
	{
		clientThread.invokeLater(() ->
		{
			int end = Math.min(start + ITEM_NAME_RESOLVE_BATCH_SIZE, missing.size());
			for (int i = start; i < end; i++)
			{
				try
				{
					int id = missing.get(i);
					String name = itemManager.getItemComposition(id).getName();
					if (name != null && !name.isEmpty() && !name.equals("null") && !name.equals("Null"))
					{
						result.markItemResolved(id, name);
					}
				}
				catch (Exception e)
				{
					// Item not in cache - skip
				}
			}
			if (end < missing.size())
			{
				resolveItemNames(result, missing, end);
				return;
			}
			itemNameResolutions.remove(result);
			SwingUtilities.invokeLater(() -> cells.rebuildPrimaryTooltips(localRsn));
		});
	}

	private static void paintUnderline(JLabel label, Graphics g, boolean leftAligned)
	{
		if (!Boolean.TRUE.equals(label.getClientProperty("underlined")))
		{
			return;
		}
		String text = label.getText();
		if (text == null || text.isBlank())
		{
			return;
		}
		FontMetrics fm = g.getFontMetrics();
		int textWidth = fm.stringWidth(text.trim());
		int y = (label.getHeight() + fm.getAscent() - fm.getDescent()) / 2 + 1;
		g.setColor(label.getForeground());
		if (leftAligned)
		{
			int iconOffset = label.getIcon() != null ? label.getIcon().getIconWidth() + label.getIconTextGap() : 0;
			int textStart = label.getInsets().left + iconOffset;
			g.drawLine(textStart, y, textStart + textWidth, y);
		}
		else
		{
			int textEnd = label.getWidth() - label.getInsets().right;
			g.drawLine(textEnd - textWidth, y, textEnd, y);
		}
	}

	// LookupSession.Listener

	@Override
	public void onLookupStart(String player, boolean isSelf, boolean isFirstSelfGreeting)
	{
		if (isSelf)
		{
			setSearchStatus(selfSearchMessage(player), SearchMessages.SELF_COLOR);
		}
		else
		{
			int searchIdx = ThreadLocalRandom.current().nextInt(SearchMessages.SEARCH.length);
			setSearchStatus(String.format(SearchMessages.SEARCH[searchIdx], player), TEXT_DIM);
		}
		searchBar.setIcon(IconTextField.Icon.LOADING_DARKER);
		resetAllLabels();
	}

	@Override
	public void onCachedResult(String player, HiscoreResult hiscore, @Nullable ClogResult clog,
		boolean isSelf, @Nullable AccountType knownType, boolean isFirstSelfGreeting)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		if (!isFirstSelfGreeting)
		{
			setSearchStatus(" ", TEXT_DIM);
		}
		renderHiscoreResult(hiscore, player, isSelf, knownType);
		if (clog != null)
		{
			renderClogResult(clog, isSelf, lookupSession.getLookupVersion());
		}
	}

	@Override
	public void onHiscoreResult(String player, HiscoreResult hiscore,
		boolean isSelf, @Nullable AccountType knownType, boolean isFirstSelfGreeting)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		if (!isFirstSelfGreeting)
		{
			setSearchStatus(" ", TEXT_DIM);
		}
		renderHiscoreResult(hiscore, player, isSelf, knownType);
		// Clog may have arrived first with a GIM type the hiscores can't detect.
		ClogResult clog = lookupSession.getClogResult();
		if (clog != null)
		{
			updateDisplayedInfoIcon();
			cells.renderClog(clog, config);
			updateClogCell(clog);
		}
	}

	@Override
	public void onClogResult(String player, @Nullable ClogResult clog, boolean isSelf, int lookupVersionAtFire)
	{
		if (clog != null)
		{
			renderClogResult(clog, isSelf, lookupVersionAtFire);
		}
		else
		{
			if (isSelf)
			{
				clogNotice.setText(SYNC_NOTICE);
				clogNotice.setIcon(new ImageIcon(getSyncIcon()));
				BufferedImage icon = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
				clogInfoLabel.setIcon(new ImageIcon(ImageUtil.resizeImage(icon, 15, 15)));
				clogInfoLabel.setText(ClogHelper.pad("Sync"));
				clogInfoLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				clogInfoLabel.setToolTipText(" ");
			}
			else
			{
				clogNotice.setText(" ");
				clogNotice.setIcon(null);
			}
			fetchRsn(player, lookupVersionAtFire);
		}
	}

	@Override
	public void onCaResult(String player, @Nullable CombatAchievementResult ca, boolean isSelf, int lookupVersionAtFire)
	{
		if (lookupVersionAtFire == lookupSession.getLookupVersion())
		{
			preloadCaReward(ca);
			if (!comparison.isComparisonMode())
			{
				updateDisplayedInfoIcon();
			}
		}
	}

	@Override
	public void onNotFound(String player)
	{
		int notFoundIdx = ThreadLocalRandom.current().nextInt(SearchMessages.NOT_FOUND.length);
		setSearchStatus(String.format(SearchMessages.NOT_FOUND[notFoundIdx], player), NOT_FOUND);
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		playerName.setText(" ");
		playerName.setIcon(null);
		playerName.setToolTipText(null);
		clogInfoLabel.setText("");
		clogInfoLabel.setIcon(null);
		clogInfoLabel.setToolTipText(null);
		searchBar.setText("");
	}

	@Override
	public void onError(String player, Throwable error)
	{
		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setText("");
		setSearchStatus("Lookup failed", TEXT_DIM);
		playerName.setText(" ");
		playerName.setIcon(null);
		playerName.setToolTipText(null);
		clogInfoLabel.setText("");
		clogInfoLabel.setIcon(null);
		clogInfoLabel.setToolTipText(null);
	}

	// ComparisonController.Listener

	@Override
	public void onCompareDataReady()
	{
		comparison.updateInfoBar();
		updateClogTotalsBar();
	}

	@Override
	public void onCompareStatus(String msg, Color color)
	{
		setSearchStatus(msg, color);
	}

	@Override
	public void onCompareError(String player, Throwable err)
	{
		if (err != null)
		{
			setSearchStatus("Lookup failed", ComparisonController.COMPARE_RED);
		}
		updateCompareIcon();
	}

	// ComparisonController.CellRenderTarget
	// Read-only accessors the controller uses for panel-side summary-bar
	// widgets (combat + totalLvl cells, playerName + clogInfoLabel).
	// Cell maps + per-cell labels live on Cells; ComparisonController reads
	// those directly.

	@Override
	public JLabel combatCell()
	{
		return combatCell;
	}

	@Override
	public JLabel totalLvlCell()
	{
		return totalLvlCell;
	}

	@Override
	public JLabel playerName()
	{
		return playerName;
	}

	@Override
	public JLabel clogInfoLabel()
	{
		return clogInfoLabel;
	}

	@Override
	public void preloadClogItemNames(ClogResult clog)
	{
		lookupItemNames(clog);
	}

	@Override
	public void restoreClogCellForCompare(ClogResult clog)
	{
		if (clog != null)
		{
			updateClogCell(clog);
		}
		else
		{
			clogInfoLabel.setText("");
			clogInfoLabel.setIcon(null);
			clogInfoLabel.setToolTipText(null);
		}
	}
}
