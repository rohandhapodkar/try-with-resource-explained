import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;

public class ResultSetCopier {

	
	public void copyResultSetToFile(String query, String fileName) throws SQLException, IOException {
		
		try(Connection con = this.getConnection();Writer writer = this.getFileWriter(fileName)) {
			this.copyResultSetToStream(con, query, writer);
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
	
	public Writer getFileWriter(String fileName) throws IOException  {
		return null;
	}
	
	
	
	
}
