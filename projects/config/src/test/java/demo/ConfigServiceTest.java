package demo;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ConfigServiceTest {
 @Test void missing(){assertEquals(30,new ConfigService().timeout(new Properties()));}
 @Test void number(){Properties p=new Properties();p.setProperty("timeout","5");assertEquals(5,new ConfigService().timeout(p));}
 @Test void invalid(){Properties p=new Properties();p.setProperty("timeout","bad");assertThrows(NumberFormatException.class,()->new ConfigService().timeout(p));}
}
