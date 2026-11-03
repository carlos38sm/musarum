package musarum.backend.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService { // MÁQUINA DE TOKENS = FABRICA Y LEE TOKENS (TODA CONFIG DE TOKENS VIVE AQUÍ)

	private final SecretKey secretKey; // LLAVE QUE FIRMA TOKENS
    private final long expirationMillis;

    JwtService(@Value("${JWT_SECRET}") String secret,
               @Value("${JWT_EXPIRATION}") long expirationMillis) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }
    
    public String generateToken(String nick, String userRole) {
    	Instant now = Instant.now();
    	Instant expiry = now.plusMillis(expirationMillis);
    	
    	return Jwts.builder()
    			.subject(nick)
    			.claim("userRole", userRole)
    			.issuedAt(Date.from(now))
    			.expiration(Date.from(expiry))
    			.signWith(secretKey)
    			.compact();
    }
    
    public boolean isTokenValid(String token) {
    	try {
    		parseClaims(token);
    		return true;
    	} catch (JwtException e) {
    		return false;
    	}
    }
    
    public String extractNick(String token) {
    	return parseClaims(token).getSubject();
    }
    
    public String extractUserRole(String token) {
    	return parseClaims(token).get("userRole", String.class);
    }
    
    private Claims parseClaims(String token) {
    	return Jwts.parser()
    			.verifyWith(secretKey)
    			.build()
    			.parseSignedClaims(token)
    			.getPayload();
    }
}