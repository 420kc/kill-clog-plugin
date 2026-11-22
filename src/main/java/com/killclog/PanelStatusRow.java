package com.killclog;

import com.killclog.StatusMessage.Kind;
import com.killclog.StatusMessage.Owner;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.function.*;
import javax.swing.*;
import lombok.*;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

/**
 * The status line above the search bar, and the killclog.com controls that share it. Whatever holds the line is a
 * {@link StatusMessage}: lookup text comes through {@link #setSearchStatus}, sync and character feedback through the
 * show and reset methods. Feedback lands only while the line is free or already its owner's, and an expiry clears only
 * the message it was started for. Widgets are touched on the Swing thread only, and the setters post themselves there,
 * so the plugin calls them from any thread.
 */
final class PanelStatusRow
{
	private static final String SYNC_HOVER_TEXT = "sync collection log";
	private static final String SYNC_FAILURE_HOVER_TEXT = "sync failed - click to retry";
	private static final String CHARACTER_HOVER_TEXT = "publish character";
	private static final String CHARACTER_DELETE_HOVER_TEXT = "delete character model";
	private static final String CHARACTER_FAILURE_HOVER_TEXT = "character failed - click to retry";
	private static final String CHARACTER_DELETE_FAILURE_HOVER_TEXT = "delete failed - click to retry";
	private static final String LOCKED_HOVER_TEXT = "Unavailable until setup";
	private static final int SYNC_EXPIRY_MS = 2500;
	private static final int CHARACTER_EXPIRY_MS = 3000;
	// k1: the brand lime. Status chrome, not data coloring, so it does not
	// route through the user-themable completion color.
	private static final Color SYNC_K1 = new Color(78, 240, 21);
	private static final ImageIcon SYNC_CHALICE_DIM = chaliceTinted(0.45f, null);
	private static final ImageIcon SYNC_CHALICE_LIT = chaliceTinted(1f, null);
	private static final ImageIcon SYNC_CHALICE_SYNCED = chaliceTinted(1f, SYNC_K1);
	// Locked: grey and fainter than at rest, and hovering never lights it.
	private static final Color LOCKED_GREY = new Color(150, 150, 150);
	private static final ImageIcon SYNC_CHALICE_LOCKED = chaliceTinted(0.35f, LOCKED_GREY);

	private final JPanel row;
	private final JLabel searchStatus = new JLabel(" ");
	private final JLabel syncArrow = new JLabel();
	private final JLabel characterPublish = new JLabel();
	private final Color textDim;
	private final SpriteManager spriteManager;
	private final TooltipController tooltipController;
	private final ControlFeedback syncFeedback;
	private final ControlFeedback characterFeedback;
	private String characterNoticeText;
	private String characterNoticeDetail;
	/** What the row shows now; null while blank. */
	private StatusMessage current;

	/**
	 * Whether this account's own log lets the chalice and the character work: hidden while nobody is logged in or
	 * the log is still loading, locked (grey, with a hover line and a chat explanation) until it's set up, ready
	 * after, an empty log included.
	 */
	enum Controls
	{
		HIDDEN,
		LOCKED,
		READY
	}

	private boolean syncArrowEnabled;
	private Controls controls = Controls.HIDDEN;
	// A click on either control while locked: the chat explains, nothing is sent.
	@Setter(AccessLevel.PACKAGE)
	private Runnable lockedHandler;
	private boolean characterPublishEnabled;
	@Setter(AccessLevel.PACKAGE)
	private Runnable killclogSyncHandler;
	@Setter(AccessLevel.PACKAGE)
	private Runnable characterPublishHandler;
	// Shift-click on the character deletes it from killclog.com; while shift is held on it, its hover line says so.
	@Setter(AccessLevel.PACKAGE)
	private Runnable characterDeleteHandler;
	private boolean characterShift;
	// Whether the character's last request was a delete, so its failure is offered back on shift, not on a plain click.
	private boolean characterDeleteRequest;
	private BufferedImage characterBase;
	private boolean syncChaliceHovered;
	// Each control's success glow: the chalice's after a sync, the character's after a publish.
	private final Glow syncGlow = new Glow(() -> refreshSyncChalice(this.syncChaliceHovered));
	private final Glow characterGlow = new Glow(() -> refreshCharacterIcon(this.characterHovered));
	private boolean characterHovered;

	PanelStatusRow(KillClogConfig config, SpriteManager spriteManager,
		TooltipController tooltipController, Color textDim)
	{
		this.spriteManager = spriteManager;
		this.tooltipController = tooltipController;
		this.textDim = textDim;
		this.syncFeedback = new ControlFeedback(config,
			(kind, text, autoClear) -> showFeedback(Owner.SYNC, kind, text, autoClear),
			this::flashSyncSuccess, "sync failed");
		this.characterFeedback = new ControlFeedback(config,
			(kind, text, autoClear) -> showFeedback(Owner.CHARACTER, kind, text, autoClear),
			this::flashCharacterSuccess, CharacterStatus.FAILED.text);
		PanelSearchBox.configureStatus(searchStatus, textDim);
		this.row = build();
	}

	JPanel component()
	{
		return row;
	}

	/**
	 * Lookup text from the panel, which decides how long it stays: text holds the line until the panel replaces it, and
	 * blank gives the line back. The controls show only while the line is free, so a search in progress or a player not
	 * found hides them.
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

	/** Blank hands the row back to whoever writes next. */
	void clearSearchStatus()
	{
		clear();
	}

	/** Lookup text that clears itself after the delay, unless something else has replaced it by then. */
	void flashSearchStatus(String text, Color color, int expiryMs)
	{
		StatusMessage message = new StatusMessage(Owner.LOOKUP, Kind.RESULT, text, color);
		show(message);
		expireIfCurrent(message, expiryMs);
	}

	/** A shift-click copied the card it opened, or the clipboard would not take it. */
	void flashCardCopy(boolean copied)
	{
		flashSearchStatus(copied ? "card copied!" : "card copy failed", copied ? SYNC_K1 : KillClogColors.NOT_FOUND,
			CHARACTER_EXPIRY_MS);
	}

	/** The rank board's notice, which only the board clears ({@link #clearBoardStatus}). */
	void setBoardStatus(String text)
	{
		show(new StatusMessage(Owner.BOARD, Kind.RESULT, text, textDim));
	}

	void clearBoardStatus()
	{
		if (ownedBy(Owner.BOARD))
		{
			clear();
		}
	}

	private void expireIfCurrent(StatusMessage message, int expiryMs)
	{
		message.expireAfter(expiryMs, () ->
		{
			if (current == message)
			{
				clear();
			}
		});
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
		refreshControlsVisibility();
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
		refreshControlsVisibility();
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

	/**
	 * The chalice's and the character's mouse: hovering lights the control, puts its tooltip up and its hover line
	 * in the status, leaving takes them down, and a left press runs its handler while it shows.
	 */
	private MouseAdapter controlMouse(JLabel control, Owner owner, Consumer<Boolean> hover,
		Supplier<String> tooltip, Supplier<String> hoverText, Supplier<Runnable> handler)
	{
		return new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (control.isVisible())
				{
					hover.accept(true);
					tooltipController.setTooltipText(control, tooltip.get());
					show(new StatusMessage(owner, Kind.HOVER, hoverText.get(), hoverColor()));
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover.accept(false);
				tooltipController.setTooltipText(control, null);
				clearYielding(owner);
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				Runnable run = handler.get();
				if (SwingUtilities.isLeftMouseButton(e) && control.isVisible() && run != null)
				{
					run.run();
				}
			}
		};
	}

	/** What a click here does now: a failure is offered back only on the action that failed. */
	private String characterHoverText()
	{
		if (controls == Controls.LOCKED)
		{
			return LOCKED_HOVER_TEXT;
		}
		if (characterShift)
		{
			return characterDeleteRequest
				? characterFeedback.hoverText(CHARACTER_DELETE_HOVER_TEXT, CHARACTER_DELETE_FAILURE_HOVER_TEXT)
				: CHARACTER_DELETE_HOVER_TEXT;
		}
		return characterNoticeText != null ? characterNoticeText : characterDeleteRequest ? CHARACTER_HOVER_TEXT
			: characterFeedback.hoverText(CHARACTER_HOVER_TEXT, CHARACTER_FAILURE_HOVER_TEXT);
	}

	/** A control's hover line: k1 green for what a click does, the not-found red while it is locked. */
	private Color hoverColor()
	{
		return controls == Controls.LOCKED ? KillClogColors.NOT_FOUND : SYNC_K1;
	}

	/** Shift held or let go on the character, as the pointer's last event there says: its hover line follows. */
	private void setCharacterShift(boolean down)
	{
		if (down == characterShift)
		{
			return;
		}
		characterShift = down;
		if (characterHovered && ownedBy(Owner.CHARACTER) && current.kind == Kind.HOVER)
		{
			show(new StatusMessage(Owner.CHARACTER, Kind.HOVER, characterHoverText(), hoverColor()));
		}
	}

	/** The character control's notice, failure, hover and glow, gone. */
	private void clearCharacterState()
	{
		characterFeedback.reset();
		characterNoticeText = null;
		characterNoticeDetail = null;
		characterHovered = false;
		characterDeleteRequest = false;
		characterGlow.clear();
	}

	private JPanel build()
	{
		syncArrow.setIcon(SYNC_CHALICE_DIM);
		tooltipController.trackTooltipComponent(syncArrow);
		tooltipController.trackTooltipComponent(characterPublish);
		characterPublish.setBorder(BorderFactory.createEmptyBorder(0, 3, 6, 2));
		characterPublish.setVerticalAlignment(JLabel.CENTER);
		characterPublish.setVisible(false);
		// Added first, so shift is known before the hover line and the press are read.
		characterPublish.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				characterShift = e.isShiftDown();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				characterShift = false;
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				characterShift = e.isShiftDown();
			}
		});
		characterPublish.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				setCharacterShift(e.isShiftDown());
			}
		});
		characterPublish.addMouseListener(controlMouse(characterPublish, Owner.CHARACTER, hovered ->
		{
			characterHovered = hovered;
			refreshCharacterIcon(hovered);
		}, () -> controls == Controls.LOCKED ? null : characterTooltipText(characterNoticeDetail != null
			? characterNoticeDetail : characterFeedback.lastFailure()), this::characterHoverText,
			() -> controls == Controls.LOCKED ? lockedHandler
				: characterShift ? characterDeleteHandler : characterPublishHandler));
		requestCharacterIcon();

		// Centered in the band between the panel's top and the search bar: the status row is taller than that band, so
		// the bottom inset lifts the icon to the band's middle.
		syncArrow.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 5));
		syncArrow.setVerticalAlignment(JLabel.CENTER);
		syncArrow.setVisible(false);
		syncArrow.addMouseListener(controlMouse(syncArrow, Owner.SYNC, hovered ->
		{
			syncChaliceHovered = hovered;
			refreshSyncChalice(hovered);
		}, () -> controls == Controls.LOCKED ? null : syncFeedback.lastFailure(),
			() -> controls == Controls.LOCKED ? LOCKED_HOVER_TEXT
				: syncFeedback.hoverText(SYNC_HOVER_TEXT, SYNC_FAILURE_HOVER_TEXT),
			() -> controls == Controls.LOCKED ? lockedHandler : killclogSyncHandler));

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
	 * The sync control is the Kill Clog chalice itself: dim at rest, full red under the cursor, and k1 green for a
	 * moment after a sync succeeds.
	 */
	private static BufferedImage chaliceBase()
	{
		BufferedImage src = KillClogIcons.pluginIcon();
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
		return new ImageIcon(KillClogIcons.recolor(base, argb ->
		{
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
			int na = Math.min(255, Math.round((argb >>> 24) * alpha));
			return (na << 24) | (r << 16) | (gch << 8) | b;
		}));
	}

	private void refreshSyncChalice(boolean hovered)
	{
		syncArrow.setIcon(controls == Controls.LOCKED ? SYNC_CHALICE_LOCKED : syncGlow.lit() ? SYNC_CHALICE_SYNCED
			: hovered ? SYNC_CHALICE_LIT : SYNC_CHALICE_DIM);
	}

	/** Any thread; also re-run after a sprite reload so a resource pack swap redraws the icon. */
	void requestCharacterIcon()
	{
		spriteManager.getSpriteAsync(SpriteID.SideiconsNew.EQUIPMENT, 0, sprite ->
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
				refreshControlsVisibility();
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
		boolean locked = controls == Controls.LOCKED;
		characterPublish.setIcon(imageTinted(characterBase,
			locked ? 0.35f : characterGlow.lit() || hovered ? 1f : 0.45f,
			locked ? LOCKED_GREY : characterGlow.lit() ? SYNC_K1 : null));
	}

	private void refreshControlsVisibility()
	{
		boolean visible = controls != Controls.HIDDEN && free();
		syncArrow.setVisible(syncArrowEnabled && visible);
		characterPublish.setVisible(characterPublishEnabled && characterBase != null && visible);
	}

	private static String characterTooltipText(String detail)
	{
		if (detail == null) return null;
		return "<html><div style='width:220px'>" + detail.replace("&", "&amp;")
			.replace("<", "&lt;").replace(">", "&gt;") + "</div></html>";
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
				syncGlow.clear();
			}
			refreshControlsVisibility();
		});
	}

	/**
	 * The chalice and the character earn their use: locked until this player's local Collection Log holds at least
	 * one capture of their own, so a fresh account sees why it cannot sync yet and can never send nothing.
	 */
	void setControls(Controls state)
	{
		SwingUtilities.invokeLater(() ->
		{
			controls = state;
			if (state != Controls.READY)
			{
				syncChaliceHovered = false;
				syncGlow.clear();
			}
			refreshSyncChalice(syncChaliceHovered);
			refreshCharacterIcon(characterHovered);
			refreshControlsVisibility();
		});
	}

	void setCharacterPublishEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() ->
		{
			characterPublishEnabled = enabled;
			if (!enabled)
			{
				clearCharacterState();
				tooltipController.setTooltipText(characterPublish, null);
			}
			refreshControlsVisibility();
		});
	}

	void showSyncProgress(boolean manual, String text, boolean autoClear)
	{
		syncFeedback.progress(manual, text, autoClear);
	}

	/** A result that shows nothing still keeps a failure for the control's next hover to say. */
	void showSyncResult(boolean manual, boolean ok, String message)
	{
		syncFeedback.complete(manual, ok, message);
	}

	/**
	 * Drop the account's sync feedback, leaving lookup text and character status alone: only a message the sync owns
	 * goes, and its expiry with it.
	 */
	void resetSyncFeedback()
	{
		SwingUtilities.invokeLater(() ->
		{
			syncFeedback.reset();
			tooltipController.setTooltipText(syncArrow, null);
			syncChaliceHovered = false;
			syncGlow.clear();
			if (ownedBy(Owner.SYNC))
			{
				clear();
			}
		});
	}

	/**
	 * The status carries its meaning: CLEAR withdraws the character's message,
	 * PUBLISHED or FAILED completes the request, and the rest show as their
	 * kind, a notice repeating its detail on hover.
	 */
	void showCharacterPublishStatus(CharacterStatus status, boolean autoClear, String detail)
	{
		runFeedbackOnEdt(() ->
		{
			characterNoticeText = null;
			characterNoticeDetail = null;
			if (status == CharacterStatus.CLEAR)
			{
				characterFeedback.reset();
				tooltipController.setTooltipText(characterPublish, null);
				characterGlow.clear();
				if (ownedBy(Owner.CHARACTER))
				{
					clear();
				}
			}
			else if (status.kind == null)
			{
				characterFeedback.complete(true, status == CharacterStatus.PUBLISHED, detail != null
					? detail : "Character failed. Click to retry.");
			}
			else
			{
				characterFeedback.reset();
				if (status.kind == Kind.PROGRESS)
				{
					characterDeleteRequest = status == CharacterStatus.DELETING;
				}
				if (status.kind == Kind.NOTICE)
				{
					characterNoticeText = (status == CharacterStatus.PENDING ? CharacterStatus.UNKNOWN : status).text;
					characterNoticeDetail = detail;
				}
				characterFeedback.show(true, status.kind, status.text, autoClear);
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
			if (ownedBy(Owner.SYNC) || ownedBy(Owner.CHARACTER))
			{
				clear();
			}
			syncGlow.clear();
			characterGlow.clear();
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
		controls = Controls.HIDDEN;
		syncChaliceHovered = false;
		syncFeedback.reset();
		characterShift = false;
		clearCharacterState();
		syncGlow.clear();
		refreshControlsVisibility();
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
	 * Sync and character feedback share the line as equals: a message lands only while the line is free or already its
	 * owner's, so lookup text is never written over. Progress is in k1; notices and results stay dim. A message that
	 * clears itself does so on its own timer, which clears nothing else. Any thread.
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
				syncGlow.clear();
			}
			else
			{
				characterGlow.clear();
			}
			StatusMessage message = new StatusMessage(owner, kind, text,
				kind == Kind.PROGRESS ? SYNC_K1 : textDim);
			show(message);
			if (autoClear)
			{
				expireIfCurrent(message, owner == Owner.SYNC ? SYNC_EXPIRY_MS : CHARACTER_EXPIRY_MS);
			}
		});
	}

	/**
	 * A sync that succeeds shows on the icon alone, so the shared line never moves. It flashes over an empty line or
	 * the sync's own text, never over lookup or character text.
	 */
	private void flashSyncSuccess()
	{
		runFeedbackOnEdt(() ->
		{
			if (!syncArrowEnabled || controls != Controls.READY
				|| current != null && !ownedBy(Owner.SYNC))
			{
				return;
			}
			if (ownedBy(Owner.SYNC))
			{
				clear();
			}
			syncGlow.flash();
		});
	}

	private void flashCharacterSuccess()
	{
		if (!characterPublishEnabled || controls != Controls.READY
			|| !free() && !ownedBy(Owner.CHARACTER))
		{
			return;
		}
		if (ownedBy(Owner.CHARACTER))
		{
			clear();
		}
		characterGlow.flash();
	}

	/** A control's success glow: lit at once, out again on its own after 2.5 s, its icon redrawn each way. */
	private static final class Glow
	{
		private final Runnable redraw;
		private Timer timer;
		private boolean lit;

		Glow(Runnable redraw)
		{
			this.redraw = redraw;
		}

		boolean lit()
		{
			return lit;
		}

		void flash()
		{
			stop();
			lit = true;
			redraw.run();
			timer = new Timer(2500, e -> clear());
			timer.setRepeats(false);
			timer.start();
		}

		void clear()
		{
			stop();
			lit = false;
			redraw.run();
		}

		private void stop()
		{
			if (timer != null)
			{
				timer.stop();
				timer = null;
			}
		}
	}
}
