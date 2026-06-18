package in.ac.iiitb.contest.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import in.ac.iiitb.contest.session.SessionAuthFilter;
import in.ac.iiitb.contest.session.SessionService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<SessionAuthFilter> sessionAuthFilter(SessionService sessions, ObjectMapper json) {
        FilterRegistrationBean<SessionAuthFilter> reg =
                new FilterRegistrationBean<>(new SessionAuthFilter(sessions, json));
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }
}

/*
* At a high level, this code ensures that every single request coming into your application passes through your custom
* session authentication logic very early in the request lifecycle.
* When a user makes a request to any endpoint in your app, Spring routes it through a chain of filters. Because of this
* configuration, Spring will trigger your SessionAuthFilter almost immediately. That filter will likely use the
* ObjectMapper to read the request and the SessionService to validate the user's session before allowing the request to
* proceed to your application logic.
* */

