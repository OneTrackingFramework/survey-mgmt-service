/**
 *
 */
package one.tracking.framework.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.google.common.collect.ImmutableList;
import one.tracking.framework.filter.BearerAuthenticationFilter;
import one.tracking.framework.repo.UserRepository;
import one.tracking.framework.support.JWTHelper;

/**
 * @author Marko Voß
 *
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${app.security.role.admin}")
  private String roleAdmin;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {

    final CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(ImmutableList.of("*"));
    configuration.setAllowedMethods(ImmutableList.of("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH"));
    configuration.setAllowCredentials(true);
    configuration.setAllowedHeaders(
        ImmutableList.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CACHE_CONTROL, HttpHeaders.CONTENT_TYPE));
    final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Configuration
  @Order(0)
  public class SecurityConfigClient extends WebSecurityConfigurerAdapter {

    @Value("${app.security.verify.client-id}")
    private String username;

    @Value("${app.security.verify.client-secret}")
    private String password;

    @Bean
    public AuthenticationEntryPoint authenticationEntryPointClient() {
      final BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
      entryPoint.setRealmName("client");
      return entryPoint;
    }

    @Autowired
    public void configureGlobal(final AuthenticationManagerBuilder auth) throws Exception {
      auth.inMemoryAuthentication()
          .withUser(this.username)
          .password(passwordEncoder().encode(this.password))
          .authorities("ROLE_CLIENT");
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      http.antMatcher("/auth/verify")
          .cors().and().csrf().disable()
          .authorizeRequests()
          .antMatchers(HttpMethod.POST, "/auth/verify").hasAnyAuthority("ROLE_CLIENT")
          .antMatchers(HttpMethod.GET, "/auth/verify").permitAll()
          .and()
          .httpBasic()
          .authenticationEntryPoint(authenticationEntryPointClient())
          .and()
          .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
  }

  @Configuration
  @Order(1)
  public class SecurityConfigDeviceToken extends WebSecurityConfigurerAdapter {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JWTHelper jwtHelper;

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      http.antMatcher("/auth/devicetoken")
          .cors().and().csrf().disable()
          .authorizeRequests()
          .anyRequest().authenticated()
          .and()
          .addFilter(bearerAuthenticationFilter())
          .sessionManagement()
          .sessionCreationPolicy(SessionCreationPolicy.STATELESS);

    }

    public BearerAuthenticationFilter bearerAuthenticationFilter() throws Exception {

      return new BearerAuthenticationFilter(authenticationManager(), this.jwtHelper) {

        @Override
        protected boolean checkIfUserExists(final String userId) {
          return SecurityConfigDeviceToken.this.userRepository.existsById(userId);
        }

      };
    }
  }

  /**
   *
   * TODO: Implement IT based on mocking {@link OAuth2UserService} instead of switching to HTTP BASIC.
   *
   */
  @Configuration
  @Profile("dev")
  @Order(2)
  public class SecurityConfigDev extends WebSecurityConfigurerAdapter {

    @Value("${app.security.dev.user}")
    private String username;

    @Value("${app.security.dev.password}")
    private String password;

    @Bean
    public AuthenticationEntryPoint authenticationEntryPointDev() {
      final BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
      entryPoint.setRealmName("dev");
      return entryPoint;
    }

    @Autowired
    public void configureGlobal(final AuthenticationManagerBuilder auth) throws Exception {
      auth.inMemoryAuthentication()
          .withUser(this.username)
          .password(passwordEncoder().encode(this.password))
          .authorities(SecurityConfig.this.roleAdmin);
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      http.cors().and().csrf().disable()
          .authorizeRequests()
          .antMatchers("/manage/**", "/user").hasAnyAuthority(SecurityConfig.this.roleAdmin)
          .and()
          .httpBasic()
          .authenticationEntryPoint(authenticationEntryPointDev())
          .and()
          .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
  }

  /**
   *
   * TODO: Implement IT based on mocking {@link OAuth2UserService} instead of switching to HTTP BASIC.
   *
   */
  @Configuration
  @Profile("!dev")
  @Order(2)
  public class SecurityConfigProd extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;


    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      http.cors().and().csrf().disable()
          .authorizeRequests()
          .antMatchers("/manage/**", "/user").hasAnyRole(SecurityConfig.this.roleAdmin)
          .antMatchers("/oauth2/**").permitAll()
          .and()
          .oauth2Login()
          .userInfoEndpoint()
          .oidcUserService(this.oidcUserService);
    }
  }

  @Configuration
  @Profile("dev")
  @Order(3)
  public class SecurityConfigOpen extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      http.cors().and().csrf().disable()
          .authorizeRequests()
          .antMatchers(
              "/v2/api-docs",
              "/swagger*/**",
              "/webjars/**",
              "/h2-console/**",
              "/v3/api-docs/**")
          .permitAll()
          .anyRequest().denyAll();
    }
  }
}
