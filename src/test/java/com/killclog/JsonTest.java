package com.killclog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import org.junit.Test;

/** A site's odd field reads as absent, never an exception: the rules every provider parser now shares. */
public class JsonTest
{
	private static final JsonObject ROW = new Gson().fromJson(
		"{\"n\":7,\"s\":\"text\",\"nil\":null,\"obj\":{\"x\":1},\"arr\":[{\"a\":1},2,\"b\",{\"a\":2}],\"num\":\"3\"}",
		JsonObject.class);

	@Test
	public void missingNullAndWrongShapesReadAsAbsent()
	{
		assertEquals(7, Json.intField(ROW, "n"));
		assertEquals(0, Json.intField(ROW, "missing"));
		assertEquals(0, Json.intField(ROW, "nil"));
		assertEquals(0, Json.intField(ROW, "obj"));
		assertEquals(0, Json.intField(null, "n"));
		assertEquals("text", Json.stringField(ROW, "s"));
		assertEquals("7", Json.stringField(ROW, "n"));
		assertNull(Json.stringField(ROW, "obj"));
		assertNull(Json.stringField(ROW, "nil"));

		assertTrue(Json.present(ROW, "obj"));
		assertFalse(Json.present(ROW, "nil"));
		assertFalse(Json.present(ROW, "missing"));

		assertEquals(1, Json.intField(Json.object(ROW, "obj"), "x"));
		assertNull(Json.object(ROW, "arr"));
		assertNull(Json.array(ROW, "obj"));
		assertNull(Json.object(null, "obj"));
	}

	@Test
	public void anArraysObjectsSkipEverythingElse()
	{
		assertEquals(2, Json.objects(ROW.get("arr")).size());
		assertEquals(2, Json.intField(Json.objects(ROW.get("arr")).get(1), "a"));
		assertTrue(Json.objects(ROW.get("obj")).isEmpty());
		assertTrue(Json.objects(null).isEmpty());
		assertEquals(1, Json.entries(ROW.get("obj")).size());
		assertTrue(Json.entries(ROW.get("arr")).isEmpty());
	}

	@Test
	public void onlyJsonNumbersAreNumbers()
	{
		assertEquals(7, Json.number(ROW.get("n")), 0);
		assertEquals(-1, Json.number(ROW.get("num")), 0);
		assertEquals(-1, Json.number(ROW.get("nil")), 0);
		assertEquals(-1, Json.number(null), 0);
	}
}
