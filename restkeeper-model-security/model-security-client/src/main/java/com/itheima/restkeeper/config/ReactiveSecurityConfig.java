package com.itheima.restkeeper.config;

import com.itheima.restkeeper.properties.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

/**
 * @ClassName ReactiveSecurityConfig.java
 * @Description 支持flum的权限配置
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class ReactiveSecurityConfig {

    @Autowired
    SecurityProperties securityProperties;

    //表单转换器
    @Autowired
    ServerAuthenticationConverter reactiveServerAuthenticationConverter;

    //认证
    @Autowired
    ReactiveAuthenticationManager jwtReactiveAuthenticationManager;

    //登录成功
    @Autowired
    ServerAuthenticationSuccessHandler jsonServerAuthenticationSuccessHandler;

    //登录失败
    @Autowired
    ServerAuthenticationFailureHandler jsonServerAuthenticationFailureHandler;

    //退出成功
    @Autowired
    ServerLogoutSuccessHandler jsonServerLogoutSuccessHandler;

    //鉴权
    @Autowired
    ReactiveAuthorizationManager jwtReactiveAuthorizationManager;

    //无权限
    @Autowired
    ServerAuthenticationEntryPoint jsonServerAuthenticationEntryPoint;


    /**
     * BCrypt密码编码
     * @return
     */
    @Bean
    public BCryptPasswordEncoder bcryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /***
     * @description 跨域处理
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(){
        return httpServletRequest -> {
            //初始化跨域配置
            CorsConfiguration cfg = new CorsConfiguration();
            //请求头中可以包含任意的参数
            cfg.addAllowedHeader("*");
            //请求方式可以是任意方式：post get patch pu delete opertions
            cfg.addAllowedMethod("*");
            //指定请求源的目标地址
            securityProperties.getOrigins().forEach(origin->{
                cfg.addAllowedOrigin(origin);
            });
            cfg.setAllowCredentials(true);
            return cfg;
        };
    }

    /****
     * @description 认证核心配置
     * @return
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        SecurityWebFilterChain chain =
            //跨域处理
            http.cors()
            .and()
                //指定当前的以表单提交方式进行校验
                .formLogin()
                .loginPage(securityProperties.getLoginPage())
                // 登陆认证
                .authenticationManager(jwtReactiveAuthenticationManager)
                // 登录成功handler
                .authenticationSuccessHandler(jsonServerAuthenticationSuccessHandler)
                // 登陆失败handler
                .authenticationFailureHandler(jsonServerAuthenticationFailureHandler)
                // 匿名访问处理
                .authenticationEntryPoint(jsonServerAuthenticationEntryPoint)
            .and()
                .logout()
                // 登出成功handler
                .logoutSuccessHandler(jsonServerLogoutSuccessHandler)
            .and()
                .csrf().disable()
                .httpBasic().disable()
                // 匿名资源放行
                .authorizeExchange().pathMatchers(securityProperties.getIgnoreUrl().toArray(new String[securityProperties.getIgnoreUrl().size()])).permitAll()
                // 访问权限控制
                .anyExchange().access(jwtReactiveAuthorizationManager)
            .and()
                .build();
        // 设置自定义登录参数转换器
        chain.getWebFilters()
                .filter(webFilter -> webFilter instanceof AuthenticationWebFilter)
                .subscribe(webFilter -> {
                    AuthenticationWebFilter filter = (AuthenticationWebFilter) webFilter;
                    filter.setServerAuthenticationConverter(reactiveServerAuthenticationConverter);
                });
        return chain;
    }

}
