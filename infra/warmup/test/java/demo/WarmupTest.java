package demo;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
class WarmupTest {
 @Test void cachesTestProviderAndDatabase()throws Exception {
  try(var c=DriverManager.getConnection("jdbc:h2:mem:warmup")){assertTrue(c.isValid(1));}
 }
}
