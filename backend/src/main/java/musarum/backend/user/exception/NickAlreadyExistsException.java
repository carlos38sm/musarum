package musarum.backend.user.exception;

@SuppressWarnings("serial")
public class NickAlreadyExistsException extends RuntimeException {
    public NickAlreadyExistsException(String nick) {
        super("EL NICK '" + nick + "' SE ENCUENTRA EN USO");
    }
}