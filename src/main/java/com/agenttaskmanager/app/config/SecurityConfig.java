package com.agenttaskmanager.app.config;

import java.security.SecureRandom;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity
public class SecurityConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);
  private static final String PASSWORD_CHARS =
      "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityProperties properties,
      PreAuthenticatedAuthenticationProvider preAuthenticatedAuthenticationProvider
  )
      throws Exception {
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
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/login", "/css/**", "/js/**").permitAll()
            .anyRequest().authenticated()
        )
        .formLogin(form -> form
            .loginPage("/login")
            .defaultSuccessUrl("/", true)
            .permitAll()
        )
        .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
        .rememberMe(remember -> remember.key(properties.getRememberMeKey()))
        .csrf(csrf -> csrf
            .ignoringRequestMatchers("/api/bridge/**", "/mcp", "/mcp/**")
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        )
        .httpBasic(Customizer.withDefaults())
        .build();
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
