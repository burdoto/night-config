package com.electronwill.nightconfig.json;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.SimpleConfig;
import com.electronwill.nightconfig.core.serialization.CharacterOutput;
import com.electronwill.nightconfig.core.serialization.FileConfig;
import com.electronwill.nightconfig.core.serialization.WriterOutput;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author TheElectronWill
 */
public class JsonConfigTest {
	private final FileConfig config = new JsonConfig();

	{
		Config config2 = new SimpleConfig();
		config2.setBoolean("boolean", true);
		config2.setBoolean("false", false);

		config.setString("string", "This is a string with a lot of characters to escape \n\r\t \\ \" ");
		config.setInt("int", 123456);
		config.setLong("long", 1234567890l);
		config.setFloat("float", 0.123456f);
		config.setDouble("double", 0.123456d);
		config.setConfig("config", config2);
		config.setList("list", Arrays.asList("a", "b", 3, null, true, false, 17.5));
		config.setValue("null", null);
	}

	private final File file = new File("test.json");

	@Test
	public void testWrite() throws IOException {
		config.writeTo(file);
	}

	@Test
	public void testRead() throws IOException {
		config.readFrom(file);
		System.out.println(config);
	}

	@Test
	public void testFancyWriter() throws IOException{
		try (Writer fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
			CharacterOutput output = new WriterOutput(fileWriter);
			FancyJsonWriter jsonWriter = new FancyJsonWriter.Builder().build(output);
			jsonWriter.writeJsonObject(config);
		}//finally closes the writer
	}
}
