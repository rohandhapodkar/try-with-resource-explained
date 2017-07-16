import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;

public class ResultSetCopierV2 extends ResultSetCopier {

	public void copyResultSetToFile(String query, String fileName) throws SQLException, IOException {

		Writer writer = null;
		Connection con = this.getConnection();
		try {
			writer = this.getFileWriter(fileName);
			this.copyResultSetToStream(con, query, writer);
		} finally {
			if (con != null) {
				try {
					con.close();
				} catch (Exception e) {

				}
			}
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public Connection testConnection() throws SQLException {
		return this.getConnection();
	}

	public void copyResultSetToStream(Connection con, String query, Writer writer) throws SQLException, IOException {
		return;
	}

	public Connection getConnection() throws SQLException {
		return null;
	}

	public Writer getFileWriter(String fileName) throws IOException {
		return null;
	}

}
