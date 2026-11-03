package musarum.backend.user.exception;

@SuppressWarnings("serial")
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long id) {
        super("NO EXISTE USUARIO CON ID: " + id);
    }
    
    public UserNotFoundException(String nick) {
        super("No existe el usuario con nick " + nick);
    }
}