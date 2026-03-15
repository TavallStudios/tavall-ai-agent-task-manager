package com.agenttaskmanager.app.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfrastructureClientConfig {

  @Bean(destroyMethod = "close")
  public MongoClient mongoClient(
      MongoProperties properties,
      MongoConnectionStringResolver mongoConnectionStringResolver
  ) {
    return MongoClients.create(mongoConnectionStringResolver.resolve(properties));
  }

  @Bean
  public HttpClient httpClient() {
    return HttpClient.newHttpClient();
  }
}
