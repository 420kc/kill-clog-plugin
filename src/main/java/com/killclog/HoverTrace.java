package com.killclog;

import java.awt.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;

/** Debug only (hb/hover-trace, never released): records card opens and closes, and logs them when two cards show. */
@Slf4j
final class HoverTrace
{
	private static final int KEEP = 80;
	private static final Deque<String> RECENT = new ArrayDeque<>();
	private static final Set<JToolTip> TIPS = Collections.newSetFromMap(new WeakHashMap<>());
	private static Timer watchdog;
	private static int lastShowing;

	private HoverTrace()
	{
	}

	static synchronized void note(String what)
	{
		RECENT.addLast(String.format("%tT.%<tL %s", System.currentTimeMillis(), what));
		while (RECENT.size() > KEEP)
		{
			RECENT.removeFirst();
		}
	}

	static void card(JToolTip tip, Component owner)
	{
		TIPS.add(tip);
		note("card made " + id(tip) + " for " + describe(owner) + " by " + caller());
	}

	static void start()
	{
		if (watchdog == null)
		{
			watchdog = new Timer(250, e -> check());
			watchdog.start();
			log.info("HOVER TRACE: watching for two cards at once");
		}
	}

	private static void check()
	{
		List<JToolTip> showing = new ArrayList<>();
		for (JToolTip tip : new ArrayList<>(TIPS))
		{
			if (tip.isShowing())
			{
				showing.add(tip);
			}
		}
		if (showing.size() > 1 && lastShowing <= 1)
		{
			StringBuilder out = new StringBuilder();
			for (JToolTip tip : showing)
			{
				out.append("\n  showing ").append(id(tip)).append(" for ").append(describe(tip.getComponent()))
					.append(" at ").append(tip.getLocationOnScreen());
			}
			synchronized (HoverTrace.class)
			{
				for (String line : RECENT)
				{
					out.append("\n  ").append(line);
				}
			}
			log.warn("HOVER TRACE: {} cards showing{}", showing.size(), out);
		}
		lastShowing = showing.size();
	}

	static String id(Object o)
	{
		return o == null ? "null" : Integer.toHexString(System.identityHashCode(o));
	}

	static String describe(Component c)
	{
		if (c == null)
		{
			return "null";
		}
		String text = c instanceof JLabel ? ((JLabel) c).getText() : "";
		String tip = c instanceof JComponent ? ((JComponent) c).getToolTipText() : null;
		return c.getClass().getSimpleName() + "@" + id(c) + "[" + (text == null ? "" : text.trim()) + "|"
			+ (tip == null ? "no tip" : "tip") + "]";
	}

	static String caller()
	{
		StringBuilder out = new StringBuilder();
		int kept = 0;
		for (StackTraceElement e : new Throwable().getStackTrace())
		{
			String cls = e.getClassName();
			if (cls.endsWith("HoverTrace") || cls.startsWith("java.awt.EventDispatchThread")
				|| cls.startsWith("java.awt.EventQueue"))
			{
				continue;
			}
			out.append(cls.replace("com.killclog.", "").replace("javax.swing.", ""))
				.append('.').append(e.getMethodName()).append(':').append(e.getLineNumber()).append(' ');
			if (++kept == 7)
			{
				break;
			}
		}
		return out.toString().trim();
	}
}
