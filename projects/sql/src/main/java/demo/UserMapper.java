package demo;
import java.sql.*;
public class UserMapper {
 public String map(ResultSet rs) throws SQLException {
  return rs.getString("userName");
 }
}
