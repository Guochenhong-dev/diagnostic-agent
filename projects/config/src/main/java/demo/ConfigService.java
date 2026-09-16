package demo;
import java.util.Properties;
public class ConfigService {
 public int timeout(Properties properties) {
  return Integer.parseInt(properties.getProperty("timeout"));
 }
}
