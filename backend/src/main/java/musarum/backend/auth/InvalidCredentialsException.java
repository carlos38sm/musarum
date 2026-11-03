package musarum.backend.auth;

@SuppressWarnings("serial")
public class InvalidCredentialsException extends RuntimeException{
    public InvalidCredentialsException() {
        super("Nick o contraseña incorrectos");
    }
}