package com.guo.diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.guo.diagnostic.Domain.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:workflow;MODE=MySQL;DB_CLOSE_DELAY=-1","app.ai.enabled=false","app.vector.enabled=false","app.runner.enabled=false","app.token=local-demo-token"})
@AutoConfigureMockMvc
class WorkflowTest {
 @Autowired MockMvc http; @Autowired RunService service; @Autowired RunStore store; @Autowired FilesService files;
 Run await(String id)throws Exception {for(int i=0;i<100;i++){Run r=store.get(id);if(!r.status.equals("QUEUED")&&!r.status.equals("ANALYZING"))return r;Thread.sleep(50);}throw new AssertionError("任务未完成");}
 @Test void authAndValidation()throws Exception {
  http.perform(get("/api/projects")).andExpect(status().isUnauthorized());
  http.perform(get("/api/projects").header("X-App-Token","local-demo-token")).andExpect(status().isOk());
  http.perform(post("/api/runs").header("X-App-Token","local-demo-token").contentType("application/json").content("{\"project\":\"../x\",\"log\":\"\"}")).andExpect(status().isBadRequest());
 }
 @Test void threeFaultsProduceAnchoredPatches()throws Exception {
  String[][] cases={{"npe","NullPointerException at demo.NameService.display(NameService.java:4)","匿名用户"},{"config","NumberFormatException at demo.ConfigService.timeout(ConfigService.java:5)","30"},{"sql","SQLException at demo.UserMapper.map(UserMapper.java:5)","user_name"}};
  for(String[] c:cases){Run r=await(service.create(new Request(c[0],c[1],false)));assertEquals("DIAGNOSED",r.status,r.error);assertEquals("RULE_DEMO",r.mode);assertFalse(r.evidence.isEmpty());assertTrue(r.diagnosis.patch().after().contains(c[2]));assertNotNull(files.patchTarget(c[0],r.diagnosis.patch()));assertThrows(IllegalArgumentException.class,()->service.verify(r.id,false));assertThrows(IllegalArgumentException.class,()->service.verify(r.id,true));}
 }
 @Test void modelDisabledFailsExplicitly()throws Exception {Run r=await(service.create(new Request("npe","NullPointerException NameService",true)));assertEquals("FAILED",r.status);assertNull(r.diagnosis);}
 @Test void rejectsTraversalAndTestEdits()throws Exception {
  assertThrows(IllegalArgumentException.class,()->files.read("npe","../../pom.xml"));
  assertThrows(IllegalArgumentException.class,()->files.patchTarget("npe",new Patch("src/test/java/demo/NameServiceTest.java","x","y")));
  assertThrows(IllegalArgumentException.class,()->files.patchTarget("npe",new Patch("src/main/java/demo/NameService.java","not present","x")));
  assertFalse(FilesService.redact("api_key=secret123 Bearer token123").contains("secret123"));
 }
 @Test void unknownFaultDoesNotInventPatch()throws Exception {Run r=await(service.create(new Request("npe","unrelated failure",false)));assertEquals("DIAGNOSED",r.status);assertNull(r.diagnosis.patch());}
}
