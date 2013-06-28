package hgtest.storage.bje;

import org.easymock.EasyMock;
import org.hypergraphdb.HGConfiguration;
import org.hypergraphdb.HGHandleFactory;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.HGStore;
import org.hypergraphdb.handle.IntPersistentHandle;
import org.hypergraphdb.storage.bje.BJEStorageImplementation;
import com.sleepycat.je.Environment;
import org.hypergraphdb.transaction.HGTransactionManager;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNull;

/**
 * This class contains unit tests for {@link BJEStorageImplementation}. For this
 * purpose we use PowerMock + EasyMock test framework (see <a
 * href="http://code.google.com/p/powermock/">PowerMock page on Google Code</a>
 * and <a href="http://easymock.org/">EasyMock home page</a> for details).
 * EasyMock's capabilities are sufficient for interfaces and plain classes. But
 * some classes in hypergraphdb modules are final so we cannot mock it in usual
 * way. PowerMock allows to create mocks even for final classes. So we can test
 * {@link BJEStorageImplementation} in isolation from other environment and in
 * most cases (if required) from other classes.
 * 
 * @author Yuriy Sechko
 */

@PrepareForTest(HGConfiguration.class)
public class BJEStorageImplementationTest extends PowerMockTestCase
{
	private static final String TEMP_DATABASE_DIRECTORY_SUFFIX = "hgtest.tmp";
	private static final String HGHANDLEFACTORY_IMPLEMENTATION_CLASS_NAME = "org.hypergraphdb.handle.UUIDHandleFactory";

	// location of temporary directory for tests
	private String testDatabaseLocation = System.getProperty("user.home")
			+ File.separator + "hgtest.tmp";

	// classes which are used by BJEStorageImplementation
	HGStore store;
	HGConfiguration configuration;

	final BJEStorageImplementation storage = new BJEStorageImplementation();

	@BeforeClass
	private void createMocks() throws Exception
	{
		store = PowerMock.createStrictMock(HGStore.class);
		configuration = PowerMock.createStrictMock(HGConfiguration.class);
	}

	@AfterMethod
	private void resetMocksAndDeleteTestDirectory()
	{
		PowerMock.reset(store, configuration);
		new File(testDatabaseLocation).delete();
	}

	private void mockConfiguration(final int calls) throws Exception
	{
		EasyMock.expect(configuration.getHandleFactory()).andReturn(
				(HGHandleFactory) Class.forName(
						HGHANDLEFACTORY_IMPLEMENTATION_CLASS_NAME)
						.newInstance());
		EasyMock.expect(configuration.isTransactional()).andReturn(true)
				.times(calls);
	}

	private void mockStore() throws Exception
	{
		EasyMock.expect(store.getDatabaseLocation()).andReturn(
				TEMP_DATABASE_DIRECTORY_SUFFIX);
	}

	private void mockTransactionManager(final int calls)
	{
		final HGTransactionManager transactionManager = new HGTransactionManager(
				storage.getTransactionFactory());
		EasyMock.expect(store.getTransactionManager())
				.andReturn(transactionManager).times(calls);
	}

	public void replay() throws Exception
	{
		EasyMock.replay(store, configuration);
	}

	private void verify() throws Exception
	{
		PowerMock.verifyAll();
	}

	@Test
	public void environmentIsTransactional() throws Exception
	{
		mockConfiguration(2);
		mockStore();
		replay();
		storage.startup(store, configuration);
		final boolean isTransactional = storage.getConfiguration()
				.getDatabaseConfig().getTransactional();
		assertTrue(isTransactional);
		storage.shutdown();
		verify();
	}

	@Test
	public void storageIsNotReadOnly() throws Exception
	{
		mockConfiguration(2);
		mockStore();
		replay();
		storage.startup(store, configuration);
		final boolean isReadOnly = storage.getConfiguration()
				.getDatabaseConfig().getReadOnly();
		assertFalse(isReadOnly);
		storage.shutdown();
		verify();
	}

	@Test
	public void databaseNameAsSpecifiedInStore() throws Exception
	{
		mockConfiguration(2);
		mockStore();
		replay();
		storage.startup(store, configuration);
		final String databaseName = storage.getBerkleyEnvironment().getHome()
				.getPath();
		assertEquals(databaseName, TEMP_DATABASE_DIRECTORY_SUFFIX);
		storage.shutdown();
		verify();
	}

	@Test
	public void getDatabasePathAfterShutdown() throws Exception
	{
		try
		{
			mockConfiguration(2);
			mockStore();
			replay();
			storage.startup(store, configuration);
			storage.shutdown();
			final Environment environment = storage.getBerkleyEnvironment();
			// environment is not open, expect exception
			environment.getHome().getPath();
		}
		catch (Exception ex)
		{
			assertEquals(ex.getClass(), IllegalStateException.class);
		}
		finally
		{
			verify();
		}
	}

	@Test
	public void storeArrayOfBytes() throws Exception
	{
		final byte[] expected = new byte[] { 1, 2, 3 };
		mockConfiguration(2);
		mockStore();
		mockTransactionManager(2);
		replay();
		storage.startup(store, configuration);
		final HGPersistentHandle handle = new IntPersistentHandle(1);
		storage.store(handle, new byte[] { 1, 2, 3 });
		final byte[] stored = storage.getData(handle);
		assertEquals(stored, expected);
		storage.shutdown();
		verify();
	}

	@Test
	public void readDataUsingHandle() throws Exception
	{
		final byte[] expected = new byte[] { 1, 2, 3 };
		mockConfiguration(2);
		mockStore();
		mockTransactionManager(2);
		replay();
		storage.startup(store, configuration);
		final HGPersistentHandle handle = new IntPersistentHandle(1);
		storage.store(handle, new byte[] { 1, 2, 3 });
		final byte[] stored = storage.getData(handle);
		assertEquals(stored, expected);
		storage.shutdown();
		verify();
	}

	@Test
	public void readDataWhichIsNotStoredUsingGivenHandle() throws Exception
	{
		mockConfiguration(2);
		mockStore();
		mockTransactionManager(1);
		replay();
		storage.startup(store, configuration);
		final HGPersistentHandle handle = new IntPersistentHandle(2);
		try
		{
			// data was not stored, expect exception
			storage.getData(handle);
		}
		catch (Exception ex)
		{
			assertEquals(ex.getClass(), org.hypergraphdb.HGException.class);
		}
		finally
		{
			storage.shutdown();
			verify();
		}
	}

	@Test
	public void removeDataUsingHandle() throws Exception
	{
		mockConfiguration(2);
		mockStore();
		mockTransactionManager(3);
		replay();
		storage.startup(store, configuration);
		final HGPersistentHandle handle = new IntPersistentHandle(1);
		storage.store(handle, new byte[] { 1, 2, 3 });
		storage.removeData(handle);
		final byte[] retrievedData = storage.getData(handle);
		assertNull(retrievedData);
		storage.shutdown();
		verify();
	}
}