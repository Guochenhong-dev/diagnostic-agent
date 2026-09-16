package com.guo.diagnostic;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.sql.*;
import java.util.*;
@Service
public class KnowledgeService {
 private final Environment env;private final ObjectMapper json;
 private final List<String> notes=List.of("空指针：核对堆栈方法中的对象是否为null；默认值应由业务规则确定，不能静默吞掉所有异常。","配置缺失：检查配置键、环境变量和类型转换；默认值只应用于缺失，非法配置应明确报错。","SQL映射：核对SELECT返回列、别名与ResultSet.getString名称是否一致，使用数据库集成测试验证。");
 public KnowledgeService(Environment e,ObjectMapper j){env=e;json=j;}
 public List<String> search(String text)throws Exception{
  if(!env.getProperty("app.vector.enabled",Boolean.class,false))return notes;
  try(Connection c=connect();PreparedStatement p=c.prepareStatement("SELECT body FROM fault_knowledge ORDER BY embedding <=> CAST(? AS vector) LIMIT 3")){
   p.setString(1,embed(text));p.setQueryTimeout(10);var out=new ArrayList<String>();try(var rs=p.executeQuery()){while(rs.next())out.add(rs.getString(1));}return out;
  }
 }
 public int index()throws Exception{
  if(!env.getProperty("app.vector.enabled",Boolean.class,false))throw new IllegalArgumentException("需要启用VECTOR_ENABLED");
  try(Connection c=connect()){
   for(int i=0;i<notes.size();i++)try(PreparedStatement p=c.prepareStatement("INSERT INTO fault_knowledge(id,body,embedding) VALUES(?,?,CAST(? AS vector)) ON CONFLICT(id) DO UPDATE SET body=EXCLUDED.body,embedding=EXCLUDED.embedding")){
    p.setInt(1,i+1);p.setString(2,notes.get(i));p.setString(3,embed(notes.get(i)));p.setQueryTimeout(10);p.executeUpdate();
   }
  }return notes.size();
 }
 private Connection connect()throws Exception{
  Properties p=new Properties();p.setProperty("user",env.getRequiredProperty("app.vector.user"));p.setProperty("password",env.getRequiredProperty("app.vector.password"));p.setProperty("connectTimeout","10");p.setProperty("socketTimeout","15");
  return DriverManager.getConnection(env.getRequiredProperty("app.vector.url"),p);
 }
 private String embed(String input)throws Exception{
  String key=env.getRequiredProperty("app.vector.embedding-key");if(key.isBlank())throw new IllegalArgumentException("需要EMBEDDING_KEY");
  var body=json.writeValueAsString(Map.of("model",env.getRequiredProperty("app.vector.embedding-model"),"input",input));
  var req=HttpRequest.newBuilder(URI.create(env.getRequiredProperty("app.vector.embedding-url"))).timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
  var res=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(req,HttpResponse.BodyHandlers.ofString());
  if(res.statusCode()!=200)throw new IllegalStateException("Embedding服务HTTP "+res.statusCode());
  var arr=json.readTree(res.body()).path("data").path(0).path("embedding");if(!arr.isArray()||arr.isEmpty())throw new IllegalStateException("Embedding结果为空");
  return arr.toString();
 }
}
