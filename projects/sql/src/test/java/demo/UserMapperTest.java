package demo;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UserMapperTest {
 @Test void realColumn()throws Exception {try(Connection c=DriverManager.getConnection("jdbc:h2:mem:fixture");Statement s=c.createStatement();ResultSet rs=s.executeQuery("SELECT 'Alice' AS user_name")){rs.next();assertEquals("Alice",new UserMapper().map(rs));}}
 @Test void nullValue()throws Exception {try(Connection c=DriverManager.getConnection("jdbc:h2:mem:fixture2");Statement s=c.createStatement();ResultSet rs=s.executeQuery("SELECT CAST(NULL AS VARCHAR) AS user_name")){rs.next();assertNull(new UserMapper().map(rs));}}
}
