package demo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NameServiceTest {
 @Test void nullName(){assertEquals("匿名用户",new NameService().display(null));}
 @Test void trimmed(){assertEquals("Alice",new NameService().display(" Alice "));}
 @Test void empty(){assertEquals("",new NameService().display(" "));}
}
