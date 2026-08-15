package com.killclog;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.AsyncBufferedImage;

/** Local identity-card pipeline: snapshot, await cached sprites, render, preview. */
@Slf4j
final class ProfileCardController
{
	private static final int SPRITE_WAIT_MS = 900;

	private final JComponent owner;
	private final Supplier<ProfileCard.Data> dataSupplier;
	private final ProfileCardLocalCapture localCapture;
	private final Consumer<String> status;
	private final Runnable syncAction;
	private final ProfileCardPreview preview = new ProfileCardPreview();
	private final AtomicInteger generation = new AtomicInteger();
	private ProfileCard.Data openData;
	private boolean busy;

	ProfileCardController(JComponent owner, Supplier<ProfileCard.Data> dataSupplier,
		ProfileCardLocalCapture localCapture, Consumer<String> status,
		Runnable syncAction)
	{
		this.owner = owner;
		this.dataSupplier = dataSupplier;
		this.localCapture = localCapture;
		this.status = status;
		this.syncAction = syncAction;
	}

	void open()
	{
		if (busy)
		{
			return;
		}
		ProfileCard.Data data;
		try
		{
			data = dataSupplier.get();
		}
		catch (RuntimeException e)
		{
			log.warn("profile card snapshot failed", e);
			status.accept("Profile card failed");
			return;
		}
		if (data == null)
		{
			status.accept("Look up your player first");
			return;
		}
		busy = true;
		int operation = generation.incrementAndGet();
		status.accept("building profile card...");
		localCapture.capture(data.rsn, snapshot -> finishLocalCapture(data, snapshot, operation));
	}

	void closePreview()
	{
		generation.incrementAndGet();
		busy = false;
		openData = null;
		if (SwingUtilities.isEventDispatchThread())
		{
			preview.close();
		}
		else
		{
			SwingUtilities.invokeLater(preview::close);
		}
	}

	private void finishLocalCapture(ProfileCard.Data data,
		ProfileCardLocalCapture.Snapshot snapshot, int operation)
	{
		if (operation != generation.get())
		{
			return;
		}
		if (snapshot == null)
		{
			busy = false;
			status.accept("Player model isn't ready");
			return;
		}
		data.questPoints = snapshot.questPoints;
		CompletableFuture.supplyAsync(() -> ProfileCardPlayerModel.render(snapshot.playerModel))
			.whenComplete((portrait, error) -> SwingUtilities.invokeLater(() ->
			{
				if (operation != generation.get())
				{
					return;
				}
				if (error != null || portrait == null)
				{
					if (error != null)
					{
						log.warn("profile card player model render failed", error);
					}
					busy = false;
					status.accept("Player model isn't ready");
					return;
				}
				data.playerModel = portrait;
				awaitSprites(data, () -> renderAndPreview(data, operation));
			}));
	}

	void showSyncStatus(String text, boolean ok)
	{
		if (ok && "synced!".equals(text) && openData != null)
		{
			try
			{
				openData.profileUrl = ProfileCardDataBuilder.profileUrl(openData.rsn);
				preview.replaceCard(ProfileCard.render(openData));
			}
			catch (RuntimeException e)
			{
				log.warn("profile card refresh after sync failed", e);
			}
		}
		preview.showSyncStatus(text, ok);
	}

	void showAppearanceStatus(String text, boolean ok)
	{
		preview.showAppearanceStatus(text, ok);
	}

	private void awaitSprites(ProfileCard.Data data, Runnable ready)
	{
		Set<AsyncBufferedImage> pending = Collections.newSetFromMap(new IdentityHashMap<>());
		addPending(pending, data.accountIcon);
		addPending(pending, data.pluginIcon);
		addPending(pending, data.tierIcon);
		if (data.petSprites != null)
		{
			for (BufferedImage image : data.petSprites)
			{
				addPending(pending, image);
			}
		}
		if (data.recentSprites != null)
		{
			for (BufferedImage image : data.recentSprites)
			{
				addPending(pending, image);
			}
		}
		if (pending.isEmpty())
		{
			ready.run();
			return;
		}

		AtomicBoolean finished = new AtomicBoolean();
		AtomicInteger remaining = new AtomicInteger(pending.size());
		Timer timeout = new Timer(SPRITE_WAIT_MS, event -> finishOnce(finished, ready));
		timeout.setRepeats(false);
		timeout.start();
		for (AsyncBufferedImage image : pending)
		{
			image.onLoaded(() ->
			{
				if (remaining.decrementAndGet() == 0)
				{
					SwingUtilities.invokeLater(() ->
					{
						timeout.stop();
						finishOnce(finished, ready);
					});
				}
			});
		}
	}

	private static void addPending(Set<AsyncBufferedImage> pending, BufferedImage image)
	{
		if (image instanceof AsyncBufferedImage)
		{
			pending.add((AsyncBufferedImage) image);
		}
	}

	private static void finishOnce(AtomicBoolean finished, Runnable ready)
	{
		if (finished.compareAndSet(false, true))
		{
			ready.run();
		}
	}

	private void renderAndPreview(ProfileCard.Data data, int operation)
	{
		if (operation != generation.get())
		{
			return;
		}
		try
		{
			BufferedImage card = ProfileCard.render(data);
			try
			{
				openData = data;
				preview.show(owner, card, data.rsn, syncAction, data.profileUrl != null);
				status.accept(" ");
			}
			catch (RuntimeException e)
			{
				log.warn("profile card preview failed", e);
			}
		}
		catch (RuntimeException e)
		{
			log.warn("profile card render failed", e);
			status.accept("Profile card failed");
		}
		finally
		{
			if (operation == generation.get())
			{
				busy = false;
			}
		}
	}
}
