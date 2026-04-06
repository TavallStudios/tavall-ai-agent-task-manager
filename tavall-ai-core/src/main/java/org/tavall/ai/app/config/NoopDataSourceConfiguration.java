package org.tavall.ai.app.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.AbstractDataSource;

@Configuration
@Conditional({MissingDataSourceUrlOnlyCondition.class, EmbeddedPostgresDisabledCondition.class})
public class NoopDataSourceConfiguration {

  @Bean
  public DataSource dataSource() {
    return new NoopDataSource();
  }

  private static final class NoopDataSource extends AbstractDataSource {

    @Override
    public Connection getConnection() throws SQLException {
      throw new SQLException("No datasource configured (embedded Postgres disabled).");
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      throw new SQLException("No datasource configured (embedded Postgres disabled).");
    }
  }
}
