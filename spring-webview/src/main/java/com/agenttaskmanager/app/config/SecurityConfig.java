package com.agenttaskmanager.app.config;

import java.security.SecureRandom;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);
  private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

  @Bean
  @Order(0)
  @ConditionalOnProperty(prefix = "app.security", name = "mcp-no-auth-enabled", havingValue = "true")
  SecurityFilterChain mcpNoAuthFilterChain(
      HttpSecurity http,
      ServerProperties serverProperties,
      McpServerProperties mcpServerProperties
  ) throws Exception {
    return http
        .securityMatcher(mcpRequestMatcher(serverProperties, mcpServerProperties))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .build();
  }

  @Bean
  @Order(1)
  SecurityFilterChain mcpSecurityFilterChain(
      HttpSecurity http,
      SecurityProperties properties,
      PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider,
      ServerProperties serverProperties,
      McpServerProperties mcpServerProperties
  ) throws Exception {
    if (properties.isProxyAuthEnabled()) {
      RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter =
          new RequestHeaderAuthenticationFilter();
      requestHeaderAuthenticationFilter.setPrincipalRequestHeader(properties.getProxyAuthHeader());
      requestHeaderAuthenticationFilter.setExceptionIfHeaderMissing(false);
      requestHeaderAuthenticationFilter.setAuthenticationManager(
          new ProviderManager(List.of(preAuthenticatedAuthenticationProvider))
      );
      http.addFilterBefore(requestHeaderAuthenticationFilter, BasicAuthenticationFilter.class);
    }

    return http
        .securityMatcher(mcpRequestMatcher(serverProperties, mcpServerProperties))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain fallbackFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .build();
  }

  private RequestMatcher mcpRequestMatcher(
      ServerProperties serverProperties,
      McpServerProperties mcpServerProperties
  ) {
    String mcpPath = normalizePath(serverProperties.getServlet().getContextPath())
        + normalizePath(mcpServerProperties.getEndpoint());
    return request -> request.getRequestURI().equals(mcpPath)
        || request.getRequestURI().startsWith(mcpPath + "/");
  }

  private String normalizePath(String value) {
    if (value == null || value.isBlank() || "/".equals(value)) {
      return "";
    }
    String normalized = value.startsWith("/") ? value : "/" + value;
    return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
  }

  @Bean
  PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider(
      UserDetailsService userDetailsService
  ) {
    PreAuthenticatedAuthenticationProvider provider = new PreAuthenticatedAuthenticationProvider();
    UserDetailsByNameServiceWrapper wrapper = new UserDetailsByNameServiceWrapper();
    wrapper.setUserDetailsService(userDetailsService);
    provider.setPreAuthenticatedUserDetailsService(wrapper);
    return provider;
  }

  @Bean
  DaoAuthenticationProvider daoAuthenticationProvider(
      UserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder
  ) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  UserDetailsService userDetailsService(SecurityProperties properties, PasswordEncoder encoder) {
    String rawPassword = properties.getPassword();
    if (rawPassword == null || rawPassword.isBlank()) {
      rawPassword = generatePassword();
      LOGGER.warn(
          "AGENT_TASK_MANAGER_PASSWORD was not set. Generated startup password for user '{}': {}",
          properties.getUsername(),
          rawPassword
      );
    }

    UserDetails user = User.withUsername(properties.getUsername())
        .password(encoder.encode(rawPassword))
        .roles("OPERATOR")
        .build();
    return new InMemoryUserDetailsManager(user);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private static String generatePassword() {
    SecureRandom random = new SecureRandom();
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < 20; index++) {
      builder.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
    }
    return builder.toString();
  }
}
