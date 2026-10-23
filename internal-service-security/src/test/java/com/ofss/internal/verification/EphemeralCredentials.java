package com.ofss.internal.verification;

import com.ofss.internal.InternalServiceVerifier;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.security.KeyPair;
import java.time.Instant;
import java.util.*;

/** Test artifact ONLY. Never packaged in a receiver's executable production jar. */
public final class EphemeralCredentials {
    public static final String ISSUER="ephemeral-http-oracle-test-issuer";
    private final KeyPair current=Jwts.SIG.RS256.keyPair().build(), next=Jwts.SIG.RS256.keyPair().build(), unknown=Jwts.SIG.RS256.keyPair().build();
    private final String userSecret=HexFormat.of().formatHex(Jwts.SIG.HS512.key().build().getEncoded());
    public String userSecret(){return userSecret;}
    public String publicKeys(){return "{\"current\":\""+Base64.getEncoder().encodeToString(current.getPublic().getEncoded())+"\",\"next\":\""+Base64.getEncoder().encodeToString(next.getPublic().getEncoded())+"\"}";}
    public String service(String audience,String identity,List<String> permissions){return service(audience,identity,permissions,"valid");}
    public String service(String audience,String identity,List<String> permissions,String variation){
        var b=Jwts.builder().header().type(InternalServiceVerifier.TYPE).keyId("current").and().issuer(ISSUER).audience().add(audience).and()
            .subject(identity).claim("principalType","SERVICE").claim("permissions",permissions).expiration(Date.from(Instant.now().plusSeconds(300)));
        switch(variation){case "issuer"->b.issuer("untrusted-test-issuer");case "expired"->b.expiration(Date.from(Instant.now().minusSeconds(30)));
            case "missing-sub"->b.subject(null);case "missing-exp"->b.expiration(null);case "missing-type"->b.claim("principalType",null);
            case "missing-permissions"->b.claim("permissions",null);case "unknown-kid"->b.header().keyId("unknown").and();case "next"->b.header().keyId("next").and();}
        var key=variation.equals("next")?next:variation.equals("wrong-key")?unknown:current;
        return b.signWith(key.getPrivate(),Jwts.SIG.RS256).compact();
    }
    /** Ephemeral test user; not a genuine security-service/MFA token. */
    public String user(){return Jwts.builder().subject("ephemeral-test-manager").claim("userId",42).claim("roles",List.of("FINANCE_MANAGER"))
        .expiration(Date.from(Instant.now().plusSeconds(300))).signWith(Keys.hmacShaKeyFor(userSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();}
    @Override public String toString(){return "Ephemeral test credentials [values withheld]";}
}
