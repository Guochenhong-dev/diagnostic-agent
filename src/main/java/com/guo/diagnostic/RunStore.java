package com.guo.diagnostic;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;
import java.util.*;
import static com.guo.diagnostic.Domain.*;
@Repository
public class RunStore {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 public RunStore(JdbcTemplate j,ObjectMapper m){jdbc=j;json=m;}
 @PostConstruct public void recover(){
  for(Run r:list())if(Set.of("QUEUED","ANALYZING","VERIFYING").contains(r.status)){
   r.status="INTERRUPTED";r.error="服务重启中断任务，可新建诊断；验证不自动重放";save(r);
  }
 }
 public synchronized void save(Run r) {
  try {String payload=json.writeValueAsString(r);
   int n=jdbc.update("UPDATE diagnostic_run SET status=?,payload=? WHERE id=?",r.status,payload,r.id);
   if(n==0)jdbc.update("INSERT INTO diagnostic_run(id,status,payload,created_at) VALUES(?,?,?,?)",r.id,r.status,payload,r.createdAt);
  }catch(Exception e){throw new IllegalStateException("保存诊断记录失败",e);}
 }
 public Run get(String id){return jdbc.query("SELECT payload FROM diagnostic_run WHERE id=?",(rs,n)->decode(rs.getString(1)),id).stream().findFirst().orElseThrow(()->new IllegalArgumentException("记录不存在"));}
 private Run decode(String s){try{return json.readValue(s,Run.class);}catch(Exception e){throw new IllegalStateException(e);}}
 public List<Run> list(){return jdbc.query("SELECT payload FROM diagnostic_run ORDER BY created_at DESC",(rs,n)->decode(rs.getString(1)));}
 public boolean claim(String id){return jdbc.update("UPDATE diagnostic_run SET status='VERIFYING' WHERE id=? AND status='DIAGNOSED'",id)==1;}
}
