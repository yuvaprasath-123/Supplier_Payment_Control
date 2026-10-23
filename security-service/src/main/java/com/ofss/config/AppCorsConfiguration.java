package com.ofss.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class AppCorsConfiguration {

	@Bean
	CorsConfigurationSource corsConfigurationSource() {

	    CorsConfiguration configuration =
	            new CorsConfiguration();

	    configuration.setAllowedOriginPatterns(
	            List.of(
	                    "http://localhost:*",
	                    "https://*.trycloudflare.com"
	            )
	    );

	    configuration.setAllowedMethods(
	            List.of(
	                    "GET",
	                    "POST",
	                    "PUT",
	                    "PATCH",
	                    "DELETE",
	                    "OPTIONS"
	            )
	    );

	    configuration.setAllowedHeaders(
	            List.of("*")
	    );

	    configuration.setAllowCredentials(true);

	    UrlBasedCorsConfigurationSource source =
	            new UrlBasedCorsConfigurationSource();

	    source.registerCorsConfiguration(
	            "/**",
	            configuration
	    );

	    return source;
	}
}