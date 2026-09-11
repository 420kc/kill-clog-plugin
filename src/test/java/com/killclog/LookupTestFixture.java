package com.killclog;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/** Controlled service completions exercise the actual EDT transport and controllers. */
final class LookupTestFixture
{
	final Map<String, CompletableFuture<HiscoreResult>> hiscores = new HashMap<>();
	final Map<String, CompletableFuture<ClogResult>> clogs = new HashMap<>();
	final Map<String, CompletableFuture<CombatAchievementResult>> cas = new HashMap<>();
	final Map<String, Integer> events = new HashMap<>();
	final KillClogConfig config = new KillClogConfig()
	{
	};
	final HiscoreService hiscoreService = new HiscoreService(null, null)
	{
		@Override
		public CompletableFuture<HiscoreResult> lookup(String player, AccountType type)
		{
			return hiscores.computeIfAbsent(player, ignored -> new CompletableFuture<>());
		}
	};
	final ClogService clogService = new ClogService(null, null, null)
	{
		@Override
		public ClogResult getCachedResult(String player)
		{
			return null;
		}

		@Override
		public CompletableFuture<ClogResult> lookup(String player)
		{
			return clogs.computeIfAbsent(player, ignored -> new CompletableFuture<>());
		}
	};
	final RuneProfileService runeProfile = new RuneProfileService(null, null, null)
	{
		@Override
		public CompletableFuture<CombatAchievementResult> lookup(String player)
		{
			return cas.computeIfAbsent(player, ignored -> new CompletableFuture<>());
		}

		@Override
		public CompletableFuture<ClogResult> lookupClog(String player)
		{
			return CompletableFuture.completedFuture(null);
		}
	};
	final KillclogService killclog = new KillclogService(null, null, null)
	{
		@Override
		public CompletableFuture<ClogResult> lookupClog(String player)
		{
			return CompletableFuture.completedFuture(null);
		}
	};
	final LookupSession primary = new LookupSession(hiscoreService, clogService,
		runeProfile, killclog, config, null, listener(LookupSession.Listener.class));
	final ComparisonController comparison = new ComparisonController(hiscoreService, clogService,
		runeProfile, killclog, primary, config, null, null, listener(ComparisonController.Listener.class))
	{
		@Override
		public void rebuildTooltipData()
		{
			// Painting is covered by the real two-card tooltip fixtures.
		}
	};

	LookupTestFixture() throws Exception
	{
		edt(() ->
		{
			primary.adoptState(hiscore(1), clog("Blue"), ca(1), "Blue");
			comparison.setRenderTarget((ComparisonController.CellRenderTarget) Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[]{ComparisonController.CellRenderTarget.class},
				(proxy, method, args) -> method.getName().equals("playerName") ? new JLabel("Blue") : null));
		});
	}

	<T> T listener(Class<T> type)
	{
		return type.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
			(proxy, method, args) ->
			{
				if (!SwingUtilities.isEventDispatchThread()) throw new AssertionError("callback outside EDT");
				events.merge(method.getName(), 1, Integer::sum);
				return null;
			}));
	}

	int events(String event)
	{
		return events.getOrDefault(event, 0);
	}

	static void edt(Runnable action) throws Exception
	{
		SwingUtilities.invokeAndWait(action);
	}

	static HiscoreResult hiscore(int marker)
	{
		return new HiscoreResult(AccountType.REGULAR, Map.of("Zulrah", marker),
			Map.of(), Map.of(), Map.of(), Map.of(), marker, marker, marker, marker);
	}

	static ClogResult clog(String player)
	{
		return new ClogResult(player, Map.of(), Map.of(), Map.of(), null, null);
	}

	static CombatAchievementResult ca(int points)
	{
		return CombatAchievementResult.of(Map.of(CombatAchievementTier.EASY, points), Map.of());
	}
}
