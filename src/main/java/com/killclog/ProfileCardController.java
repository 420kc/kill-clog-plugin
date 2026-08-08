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

/** One-click card pipeline: snapshot, await cached sprites, render, export, preview. */
@Slf4j
final class ProfileCardController
{
	private static final int SPRITE_WAIT_MS = 900;

	private final JComponent owner;
	private final Supplier<ProfileCard.Data> dataSupplier;
	private final ProfileCardLocalCapture localCapture;
	private final Consumer<String> status;
	private final ProfileCardPreview preview = new ProfileCardPreview();
	private final AtomicInteger generation = new AtomicInteger();
	private boolean busy;

	ProfileCardController(JComponent owner, Supplier<ProfileCard.Data> dataSupplier,
		ProfileCardLocalCapture localCapture, Consumer<String> status)
	{
		this.owner = owner;
		this.dataSupplier = dataSupplier;
		this.localCapture = localCapture;
		this.status = status;
	}

	void share()
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
				awaitSprites(data, () -> renderAndExport(data, operation));
			}));
	}

	private void awaitSprites(ProfileCard.Data data, Runnable ready)
	{
		Set<AsyncBufferedImage> pending = Collections.newSetFromMap(new IdentityHashMap<>());
		addPending(pending, data.accountIcon);
		addPending(pending, data.pluginIcon);
		addPending(pending, data.tierIcon);
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

	private void renderAndExport(ProfileCard.Data data, int operation)
	{
		if (operation != generation.get())
		{
			return;
		}
		try
		{
			BufferedImage card = ProfileCard.render(data);
			ProfileCardShare.Export export = ProfileCardShare.export(card, data.rsn);
			if (!export.copied && export.saved == null)
			{
				status.accept("Profile card failed");
				return;
			}
			if (export.copied && export.saved != null)
			{
				status.accept("Profile card copied");
			}
			else if (export.copied)
			{
				status.accept("Copied - file save failed");
			}
			else
			{
				status.accept("Saved - clipboard unavailable");
			}
			try
			{
				preview.show(owner, card, export);
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
