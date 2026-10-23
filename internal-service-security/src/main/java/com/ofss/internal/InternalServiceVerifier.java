package com.ofss.internal;

import io.jsonwebtoken.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Dedicated asymmetric trust domain. No issuance, remote key lookup, user-key fallback or default grants. */
public final class InternalServiceVerifier {
    public static final String TYPE = "service+jwt";
    private final boolean enabled;
    private final JwtParser parser;
    private final String audience;
    private final Map<String,Set<String>> grants;

    public static InternalServiceVerifier from(Environment env, Set<String> supportedPermissions) {
        return new InternalServiceVerifier(env.getProperty("internal-access.enabled", Boolean.class, false),
                env.getProperty("internal-access.issuer", ""), env.getProperty("internal-access.audience", ""),
                env.getProperty("internal-access.public-keys", ""), env.getProperty("internal-access.service-permissions", ""), supportedPermissions);
    }
    public InternalServiceVerifier(boolean enabled, String issuer, String audience, String publicKeysJson,
            String servicePermissionsJson, Set<String> supportedPermissions) {
        this.enabled = enabled;
        if (!enabled) { this.parser=null; this.audience=null; this.grants=Map.of(); return; }
        try {
            requireText(issuer); requireText(audience);
            var mapper=JsonMapper.builder().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
            Object keyValue=mapper.readValue(publicKeysJson,Object.class);
            Object grantValue=mapper.readValue(servicePermissionsJson,Object.class);
            if (!(keyValue instanceof Map<?,?> keyMap) || keyMap.isEmpty() || !(grantValue instanceof Map<?,?> grantMap) || grantMap.isEmpty()
                    || supportedPermissions==null || supportedPermissions.isEmpty()) throw invalid();
            var keys=new HashMap<String,PublicKey>();
            for(var entry:keyMap.entrySet()) {
                if(!(entry.getKey() instanceof String kid) || !kid.matches("[A-Za-z0-9._-]{1,64}") || !(entry.getValue() instanceof String encoded)) throw invalid();
                PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
                if(!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength()<2048) throw invalid();
                keys.put(kid,key);
            }
            var allowed=new HashMap<String,Set<String>>();
            for(var entry:grantMap.entrySet()) {
                if(!(entry.getKey() instanceof String identity) || !identity.matches("[a-z][a-z0-9-]{0,63}")) throw invalid();
                Set<String> permissions=permissions(entry.getValue());
                if(!supportedPermissions.containsAll(permissions)) throw invalid();
                allowed.put(identity,permissions);
            }
            this.grants=Map.copyOf(allowed); this.audience=audience;
            var pinnedKeys=Map.copyOf(keys);
            this.parser=Jwts.parser().keyLocator(header->{
                if(!"RS256".equals(header.getAlgorithm()) || !(header.get("kid") instanceof String kid)) throw invalid();
                PublicKey key=pinnedKeys.get(kid); if(key==null) throw invalid(); return key;
            }).requireIssuer(issuer).requireAudience(audience).build();
        } catch(Exception exception) {
            // Never attach a parsing/key exception: its message may include external configuration.
            throw new IllegalStateException("Complete valid external internal-access configuration is required");
        }
    }
    public boolean enabled() { return enabled; }
    public UsernamePasswordAuthenticationToken authenticate(String token) {
        if(!enabled) throw invalid();
        var signed=parser.parseSignedClaims(token); var claims=signed.getPayload();
        if(!TYPE.equals(signed.getHeader().getType()) || !"SERVICE".equals(claims.get("principalType"))
                || claims.containsKey("roles") || claims.containsKey("userId") || claims.getExpiration()==null
                || !claims.getExpiration().toInstant().isAfter(Instant.now()) || !Set.of(audience).equals(claims.getAudience())) throw invalid();
        String identity=claims.getSubject();
        if(identity==null || identity.isBlank()) throw invalid();
        Set<String> allowed=grants.get(identity); if(allowed==null) throw invalid();
        var requested=permissions(claims.get("permissions"));
        if(!allowed.containsAll(requested)) throw invalid();
        var authorities=requested.stream().sorted().map(p->new SimpleGrantedAuthority(ServicePermissions.authority(p))).toList();
        return UsernamePasswordAuthenticationToken.authenticated(new ServicePrincipal(identity),null,authorities);
    }
    private static Set<String> permissions(Object value) {
        if(!(value instanceof List<?> list) || list.isEmpty()) throw invalid();
        var result=new HashSet<String>();
        for(Object item:list) { if(!(item instanceof String name) || name.isBlank() || !result.add(name)) throw invalid(); }
        return Set.copyOf(result);
    }
    private static void requireText(String text) { if(text==null || text.isBlank() || !text.equals(text.trim())) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid service authentication"); }
}
