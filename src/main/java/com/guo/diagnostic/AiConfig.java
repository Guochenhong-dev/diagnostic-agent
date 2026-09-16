package com.guo.diagnostic;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.*;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
@Configuration
@ConditionalOnProperty(name="app.ai.enabled",havingValue="true")
public class AiConfig {
 @Bean ChatClient chatClient(@Value("${app.ai.api-key}")String key,@Value("${app.ai.base-url}")String url,@Value("${app.ai.model}")String model){
  if(key.isBlank())throw new IllegalArgumentException("请设置AI_API_KEY");
  var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(10000);factory.setReadTimeout(30000);
  var api=OpenAiApi.builder().apiKey(key).baseUrl(url).restClientBuilder(RestClient.builder().requestFactory(factory)).build();
  var chat=OpenAiChatModel.builder().openAiApi(api).defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.1).maxTokens(1800).build())
   .retryTemplate(RetryTemplate.builder().maxAttempts(1).fixedBackoff(100).build()).build();
  return ChatClient.create(chat);
 }
}
