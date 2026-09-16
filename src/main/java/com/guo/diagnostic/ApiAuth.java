package com.guo.diagnostic;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Component
public class ApiAuth extends OncePerRequestFilter {
 private final byte[] token;
 public ApiAuth(@Value("${app.token}")String t){if(t.isBlank())throw new IllegalArgumentException("APP_TOKEN不能为空");token=t.getBytes(StandardCharsets.UTF_8);}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  if(req.getRequestURI().startsWith("/api/")){
   String value=req.getHeader("X-App-Token");if(value==null||!MessageDigest.isEqual(token,value.getBytes(StandardCharsets.UTF_8))){res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"error\":\"请填写正确的本地访问令牌\"}");return;}
   if(req.getContentLengthLong()>64000){res.setStatus(413);return;}
  }
  res.setHeader("X-Content-Type-Options","nosniff");chain.doFilter(req,res);
 }
}
