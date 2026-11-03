package musarum.backend.shared.security;

@SuppressWarnings("serial")
public class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException() {
        super("No autenticado");
    }
}