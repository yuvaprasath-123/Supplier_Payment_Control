package com.ofss.internal;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalAuthenticationFilterTest {
    final InternalServiceVerifier verifier=mock(InternalServiceVerifier.class);
    final InternalErrorWriter errors=mock(InternalErrorWriter.class);
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"B","Bearer","Bearer ","Bearer\tfixture","Basic fixture"})
    void missingOrUnsupportedCredentialsDoNotReachVerification(String header) throws Exception {
        when(verifier.enabled()).thenReturn(true);var request=new MockHttpServletRequest("GET","/internal/v1/configuration/active");if(header!=null)request.addHeader("Authorization",header);
        var response=new MockHttpServletResponse();var chain=mock(jakarta.servlet.FilterChain.class);
        new InternalAuthenticationFilter(verifier,errors).doFilter(request,response,chain);
        verify(errors).unauthorized(request,response,false);verify(verifier,never()).authenticate(org.mockito.ArgumentMatchers.anyString());verifyNoInteractions(chain);
    }
    @Test void failedVerificationIsAnInvalidTokenAndDoesNotReflectItsCause() throws Exception {
        when(verifier.enabled()).thenReturn(true);when(verifier.authenticate("fixture")).thenThrow(new IllegalArgumentException("private token detail"));
        var request=new MockHttpServletRequest("GET","/internal/v1/configuration/active");request.addHeader("Authorization","Bearer fixture");var response=new MockHttpServletResponse();var chain=mock(jakarta.servlet.FilterChain.class);
        new InternalAuthenticationFilter(verifier,errors).doFilter(request,response,chain);verify(errors).unauthorized(request,response,true);verifyNoInteractions(chain);assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void downstreamDataFailureIsNotMisclassifiedAsAuthenticationFailure() throws Exception {
        when(verifier.enabled()).thenReturn(true);when(verifier.authenticate("fixture")).thenReturn(UsernamePasswordAuthenticationToken.authenticated(new ServicePrincipal("service"),null,List.of()));
        var request=new MockHttpServletRequest("GET","/internal/v1/configuration/active");request.addHeader("Authorization","BEARER fixture");var response=new MockHttpServletResponse();var chain=mock(jakarta.servlet.FilterChain.class);
        var failure=new IllegalArgumentException("private data failure");doThrow(failure).when(chain).doFilter(request,response);
        assertThatThrownBy(()->new InternalAuthenticationFilter(verifier,errors).doFilter(request,response,chain)).isSameAs(failure);verifyNoInteractions(errors);
    }
}
