package com.ofss.internal.verification;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.io.IOException;
import java.util.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Loopback synchronous requests only; never print tokens, response bodies, exception payloads or private evidence. */
public final class InternalReadHttp implements AutoCloseable {
    private final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
    private final Runnable uncertain;
    private final ObjectReader reader=JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build().readerFor(Object.class);
    public record Reply(int status,Map<String,Object> body) {}
    public InternalReadHttp(Runnable uncertain){this.uncertain=uncertain;}
    public static URI target(URI base,String path){
        if(base==null||!"http".equals(base.getScheme())||!"127.0.0.1".equals(base.getHost())||base.getPort()<1||base.getUserInfo()!=null||base.getRawQuery()!=null||base.getRawFragment()!=null
            ||!(base.getRawPath().isEmpty()||base.getRawPath().equals("/"))||path==null||!path.matches("/(?:internal/v1|api/v1)/[A-Za-z0-9/_-]+")||path.contains(".."))
            throw new IllegalArgumentException("Only loopback verification routes are permitted");
        return base.resolve(path);
    }
    @SuppressWarnings("unchecked") public Reply get(URI base,String path,String token){
        var request=HttpRequest.newBuilder(target(base,path)).timeout(Duration.ofSeconds(15)).header("Accept","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        final HttpResponse<String> response;
        try{response=client.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());}
        catch(IOException e){uncertain.run();throw new AssertionError("HTTP completion uncertain; defer fixture cleanup; values withheld");}
        catch(InterruptedException e){uncertain.run();Thread.currentThread().interrupt();throw new AssertionError("HTTP interrupted; defer fixture cleanup; values withheld");}
        if(!response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))throw new AssertionError("Expected JSON response; body withheld");
        try{Object body=reader.readValue(response.body());if(!(body instanceof Map<?,?>))throw new IllegalArgumentException();return new Reply(response.statusCode(),(Map<String,Object>)body);}
        catch(RuntimeException e){throw new AssertionError("Invalid JSON verification response; body withheld");}
    }
    @Override public void close(){
        client.shutdown();
        try{if(!client.awaitTermination(Duration.ofSeconds(10))){client.shutdownNow();if(!client.awaitTermination(Duration.ofSeconds(5))){uncertain.run();throw new AssertionError("HTTP workers not terminated; cleanup deferred");}}}
        catch(InterruptedException e){uncertain.run();Thread.currentThread().interrupt();throw new AssertionError("HTTP worker shutdown interrupted; cleanup deferred");}
    }
}
