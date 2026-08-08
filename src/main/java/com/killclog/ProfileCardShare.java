package com.killclog;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/** Clipboard-first export with a PNG keepsake under RuneLite screenshots. */
@Slf4j
final class ProfileCardShare
{
	private static final File CARD_DIR =
		new File(RuneLite.RUNELITE_DIR, "screenshots/kill-clog");

	private ProfileCardShare()
	{
	}

	static final class Export
	{
		final boolean copied;
		final File saved;

		private Export(boolean copied, File saved)
		{
			this.copied = copied;
			this.saved = saved;
		}
	}

	static Export export(BufferedImage card, String rsn)
	{
		return new Export(copyToClipboard(card), saveToDisk(card, rsn));
	}

	private static boolean copyToClipboard(BufferedImage card)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new ImageTransferable(card), null);
			return true;
		}
		catch (Exception e)
		{
			log.warn("profile card clipboard copy failed", e);
			return false;
		}
	}

	private static File saveToDisk(BufferedImage card, String rsn)
	{
		try
		{
			if (!CARD_DIR.exists() && !CARD_DIR.mkdirs())
			{
				return null;
			}
			String stamp = LocalDateTime.now()
				.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			File out = new File(CARD_DIR, sanitize(rsn) + "-" + stamp + ".png");
			return ImageIO.write(card, "png", out) ? out : null;
		}
		catch (Exception e)
		{
			log.warn("profile card save failed", e);
			return null;
		}
	}

	static String sanitize(String rsn)
	{
		if (rsn == null || rsn.isBlank())
		{
			return "profile";
		}
		String safe = rsn.toLowerCase(java.util.Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-+|-+$)", "");
		return safe.isEmpty() ? "profile" : safe;
	}

	private static final class ImageTransferable implements Transferable
	{
		private final BufferedImage image;

		private ImageTransferable(BufferedImage image)
		{
			this.image = image;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors()
		{
			return new DataFlavor[]{DataFlavor.imageFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			return DataFlavor.imageFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
		{
			if (!isDataFlavorSupported(flavor))
			{
				throw new UnsupportedFlavorException(flavor);
			}
			return image;
		}
	}
}
