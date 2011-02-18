/*
 * Copyright (c) 2008-2010 XebiaLabs B.V. All rights reserved.
 *
 * Your use of XebiaLabs Software and Documentation is subject to the Personal
 * License Agreement.
 *
 * http://www.xebialabs.com/deployit-personal-edition-license-agreement
 *
 * You are granted a personal license (i) to use the Software for your own
 * personal purposes which may be used in a production environment and/or (ii)
 * to use the Documentation to develop your own plugins to the Software.
 * "Documentation" means the how to's and instructions (instruction videos)
 * provided with the Software and/or available on the XebiaLabs website or other
 * websites as well as the provided API documentation, tutorial and access to
 * the source code of the XebiaLabs plugins. You agree not to (i) lease, rent
 * or sublicense the Software or Documentation to any third party, or otherwise
 * use it except as permitted in this agreement; (ii) reverse engineer,
 * decompile, disassemble, or otherwise attempt to determine source code or
 * protocols from the Software, and/or to (iii) copy the Software or
 * Documentation (which includes the source code of the XebiaLabs plugins). You
 * shall not create or attempt to create any derivative works from the Software
 * except and only to the extent permitted by law. You will preserve XebiaLabs'
 * copyright and legal notices on the Software and Documentation. XebiaLabs
 * retains all rights not expressly granted to You in the Personal License
 * Agreement.
 */

package com.xebialabs.overthere;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class HostSessionItestBase {

	protected ConnectionOptions options;

	protected String type;

	protected HostConnection connection;

	@Before
	public void connect() {
		setTypeAndOptions();
		connection = Overthere.getConnection(type, options);
	}

	protected abstract void setTypeAndOptions();

	@After
	public void disconnect() {
		connection.disconnect();
	}

	@Test
	public void createWriteReadAndRemoveTemporaryFile() throws IOException {
		final String prefix = "prefix";
		final String suffix = "suffix";
		final byte[] contents = ("Contents of the temporary file created at " + System.currentTimeMillis() + "ms since the epoch").getBytes();

		HostFile tempFile = connection.getTempFile(prefix, suffix);
		assertNotNull("Expected a non-null return value from HostConnection.getTempFile()", tempFile);
		assertTrue("Expected name of temporary file to start with the prefix", tempFile.getName().startsWith(prefix));
		assertTrue("Expected name of temporary file to end with the suffix", tempFile.getName().endsWith(suffix));
		assertFalse("Expected temporary file to not exist yet", tempFile.exists());

		OutputStream outputStream = tempFile.put(contents.length);
		try {
			outputStream.write(contents);
		} finally {
			outputStream.close();
		}

		assertTrue("Expected temporary file to exist after writing to it", tempFile.exists());
		assertFalse("Expected temporary file to not be a directory", tempFile.isDirectory());
		assertEquals("Expected temporary file to have the size of the contents written to it", contents.length, tempFile.length());
		assertTrue("Expected temporary file to be readable", tempFile.canRead());
		assertTrue("Expected temporary file to be writeable", tempFile.canWrite());

		// Windows systems don't support the concept of checking for executability
		if (connection.getHostOperatingSystem() == OperatingSystemFamily.UNIX) {
			assertFalse("Expected temporary file to not be executable", tempFile.canExecute());
		}

		DataInputStream inputStream = new DataInputStream(tempFile.get());
		try {
			final byte[] contentsRead = new byte[contents.length];
			inputStream.readFully(contentsRead);
			assertEquals("Expected input stream to be exhausted after reading the full contents", 0, inputStream.available());
			assertTrue("Expected contents in temporary file to be identical to data written into it", Arrays.equals(contents, contentsRead));
		} finally {
			inputStream.close();
		}

		tempFile.delete();
		assertFalse("Expected temporary file to no longer exist", tempFile.exists());
	}

	@Test
	public void createPopulateListAndRemoveTemporaryDirectory() throws IOException {
		final String prefix = "prefix";
		final String suffix = "suffix";

		HostFile tempDir = connection.getTempFile(prefix, suffix);
		assertNotNull("Expected a non-null return value from HostConnection.getTempFile()", tempDir);
		assertTrue("Expected name of temporary file to start with the prefix", tempDir.getName().startsWith(prefix));
		assertTrue("Expected name of temporary file to end with the suffix", tempDir.getName().endsWith(suffix));
		assertFalse("Expected temporary file to not exist yet", tempDir.exists());

		tempDir.mkdir();
		assertTrue("Expected temporary directory to exist after creating it", tempDir.exists());
		assertTrue("Expected temporary directory to be a directory", tempDir.isDirectory());

		HostFile anotherTempDir = connection.getTempFile(prefix, suffix);
		assertFalse("Expected temporary directories created with identical prefix and suffix to still be different",
		        tempDir.getPath().equals(anotherTempDir.getPath()));

		HostFile nested1 = tempDir.getFile("nested1");
		HostFile nested2 = nested1.getFile("nested2");
		HostFile nested3 = nested2.getFile("nested3");
		assertFalse("Expected deeply nested directory to not exist", nested3.exists());
		try {
			nested3.mkdir();
			fail("Expected not to be able to create a deeply nested directory in one go");
		} catch (RuntimeIOException expected1) {
		}
		assertFalse("Expected deeply nested directory to still not exist", nested3.exists());
		nested3.mkdirs();
		assertTrue("Expected deeply nested directory to exist after invoking mkdirs on it", nested3.exists());

		final byte[] contents = ("Contents of the temporary file created at " + System.currentTimeMillis() + "ms since the epoch").getBytes();
		HostFile regularFile = tempDir.getFile("somefile.txt");
		regularFile.put(new ByteArrayInputStream(contents), contents.length);

		List<String> dirContents = tempDir.list();
		assertEquals("Expected directory to contain two entries", 2, dirContents.size());
		assertTrue("Expected directory to contain parent of deeply nested directory", dirContents.contains(nested1.getName()));
		assertTrue("Expected directory to contain regular file that was just created", dirContents.contains(regularFile.getName()));

		try {
			nested1.delete();
			fail("Expected to not be able to remove a non-empty directory");
		} catch (RuntimeIOException expected2) {
		}
		nested1.deleteRecursively();
		assertFalse("Expected parent of deeply nested directory to have been removed recursively", nested1.exists());

		regularFile.delete();
		tempDir.delete();
		assertFalse("Expected temporary directory to not exist after removing it when it was empty", tempDir.exists());
	}

}