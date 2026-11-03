package musarum.backend.library.exception;

/**
 * El OWNER intentó añadir una carpeta que no vive bajo MUSARUM_MUSIC_PATH.
 * Dentro de Docker esa carpeta no sería visible aunque exista en el host.
 */
public class FolderOutsideUmbrellaException extends RuntimeException {
    public FolderOutsideUmbrellaException(String path, String umbrella) {
        super("La carpeta '" + path + "' no está bajo la carpeta raíz '" + umbrella + "'");
    }
}
