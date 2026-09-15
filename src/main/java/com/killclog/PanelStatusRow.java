package com.killclog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.AccessLevel;
import lombok.Setter;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

/**
 * The status line above the search bar and the killclog.com one-click
 * controls that share it. Lookup messages arrive through
 * {@link #setSearchStatus}; sync and character feedback arrive through the
 * show/reset methods and only write while the bar is free or already theirs.
 * Widgets are touched on the EDT only.
 */
final class PanelStatusRow
{
	private static final String SYNC_HOVER_TEXT = "sync to killclog.com";
	private static final String SYNC_FAILURE_HOVER_TEXT = "sync failed - click to retry";
	private static final String CHARACTER_HOVER_TEXT = "publish character";
	private static final String CHARACTER_FAILURE_HOVER_TEXT = "publish failed - click to retry";
	// k1: the brand lime. Status chrome, not data coloring, so it does not
	// route through the user-themable completion color.
	private static final Color SYNC_K1 = new Color(78, 240, 21);
	private static final ImageIcon SYNC_CHALICE_DIM = chaliceTinted(0.45f, null);
	private static final ImageIcon SYNC_CHALICE_LIT = chaliceTinted(1f, null);
	private static final ImageIcon SYNC_CHALICE_SYNCED = chaliceTinted(1f, SYNC_K1);

	private final JPanel row;
	private final JLabel searchStatus = new JLabel(" ");
	private final JLabel syncArrow = new JLabel();
	private final JLabel characterPublish = new JLabel();
	private final Color textDim;
	private final SpriteManager spriteManager;
	private final TooltipController tooltipController;
	private final FirstPartyFeedback syncFeedback;
	private final FirstPartyFeedback characterFeedback;
	private String characterNoticeText;
	private String characterNoticeDetail;

	private boolean syncArrowEnabled;
	private boolean syncArrowHasData;
	private boolean characterPublishEnabled;
	@Setter(AccessLevel.PACKAGE)
	private Runnable killclogSyncHandler;
	@Setter(AccessLevel.PACKAGE)
	private Runnable characterPublishHandler;
	private Timer firstPartyStatusClearTimer;
	private Timer syncSuccessGlowTimer;
	private Timer characterSuccessGlowTimer;
	private BufferedImage characterBase;
	private boolean syncedGlow;
	private boolean syncChaliceHovered;
	private boolean characterPublishedGlow;
	private boolean characterHovered;

	PanelStatusRow(KillClogConfig config, SpriteManager spriteManager,
		TooltipController tooltipController, Color textDim)
	{
		this.spriteManager = spriteManager;
		this.tooltipController = tooltipController;
		this.textDim = textDim;
		this.syncFeedback = new FirstPartyFeedback(config, this::showSyncStatus,
			this::flashSyncSuccess, "sync failed");
		this.characterFeedback = new FirstPartyFeedback(config, this::showCharacterStatusText,
			this::flashCharacterSuccess, KillClogPlugin.CHARACTER_FAILED_STATUS);
		PanelSearchBox.configureStatus(searchStatus, textDim);
		this.row = build();
	}

	JPanel component()
	{
		return row;
	}

	String statusText()
	{
		return searchStatus.getText();
	}

	/**
	 * Single point of control for the status text. Also the arrow's
	 * landlord: the sync arrow only shows while the bar is free (blank, or
	 * showing the arrow's own hover text), so search progress, player-not-found
	 * lines, and the sync flow itself all naturally park it.
	 */
	void setSearchStatus(String text, Color color)
	{
		searchStatus.setIcon(null);
		searchStatus.setText(text);
		searchStatus.setForeground(color);
		refreshFirstPartyVisibility();
	}

	// ── layout ─────────────────────────────────────────────────────────

	private JPanel build()
	{
		syncArrow.setIcon(SYNC_CHALICE_DIM);
		tooltipController.trackTooltipComponent(syncArrow);
		tooltipController.trackTooltipComponent(characterPublish);
		characterPublish.setBorder(BorderFactory.createEmptyBorder(0, 3, 6, 2));
		characterPublish.setVerticalAlignment(JLabel.CENTER);
		characterPublish.setVisible(false);
		characterPublish.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (characterPublish.isVisible())
				{
					characterHovered = true;
					refreshCharacterIcon(true);
					tooltipController.setTooltipText(characterPublish, characterTooltipText(characterNoticeDetail != null
						? characterNoticeDetail : characterFeedback.lastFailure()));
					setSearchStatus(characterNoticeText != null ? characterNoticeText
						: characterFeedback.lastFailure() == null ? CHARACTER_HOVER_TEXT : CHARACTER_FAILURE_HOVER_TEXT, SYNC_K1);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				characterHovered = false;
				tooltipController.setTooltipText(characterPublish, null);
				refreshCharacterIcon(false);
				if (isCharacterHoverStatus(searchStatus.getText()))
				{
					setSearchStatus(" ", textDim);
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e)
					&& characterPublish.isVisible() && characterPublishHandler != null)
				{
					characterPublishHandler.run();
				}
			}
		});
		requestCharacterIcon();

		// Sits vertically centered in the band between the panel top and the
		// search bar: the bottom inset biases the icon upward within the
		// taller status row so its center lands on the band's center.
		syncArrow.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 5));
		syncArrow.setVerticalAlignment(JLabel.CENTER);
		syncArrow.setVisible(false);
		syncArrow.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (syncArrow.isVisible())
				{
					syncChaliceHovered = true;
					refreshSyncChalice(true);
					tooltipController.setTooltipText(syncArrow, syncFeedback.lastFailure());
					setSearchStatus(syncFeedback.lastFailure() == null
						? SYNC_HOVER_TEXT : SYNC_FAILURE_HOVER_TEXT, SYNC_K1);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				syncChaliceHovered = false;
				tooltipController.setTooltipText(syncArrow, null);
				refreshSyncChalice(false);
				if (isSyncHoverStatus(searchStatus.getText()))
				{
					setSearchStatus(" ", textDim);
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e)
					&& syncArrow.isVisible() && killclogSyncHandler != null)
				{
					killclogSyncHandler.run();
				}
			}
		});

		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		// The parent column is BoxLayout: children must agree on alignment or
		// the whole stack shears sideways.
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		reserveStatusRowHeight(row, searchStatus.getPreferredSize().height);
		row.add(searchStatus, BorderLayout.CENTER);
		JPanel actions = new JPanel();
		actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
		actions.setOpaque(false);
		actions.add(characterPublish);
		actions.add(syncArrow);
		row.add(actions, BorderLayout.EAST);
		return row;
	}

	static void reserveStatusRowHeight(JPanel row, int labelHeight)
	{
		int height = Math.max(labelHeight, 14) + 2;
		row.setMinimumSize(new Dimension(0, height));
		row.setPreferredSize(new Dimension(0, height));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
	}

	// ── icons ──────────────────────────────────────────────────────────

	/**
	 * The sync control wears the Kill Clog chalice itself: dim at rest,
	 * full red on hover, k1 green briefly after a successful sync.
	 */
	private static BufferedImage chaliceBase()
	{
		BufferedImage src = ImageUtil.loadImageResource(KillClogPlugin.class, "icon.png");
		int h = 13;
		int w = Math.max(1, src.getWidth() * h / src.getHeight());
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	private static ImageIcon chaliceTinted(float alpha, Color tint)
	{
		return imageTinted(chaliceBase(), alpha, tint);
	}

	private static ImageIcon imageTinted(BufferedImage base, float alpha, Color tint)
	{
		BufferedImage out = new BufferedImage(
			base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < base.getHeight(); y++)
		{
			for (int x = 0; x < base.getWidth(); x++)
			{
				int argb = base.getRGB(x, y);
				int a = (argb >>> 24);
				if (a == 0)
				{
					continue;
				}
				int r = (argb >> 16) & 0xFF;
				int gch = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;
				if (tint != null)
				{
					// Luminance drives the tint so the source keeps its shading.
					int lum = Math.min(255, (int) (0.299 * r + 0.587 * gch + 0.114 * b) + 90);
					r = tint.getRed() * lum / 255;
					gch = tint.getGreen() * lum / 255;
					b = tint.getBlue() * lum / 255;
				}
				int na = Math.min(255, Math.round(a * alpha));
				out.setRGB(x, y, (na << 24) | (r << 16) | (gch << 8) | b);
			}
		}
		return new ImageIcon(out);
	}

	private void refreshSyncChalice(boolean hovered)
	{
		syncArrow.setIcon(syncedGlow ? SYNC_CHALICE_SYNCED
			: hovered ? SYNC_CHALICE_LIT : SYNC_CHALICE_DIM);
	}

	/** Any thread; also re-run after a sprite reload so a resource pack swap redraws the icon. */
	void requestCharacterIcon()
	{
		spriteManager.getSpriteAsync(SpriteID.AchievementDiaryIcons.BROWN_CHARACTER_SUMMARY, 0, sprite ->
		{
			if (sprite == null)
			{
				return;
			}
			int height = 14;
			int width = Math.max(1, sprite.getWidth() * height / sprite.getHeight());
			BufferedImage resized = ImageUtil.resizeImage(sprite, width, height);
			SwingUtilities.invokeLater(() ->
			{
				characterBase = resized;
				refreshCharacterIcon(false);
				refreshFirstPartyVisibility();
			});
		});
	}

	private void refreshCharacterIcon(boolean hovered)
	{
		if (characterBase == null)
		{
			characterPublish.setIcon(null);
			return;
		}
		characterPublish.setIcon(imageTinted(characterBase,
			characterPublishedGlow || hovered ? 1f : 0.45f,
			characterPublishedGlow ? SYNC_K1 : null));
	}

	// ── visibility and bar ownership ───────────────────────────────────

	private boolean statusBarFree()
	{
		String text = searchStatus.getText();
		return text == null || text.trim().isEmpty()
			|| isSyncHoverStatus(text) || isCharacterHoverStatus(text);
	}

	private void refreshFirstPartyVisibility()
	{
		boolean visible = syncArrowHasData && statusBarFree();
		syncArrow.setVisible(syncArrowEnabled && visible);
		characterPublish.setVisible(characterPublishEnabled && characterBase != null && visible);
	}

	private boolean barOwnedBySync()
	{
		return isSyncOwnedStatus(searchStatus.getText());
	}

	static boolean isSyncOwnedStatus(String text)
	{
		return isSyncHoverStatus(text) || "syncing...".equals(text)
			|| "retrying...".equals(text) || "sync failed".equals(text);
	}

	private static boolean isSyncHoverStatus(String text)
	{
		return SYNC_HOVER_TEXT.equals(text) || SYNC_FAILURE_HOVER_TEXT.equals(text);
	}

	private static boolean isCharacterHoverStatus(String text)
	{
		return CHARACTER_HOVER_TEXT.equals(text) || CHARACTER_FAILURE_HOVER_TEXT.equals(text)
			|| isCharacterNotice(text);
	}

	private static String characterTooltipText(String detail)
	{
		if (detail == null) return null;
		return "<html><div style='width:220px'>" + detail.replace("&", "&amp;")
			.replace("<", "&lt;").replace(">", "&gt;") + "</div></html>";
	}

	static boolean isCharacterNotice(String text)
	{
		return KillClogPlugin.CHARACTER_PENDING_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_RECOVERY_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_DISABLED_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_APPEARANCE_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_UNKNOWN_STATUS.equals(text);
	}

	private boolean barOwnedByCharacter()
	{
		String text = searchStatus.getText();
		return isCharacterHoverStatus(text)
			|| KillClogPlugin.CHARACTER_RENDERING_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_BUSY_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_PUBLISHED_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_FAILED_STATUS.equals(text);
	}

	static boolean canFlashSyncSuccess(String text)
	{
		return text == null || text.trim().isEmpty() || isSyncOwnedStatus(text);
	}

	private boolean barOwnedByFirstParty()
	{
		return barOwnedBySync() || barOwnedByCharacter();
	}

	// ── plugin-facing controls ─────────────────────────────────────────

	/** The plugin flips this with the sync checkbox; off hides the arrow. */
	void setSyncArrowEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			syncArrowEnabled = enabled;
			if (!enabled)
			{
				syncChaliceHovered = false;
				syncFeedback.reset();
				tooltipController.setTooltipText(syncArrow, null);
				clearSyncSuccessGlow();
			}
			refreshFirstPartyVisibility();
		});
	}

	/**
	 * The chalice earns its appearance: hidden until this player's local
	 * collection log holds at least one first-party capture, so a fresh
	 * install can never click sync with nothing to send.
	 */
	void setSyncArrowHasData(boolean hasData)
	{
		SwingUtilities.invokeLater(() ->
		{
			syncArrowHasData = hasData;
			if (!hasData)
			{
				syncChaliceHovered = false;
				clearSyncSuccessGlow();
			}
			refreshFirstPartyVisibility();
		});
	}

	void setCharacterPublishEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			characterPublishEnabled = enabled;
			if (!enabled)
			{
				characterFeedback.reset();
				characterNoticeText = null;
				characterNoticeDetail = null;
				characterHovered = false;
				tooltipController.setTooltipText(characterPublish, null);
				clearCharacterSuccessGlow();
			}
			refreshFirstPartyVisibility();
		});
	}

	void showSyncProgress(boolean manual, String text, boolean autoClear)
	{
		syncFeedback.progress(manual, text, autoClear);
	}

	/** Silent results still retain failures for the control's next deliberate hover. */
	void showSyncResult(boolean manual, boolean ok, String message)
	{
		syncFeedback.complete(manual, ok, message);
	}

	/** Drop account-scoped sync feedback without disturbing lookup or character status. */
	void resetSyncFeedback()
	{
		SwingUtilities.invokeLater(() ->
		{
			stopFirstPartyStatusTimer();
			syncFeedback.reset();
			tooltipController.setTooltipText(syncArrow, null);
			syncChaliceHovered = false;
			clearSyncSuccessGlow();
			if (barOwnedBySync())
			{
				setSearchStatus(" ", textDim);
			}
		});
	}

	void showCharacterPublishStatus(String text, boolean ok, boolean autoClear, String detail)
	{
		runFeedbackOnEdt(() ->
		{
			characterNoticeText = null;
			characterNoticeDetail = null;
			if (text.trim().isEmpty())
			{
				characterFeedback.reset();
				tooltipController.setTooltipText(characterPublish, null);
				clearCharacterSuccessGlow();
				if (barOwnedByCharacter())
				{
					stopFirstPartyStatusTimer();
					setSearchStatus(" ", textDim);
				}
			}
			else if (ok || KillClogPlugin.CHARACTER_FAILED_STATUS.equals(text))
			{
				characterFeedback.complete(true, ok, detail != null
					? detail : "Character upload failed. Click to retry.");
			}
			else
			{
				characterFeedback.reset();
				if (isCharacterNotice(text))
				{
					characterNoticeText = KillClogPlugin.CHARACTER_PENDING_STATUS.equals(text)
						? KillClogPlugin.CHARACTER_UNKNOWN_STATUS : text;
					characterNoticeDetail = detail;
				}
				characterFeedback.progress(true, text, autoClear);
			}
			if (characterHovered)
			{
				tooltipController.setTooltipText(characterPublish,
					characterTooltipText(characterNoticeDetail != null ? characterNoticeDetail : characterFeedback.lastFailure()));
			}
		});
	}

	void refreshSyncFeedbackSettings()
	{
		runFeedbackOnEdt(() ->
		{
			// Changing feedback preferences clears existing transient chrome;
			// the next callback reads the new settings. Failure history remains.
			if (barOwnedByFirstParty())
			{
				stopFirstPartyStatusTimer();
				setSearchStatus(" ", textDim);
			}
			clearSyncSuccessGlow();
			clearCharacterSuccessGlow();
		});
	}

	/** Stops every timer and hides the controls; the row stays reusable afterwards. */
	void shutdown()
	{
		stopFirstPartyStatusTimer();
		syncArrowEnabled = false;
		syncArrowHasData = false;
		syncChaliceHovered = false;
		syncFeedback.reset();
		characterFeedback.reset();
		characterNoticeText = null;
		characterNoticeDetail = null;
		characterHovered = false;
		clearCharacterSuccessGlow();
		clearSyncSuccessGlow();
		refreshFirstPartyVisibility();
	}

	// ── feedback internals ─────────────────────────────────────────────

	private static void runFeedbackOnEdt(Runnable action)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			action.run();
		}
		else
		{
			SwingUtilities.invokeLater(action);
		}
	}

	private void stopFirstPartyStatusTimer()
	{
		if (firstPartyStatusClearTimer != null)
		{
			firstPartyStatusClearTimer.stop();
			firstPartyStatusClearTimer = null;
		}
	}

	private void stopSyncSuccessGlowTimer()
	{
		if (syncSuccessGlowTimer != null)
		{
			syncSuccessGlowTimer.stop();
			syncSuccessGlowTimer = null;
		}
	}

	private void clearSyncSuccessGlow()
	{
		stopSyncSuccessGlowTimer();
		syncedGlow = false;
		refreshSyncChalice(syncChaliceHovered);
	}

	/**
	 * Sync-flow status line: "syncing..." while in flight, then "sync failed"
	 * which clears itself after a beat. Any thread. The bar
	 * is shared: sync text only writes when the bar is free or already the
	 * sync's, so lookup and player-not-found messages are never stomped.
	 * Sync chrome speaks in k1; only failure stays dim.
	 */
	private void showSyncStatus(String text, boolean autoClear)
	{
		runFeedbackOnEdt(() ->
		{
			if (!statusBarFree() && !barOwnedBySync())
			{
				return;
			}
			stopFirstPartyStatusTimer();
			clearSyncSuccessGlow();
			setSearchStatus(text, "sync failed".equals(text) ? textDim : SYNC_K1);
			if (autoClear)
			{
				firstPartyStatusClearTimer = new Timer(2500, e ->
				{
					if (text.equals(searchStatus.getText()))
					{
						setSearchStatus(" ", textDim);
					}
					firstPartyStatusClearTimer = null;
				});
				firstPartyStatusClearTimer.setRepeats(false);
				firstPartyStatusClearTimer.start();
			}
		});
	}

	/** Successful sync feedback is icon-only so the shared status row never moves. */
	private void flashSyncSuccess()
	{
		runFeedbackOnEdt(() ->
		{
			if (!syncArrowEnabled || !syncArrowHasData
				|| !canFlashSyncSuccess(searchStatus.getText()))
			{
				return;
			}
			stopFirstPartyStatusTimer();
			stopSyncSuccessGlowTimer();
			if (barOwnedBySync())
			{
				setSearchStatus(" ", textDim);
			}
			syncedGlow = true;
			refreshFirstPartyVisibility();
			refreshSyncChalice(syncChaliceHovered);

			syncSuccessGlowTimer = new Timer(2500, e ->
			{
				syncedGlow = false;
				refreshSyncChalice(syncChaliceHovered);
				syncSuccessGlowTimer = null;
			});
			syncSuccessGlowTimer.setRepeats(false);
			syncSuccessGlowTimer.start();
		});
	}

	private void clearCharacterSuccessGlow()
	{
		if (characterSuccessGlowTimer != null)
		{
			characterSuccessGlowTimer.stop();
			characterSuccessGlowTimer = null;
		}
		characterPublishedGlow = false;
		refreshCharacterIcon(characterHovered);
	}

	private void flashCharacterSuccess()
	{
		if (!characterPublishEnabled || !syncArrowHasData
			|| !statusBarFree() && !barOwnedByCharacter())
		{
			return;
		}
		clearCharacterSuccessGlow();
		if (barOwnedByCharacter())
		{
			stopFirstPartyStatusTimer();
			setSearchStatus(" ", textDim);
		}
		characterPublishedGlow = true;
		refreshCharacterIcon(characterHovered);
		characterSuccessGlowTimer = new Timer(2500, e -> clearCharacterSuccessGlow());
		characterSuccessGlowTimer.setRepeats(false);
		characterSuccessGlowTimer.start();
	}

	private void showCharacterStatusText(String text, boolean autoClear)
	{
		runFeedbackOnEdt(() ->
		{
			if (!statusBarFree() && !barOwnedByCharacter())
			{
				return;
			}
			stopFirstPartyStatusTimer();
			clearCharacterSuccessGlow();
			boolean active = KillClogPlugin.CHARACTER_RENDERING_STATUS.equals(text);
			setSearchStatus(text, active ? SYNC_K1 : textDim);
			if (autoClear)
			{
				firstPartyStatusClearTimer = new Timer(3000, e ->
				{
					if (text.equals(searchStatus.getText()))
					{
						setSearchStatus(" ", textDim);
					}
					firstPartyStatusClearTimer = null;
				});
				firstPartyStatusClearTimer.setRepeats(false);
				firstPartyStatusClearTimer.start();
			}
		});
	}
}
