package com.agenttaskmanager.app.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(MissingDataSourceUrlCondition.class)
public class EmbeddedPostgresDataSourceConfiguration {

  @Bean
  public DataSource dataSource() {
    return SharedEmbeddedPostgresDataSourceFactory.dataSource();
  }
}
