package musarum.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class OwnerBootstrap implements ApplicationRunner {

    private final UserService userService;
    private final String ownerNick;
    private final String ownerPassword;

    OwnerBootstrap(UserService userService,
                   @Value("${MUSARUM_OWNER_NICK}") String ownerNick,
                   @Value("${MUSARUM_OWNER_PASSWORD}") String ownerPassword) {
        this.userService = userService;
        this.ownerNick = ownerNick;
        this.ownerPassword = ownerPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        userService.createAdminIfNoneExists(ownerNick, ownerPassword);
    }
}