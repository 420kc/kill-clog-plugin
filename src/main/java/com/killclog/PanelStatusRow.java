package com.killclog;

import com.killclog.StatusMessage.Kind;
import com.killclog.StatusMessage.Owner;
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
 * controls that share it. Every occupant is a {@link StatusMessage}: lookup
 * text arrives through {@link #setSearchStatus}, sync and character feedback
 * through the show/reset methods. Feedback only lands while the row is free
 * or already its owner's, and an expiry only ever clears the message it was
 * started for. Widgets are touched on the EDT only.
 */
final class PanelStatusRow
{
	private static final String SYNC_HOVER_TEXT = "sync to killclog.com";
	private static final String SYNC_FAILURE_HOVER_TEXT = "sync failed - click to retry";
	private static final String CHARACTER_HOVER_TEXT = "publish character";
	private static final String CHARACTER_FAILURE_HOVER_TEXT = "publish failed - click to retry";
	private static final int SYNC_EXPIRY_MS = 2500;
	private static final int CHARACTER_EXPIRY_MS = 3000;
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
	/** What the row shows now; null while blank. */
	private StatusMessage current;

	private boolean syncArrowEnabled;
	private boolean syncArrowHasData;
	private boolean characterPublishEnabled;
	@Setter(AccessLevel.PACKAGE)
	private Runnable killclogSyncHandler;
	@Setter(AccessLevel.PACKAGE)
	private Runnable characterPublishHandler;
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
		this.syncFeedback = new FirstPartyFeedback(config,
			(kind, text, autoClear) -> showFeedback(Owner.SYNC, kind, text, autoClear),
			this::flashSyncSuccess, "sync failed");
		this.characterFeedback = new FirstPartyFeedback(config,
			(kind, text, autoClear) -> showFeedback(Owner.CHARACTER, kind, text, autoClear),
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
	 * Lookup text from the panel. The panel manages lookup lifetimes itself:
	 * text holds the row until the panel replaces it, and blank hands the row
	 * back. The controls only show while the row is free, so search progress
	 * and player-not-found lines naturally park them.
	 */
	void setSearchStatus(String text, Color color)
	{
		if (text == null || text.trim().isEmpty())
		{
			clear();
		}
		else
		{
			show(new StatusMessage(Owner.LOOKUP, Kind.RESULT, text, color));
		}
	}

	// ── occupancy ──────────────────────────────────────────────────────

	/** Replacing a message retires it: its expiry can never fire on a successor. */
	private void show(StatusMessage message)
	{
		if (current != null)
		{
			current.cancelExpiry();
		}
		current = message;
		searchStatus.setText(message.text);
		searchStatus.setForeground(message.color);
		refreshFirstPartyVisibility();
	}

	private void clear()
	{
		if (current != null)
		{
			current.cancelExpiry();
			current = null;
		}
		searchStatus.setText(" ");
		searchStatus.setForeground(textDim);
		refreshFirstPartyVisibility();
	}

	/** Blank, hover lines and notices leave the row free for the next writer. */
	private boolean free()
	{
		return current == null || current.yields();
	}

	private boolean ownedBy(Owner owner)
	{
		return current != null && current.owner == owner;
	}

	/** A control's own hover line or notice clears when the pointer leaves it. */
	private void clearYielding(Owner owner)
	{
		if (ownedBy(owner) && current.yields())
		{
			clear();
		}
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
					show(new StatusMessage(Owner.CHARACTER, Kind.HOVER, characterNoticeText != null ? characterNoticeText
						: characterFeedback.lastFailure() == null ? CHARACTER_HOVER_TEXT : CHARACTER_FAILURE_HOVER_TEXT, SYNC_K1));
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				characterHovered = false;
				tooltipController.setTooltipText(characterPublish, null);
				refreshCharacterIcon(false);
				clearYielding(Owner.CHARACTER);
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
					show(new StatusMessage(Owner.SYNC, Kind.HOVER, syncFeedback.lastFailure() == null
						? SYNC_HOVER_TEXT : SYNC_FAILURE_HOVER_TEXT, SYNC_K1));
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				syncChaliceHovered = false;
				tooltipController.setTooltipText(syncArrow, null);
				refreshSyncChalice(false);
				clearYielding(Owner.SYNC);
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

	private void refreshFirstPartyVisibility()
	{
		boolean visible = syncArrowHasData && free();
		syncArrow.setVisible(syncArrowEnabled && visible);
		characterPublish.setVisible(characterPublishEnabled && characterBase != null && visible);
	}

	private static String characterTooltipText(String detail)
	{
		if (detail == null) return null;
		return "<html><div style='width:220px'>" + detail.replace("&", "&amp;")
			.replace("<", "&lt;").replace(">", "&gt;") + "</div></html>";
	}

	/** The plugin's actionable character hints; shown as notices and repeated on hover. */
	static boolean isCharacterNotice(String text)
	{
		return KillClogPlugin.CHARACTER_PENDING_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_RECOVERY_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_DISABLED_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_APPEARANCE_STATUS.equals(text)
			|| KillClogPlugin.CHARACTER_UNKNOWN_STATUS.equals(text);
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
			// Stops whichever expiry is running, character included; see the
			// status row maintenance notes before changing this policy.
			if (current != null)
			{
				current.cancelExpiry();
			}
			syncFeedback.reset();
			tooltipController.setTooltipText(syncArrow, null);
			syncChaliceHovered = false;
			clearSyncSuccessGlow();
			if (ownedBy(Owner.SYNC))
			{
				clear();
			}
		});
	}

	/**
	 * The plugin speaks in status strings; they are classified once here.
	 * Blank withdraws the character's message, a success or failure completes
	 * the request, the notice constants are notices, "updating character..."
	 * is progress, and anything else is a result.
	 */
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
				if (ownedBy(Owner.CHARACTER))
				{
					clear();
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
				Kind kind = Kind.RESULT;
				if (isCharacterNotice(text))
				{
					kind = Kind.NOTICE;
					characterNoticeText = KillClogPlugin.CHARACTER_PENDING_STATUS.equals(text)
						? KillClogPlugin.CHARACTER_UNKNOWN_STATUS : text;
					characterNoticeDetail = detail;
				}
				else if (KillClogPlugin.CHARACTER_RENDERING_STATUS.equals(text))
				{
					kind = Kind.PROGRESS;
				}
				characterFeedback.show(true, kind, text, autoClear);
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
			if (current != null && current.owner != Owner.LOOKUP)
			{
				clear();
			}
			clearSyncSuccessGlow();
			clearCharacterSuccessGlow();
		});
	}

	/** Stops every timer and hides the controls; the row stays reusable afterwards. */
	void shutdown()
	{
		if (current != null)
		{
			current.cancelExpiry();
		}
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

	/**
	 * Sync and character feedback share the row on equal terms: a message
	 * only lands while the row is free or already its owner's, so lookup text
	 * is never stomped. Progress speaks in k1; notices and results stay dim.
	 * An auto-clearing message expires on its own timer, and that expiry
	 * clears nothing else. Any thread.
	 */
	private void showFeedback(Owner owner, Kind kind, String text, boolean autoClear)
	{
		runFeedbackOnEdt(() ->
		{
			if (!free() && !ownedBy(owner))
			{
				return;
			}
			if (owner == Owner.SYNC)
			{
				clearSyncSuccessGlow();
			}
			else
			{
				clearCharacterSuccessGlow();
			}
			StatusMessage message = new StatusMessage(owner, kind, text,
				kind == Kind.PROGRESS ? SYNC_K1 : textDim);
			show(message);
			if (autoClear)
			{
				message.expireAfter(owner == Owner.SYNC ? SYNC_EXPIRY_MS : CHARACTER_EXPIRY_MS, () ->
				{
					if (current == message)
					{
						clear();
					}
				});
			}
		});
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
	 * Successful sync feedback is icon-only so the shared status row never
	 * moves. It flashes over a blank row or the sync's own text, never over
	 * lookup or character text.
	 */
	private void flashSyncSuccess()
	{
		runFeedbackOnEdt(() ->
		{
			if (!syncArrowEnabled || !syncArrowHasData
				|| current != null && !ownedBy(Owner.SYNC))
			{
				return;
			}
			stopSyncSuccessGlowTimer();
			if (ownedBy(Owner.SYNC))
			{
				clear();
			}
			syncedGlow = true;
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
			|| !free() && !ownedBy(Owner.CHARACTER))
		{
			return;
		}
		clearCharacterSuccessGlow();
		if (ownedBy(Owner.CHARACTER))
		{
			clear();
		}
		characterPublishedGlow = true;
		refreshCharacterIcon(characterHovered);
		characterSuccessGlowTimer = new Timer(2500, e -> clearCharacterSuccessGlow());
		characterSuccessGlowTimer.setRepeats(false);
		characterSuccessGlowTimer.start();
	}
}
