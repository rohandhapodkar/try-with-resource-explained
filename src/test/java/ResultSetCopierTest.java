import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class ResultSetCopierTest {

	ResultSetCopier copyFile;

	@Mock
	Connection con;

	@Mock
	Writer writer;

	private String query = "select 1 from account";

	private String fileName = "account.csv";

	interface ThrowingValidator {
		void validate() throws Exception;
	}

	final ThrowingValidator COPY_FILE_GET_CONNECTION_CALLED = () -> Mockito.verify(copyFile).getConnection();
	final ThrowingValidator COPY_FILE_GET_WRITER_CALLED = () -> Mockito.verify(copyFile).getFileWriter(anyString());
	final ThrowingValidator COPY_FILE_COPY_RESULT_SET_CALLED = () -> Mockito.verify(copyFile)
			.copyResultSetToFile(anyString(), anyString());
	final ThrowingValidator COPY_FILE_TO_STREAM_CALLED = () -> Mockito.verify(copyFile)
			.copyResultSetToStream(any(Connection.class), anyString(), any(Writer.class));

	final ThrowingValidator CONNECTION_CLOSED = () -> Mockito.verify(con).close();
	final ThrowingValidator WRITER_CLOSED = () -> Mockito.verify(writer).close();

	@After
	public void validateNoMoreInteraction() throws Exception {
		Mockito.verifyNoMoreInteractions(con);
		Mockito.verifyNoMoreInteractions(writer);
		Mockito.verify(copyFile).copyResultSetToFile(anyString(), anyString());
		Mockito.verifyNoMoreInteractions(copyFile);

	}

	@Before
	public void initMocks() throws SQLException, IOException {
		MockitoAnnotations.initMocks(this);
		copyFile = Mockito.spy(getClassToSpy());
		when(copyFile.getConnection()).thenReturn(con);

		when(copyFile.getFileWriter(anyString())).thenReturn(writer);
		doNothing().when(copyFile).copyResultSetToStream(any(Connection.class), anyString(), any(Writer.class));
	}

	protected Class<? extends ResultSetCopier> getClassToSpy() {
		return ResultSetCopier.class;
	}

	public void validate(ThrowingValidator... validators) throws Exception {
		for (ThrowingValidator validator : validators) {
			validator.validate();
		}
	}

	/**
	 * This is happy path scenario, where no exceptions thrown by any method.
	 * 
	 * @throws Exception
	 */
	@Test
	public void test_happy_path_scenario() throws Exception {
		Assertions.assertThatCode(() -> copyFile.copyResultSetToFile(query, fileName)).doesNotThrowAnyException();

		this.validate(COPY_FILE_GET_CONNECTION_CALLED, COPY_FILE_GET_WRITER_CALLED, COPY_FILE_COPY_RESULT_SET_CALLED,
				COPY_FILE_TO_STREAM_CALLED, CONNECTION_CLOSED, WRITER_CLOSED);
	}

	/**
	 * In this test case, since null connection was returned, JVM skip close()
	 * method on Connection resource.
	 * 
	 * @throws Exception
	 */
	@Test
	public void test_try_with_resource_is_null_safe() throws Exception {
		when(copyFile.getConnection()).thenReturn(null);

		Assertions.assertThatCode(() -> copyFile.copyResultSetToFile(query, fileName)).doesNotThrowAnyException();

		this.validate(COPY_FILE_GET_CONNECTION_CALLED, COPY_FILE_GET_WRITER_CALLED, COPY_FILE_TO_STREAM_CALLED,
				COPY_FILE_COPY_RESULT_SET_CALLED, WRITER_CLOSED);
	}

	/**
	 * In this test case,both null resources were returned, JVM doesn't fail
	 * with NullPoinerException.
	 * 
	 * @throws Exception
	 */
	@Test
	public void test_try_with_resource_is_null_safe_for_both_resources() throws Exception {
		when(copyFile.getConnection()).thenReturn(null);
		when(copyFile.getFileWriter(anyString())).thenReturn(null);

		Assertions.assertThatCode(() -> copyFile.copyResultSetToFile(query, fileName)).doesNotThrowAnyException();

		this.validate(COPY_FILE_GET_CONNECTION_CALLED, COPY_FILE_GET_WRITER_CALLED, COPY_FILE_TO_STREAM_CALLED,
				COPY_FILE_COPY_RESULT_SET_CALLED);
	}

	/**
	 * When Connection instance throws Exception during close method, same
	 * exception is propagated to caller.
	 * 
	 * @throws Exception
	 */
	@Test
	public void test_exception_during_close_connection_is_sql_Exception() throws Exception {
		Mockito.doThrow(new SQLException("Exception while closing connection")).when(con).close();

		Assertions.assertThatCode(() -> copyFile.copyResultSetToFile(query, fileName)).isInstanceOf(SQLException.class)
				.hasMessage("Exception while closing connection")
				.matches(throwable -> throwable.getSuppressed().length == 0);

		this.validate(COPY_FILE_GET_CONNECTION_CALLED, COPY_FILE_GET_WRITER_CALLED, COPY_FILE_TO_STREAM_CALLED,
				COPY_FILE_COPY_RESULT_SET_CALLED, CONNECTION_CLOSED, WRITER_CLOSED);
	}

	/**
	 * When exception is thrown within try block, participating resources are
	 * closed first and then same Exception is rethrown back to caller.
	 * 
	 * @throws Exception
	 */
	@Test
	public void test_when_try_block_throws_exception_resources_are_closed() throws Exception {
		Mockito.doThrow(new SQLException("Exception in copyResultSetToFile")).when(copyFile)
				.copyResultSetToStream(any(Connection.class), anyString(), any(Writer.class));

		Assertions.assertThatCode(() -> copyFile.copyResultSetToFile(query, fileName)).isInstanceOf(SQLException.class)
				.hasMessage("Exception in copyResultSetToFile")
				.matches(throwable -> throwable.getSuppressed().length == 0);

		this.validate(COPY_FILE_GET_CONNECTION_CALLED, COPY_FILE_GET_WRITER_CALLED, COPY_FILE_TO_STREAM_CALLED,
				COPY_FILE_COPY_RESULT_SET_CALLED, CONNECTION_CLOSED, WRITER_CLOSED);
	}

	public boolean verifyExceptionTypeAndMessage(Throwable t, Class<? extends Throwable> clazz, String msg) {
		return Assertions.assertThat(t).isInstanceOf(clazz).hasMessage(msg) != null;
	}

	/**
	 * When both participating resources throws Exception in close() method,
	 * last thrown Exception ie. IOException is propagated to caller and
	 * SQLException is added to list of SuppressedException of IOException
	 * 
	 * @param t
	 * @param clazz
	 * @param msg
	 * @return
	 */
	@Test
	public void test_SQL_Exception_is_suppressed_and_IOException_Thrown() throws Exception {
		Mockito.doThrow(new SQLException("Exception while closing connection")).when(con).close();
		Mockito.doThrow(new IOException("Exception while closing writer")).when(writer).close();

		Assertions.assertThatCode(() -> copyFile.copyResultSetToFile(query, fileName)).isInstanceOf(IOException.class)
				.hasMessage("Exception while closing writer")
				.matches(throwable -> throwable.getSuppressed().length == 1, "has one suppressed")
				.matches(throwable -> verifyExceptionTypeAndMessage(throwable.getSuppressed()[0], SQLException.class,
						"Exception while closing connection"), "suppressed instance of SQLException");

		this.validate(COPY_FILE_GET_CONNECTION_CALLED, COPY_FILE_GET_WRITER_CALLED, COPY_FILE_TO_STREAM_CALLED,
				COPY_FILE_COPY_RESULT_SET_CALLED, CONNECTION_CLOSED, WRITER_CLOSED);
	}

	@Test
	public void test_exception_thrown_in_getConnection() throws Exception {
		Mockito.doThrow(new SQLException("Exception while creating new connection")).when(copyFile).getConnection();

		Assertions.assertThatCode(() -> copyFile.copyResultSetToFile(query, fileName)).isInstanceOf(SQLException.class)
				.hasMessage("Exception while creating new connection")
				.matches(throwable -> throwable.getSuppressed().length == 0, "no suppressed exception");

		this.validate(COPY_FILE_GET_CONNECTION_CALLED, COPY_FILE_COPY_RESULT_SET_CALLED);
	}

	/**
	 * When Exception is thrown in try block and at the same time Exceptions are
	 * thrown while invoking resources close() method, then Exception thrown
	 * from try block is propagated to calling method and Exception thrown from
	 * close method are added to suppressed exception list.
	 * 
	 * @throws Exception
	 */
	@Test
	public void test_try_and_resources_throwing_exception() throws Exception {
		Mockito.doThrow(new SQLException("Exception in copyResultSetToFile")).when(copyFile)
				.copyResultSetToStream(any(Connection.class), anyString(), any(Writer.class));
		Mockito.doThrow(new SQLException("Exception while closing connection")).when(con).close();
		Mockito.doThrow(new IOException("Exception while closing writer")).when(writer).close();

		Assertions.assertThatCode(() -> copyFile.copyResultSetToFile(query, fileName)).isInstanceOf(SQLException.class)
				.hasMessage("Exception in copyResultSetToFile")
				.matches(throwable -> throwable.getSuppressed().length == 2, "has two suppressed")
				.matches(throwable -> verifyExceptionTypeAndMessage(throwable.getSuppressed()[0], IOException.class,
						"Exception while closing writer"), "suppressed instance of IOException")
				.matches(throwable -> verifyExceptionTypeAndMessage(throwable.getSuppressed()[1], SQLException.class,
						"Exception while closing connection"), "suppressed instance of SQLException");

		this.validate(COPY_FILE_GET_CONNECTION_CALLED, COPY_FILE_GET_WRITER_CALLED, COPY_FILE_TO_STREAM_CALLED,
				COPY_FILE_COPY_RESULT_SET_CALLED, CONNECTION_CLOSED, WRITER_CLOSED);
	}

}
