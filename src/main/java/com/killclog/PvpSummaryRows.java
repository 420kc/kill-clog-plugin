package com.killclog;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import lombok.Setter;

/**
 * The five PvP activities as rows inside the Combat Summary: icon, label,
 * score, and collection progress where the activity has a log.
 */
final class PvpSummaryRows
{
	private static final int ICON_SIZE = 13;
	private static final int ICON_GAP = 4;
	private static final int COL_GAP = 6;

	// Short names for PanelData.PVP_ACTIVITIES, in its order.
	private static final String[] LABELS = {
		"LMS", "Soul Wars", "PvP Arena", "BH Hunter", "BH Rogue",
	};

	private final int[] scores = new int[LABELS.length];
	private final int[] obtained = new int[LABELS.length];
	private final int[] total = new int[LABELS.length];

	@Setter
	private BufferedImage[] icons;

	/** A null result renders all five rows with "--" scores: the empty state. */
	void setData(HiscoreResult hiscoreResult, ClogResult clogResult)
	{
		Arrays.fill(scores, 0);
		Arrays.fill(obtained, -1);
		if (hiscoreResult == null)
		{
			return;
		}

		for (int i = 0; i < LABELS.length; i++)
		{
			scores[i] = hiscoreResult.getActivityScore(PanelData.PVP_ACTIVITIES[i].getName());
		}

		if (clogResult != null)
		{
			setProgress(0, ClogHelper.clogCounts("last_man_standing", clogResult));
			setProgress(1, ClogHelper.clogCounts("soul_wars", clogResult));
		}
	}

	private void setProgress(int row, int[] counts)
	{
		if (counts != null)
		{
			obtained[row] = counts[0];
			total[row] = counts[1];
		}
	}

	/** The rows as one table part: progress only rides alongside a real score; a "--" row stays dash-only. */
	CardBody.Part part()
	{
		int[] shown = new int[LABELS.length];
		for (int i = 0; i < LABELS.length; i++)
		{
			shown[i] = scores[i] > 0 ? obtained[i] : -1;
		}
		return CardBody.table(icons, LABELS, scores, shown, total, null, ICON_SIZE, ICON_GAP, COL_GAP);
	}
}
