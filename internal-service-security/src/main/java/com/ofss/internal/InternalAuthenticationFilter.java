package com.ofss.internal;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Installed only in the /internal/** security chain, never as a global servlet filter. */
public final class InternalAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern BEARER=Pattern.compile("(?i:Bearer) +([^\\s]+)");
    private final InternalServiceVerifier verifier;
    private final InternalErrorWriter errors;
    public InternalAuthenticationFilter(InternalServiceVerifier verifier, InternalErrorWriter errors) { this.verifier=verifier; this.errors=errors; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        SecurityContextHolder.clearContext();
        if(!verifier.enabled()) { errors.write(request,response,404,"INTERNAL_ACCESS_DISABLED","Internal access is unavailable"); return; }
        String header=request.getHeader("Authorization");
        if(header==null || !header.regionMatches(true,0,"Bearer",0,6) || (header.length()>6 && header.charAt(6)!=' ') || header.substring(6).isBlank()) {
            errors.unauthorized(request,response,false); return;
        }
        try {
            var match=BEARER.matcher(header); if(!match.matches()) throw new IllegalArgumentException("Invalid service authentication");
            var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(verifier.authenticate(match.group(1)));SecurityContextHolder.setContext(context);
        } catch(JwtException|IllegalArgumentException exception) {
            SecurityContextHolder.clearContext(); errors.unauthorized(request,response,true); return;
        }
        // Database failures must remain outside the authentication catch.
        chain.doFilter(request,response);
    }
}
