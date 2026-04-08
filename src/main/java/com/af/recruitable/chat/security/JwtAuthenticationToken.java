package com.af.recruitable.chat.security;
import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
@Getter
public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final UUID userId;
    private final String token;
    private final String email;
    private final List<String> roles;
    private final List<String> permissions;
    private final UUID organizationId;
    public JwtAuthenticationToken(UUID userId, String token,
            Collection<? extends GrantedAuthority> authorities,
            String email, List<String> roles, List<String> permissions,
            UUID organizationId) {
        super(authorities);
        this.userId = userId;
        this.token = token;
        this.email = email;
        this.roles = roles;
        this.permissions = permissions;
        this.organizationId = organizationId;
        setAuthenticated(true);
    }
    @Override public Object getPrincipal() { return userId; }
    @Override public Object getCredentials() { return token; }
    @Override public String getName() { return email; }
}
