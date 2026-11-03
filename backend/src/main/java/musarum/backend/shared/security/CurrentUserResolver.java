package musarum.backend.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import musarum.backend.user.UserService;

@Component
public class CurrentUserResolver {

    private final UserService userService;

    CurrentUserResolver(UserService userService) {
        this.userService = userService;
    }

    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) throw new UnauthenticatedException();
        String nick = auth.getName();
        return userService.findEntityByNick(nick).getId();
    }
}