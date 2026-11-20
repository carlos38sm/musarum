package musarum.backend.shared;

import java.time.Instant;
import musarum.backend.album.exception.AlbumNotFoundException;
import musarum.backend.artist.exception.ArtistNotFoundException;
import musarum.backend.auth.InvalidCredentialsException;
import musarum.backend.enrichment.exception.*;
import musarum.backend.favorite.exception.*;
import musarum.backend.genre.exception.GenreNotFoundException;
import musarum.backend.library.exception.FolderNotFoundException;
import musarum.backend.library.exception.FolderOutsideUmbrellaException;
import musarum.backend.playlist.exception.*;
import musarum.backend.scanner.exception.ScanFailedException;
import musarum.backend.shared.security.UnauthenticatedException;
import musarum.backend.song.exception.LyricsNotFoundException;
import musarum.backend.song.exception.SongNotFoundException;
import musarum.backend.streaming.AudioFileNotFoundException;
import musarum.backend.user.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NickAlreadyExistsException.class)
    ResponseEntity<ApiError> handleNickExists(NickAlreadyExistsException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Nick o contraseña incorrectos");
    }

    @ExceptionHandler(ArtistNotFoundException.class)
    ResponseEntity<ApiError> handleArtistNotFound(ArtistNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(GenreNotFoundException.class)
    ResponseEntity<ApiError> handleGenreNotFound(GenreNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AlbumNotFoundException.class)
    ResponseEntity<ApiError> handleAlbumNotFound(AlbumNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SongNotFoundException.class)
    ResponseEntity<ApiError> handleSongNotFound(SongNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LyricsNotFoundException.class)
    ResponseEntity<ApiError> handleLyricsNotFound(LyricsNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AudioFileNotFoundException.class)
    ResponseEntity<ApiError> handleAudioNotFound(AudioFileNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ScanFailedException.class)
    ResponseEntity<ApiError> handleScanFailed(ScanFailedException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(PlaylistNotFoundException.class)
    ResponseEntity<ApiError> handlePlaylistNotFound(PlaylistNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SongAlreadyInPlaylistException.class)
    ResponseEntity<ApiError> handleSongAlreadyIn(SongAlreadyInPlaylistException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SongNotInPlaylistException.class)
    ResponseEntity<ApiError> handleSongNotIn(SongNotInPlaylistException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(FavoriteNotFoundException.class)
    ResponseEntity<ApiError> handleFavoriteNotFound(FavoriteNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SongAlreadyFavoritedException.class)
    ResponseEntity<ApiError> handleSongAlreadyFavorited(SongAlreadyFavoritedException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ApiError> handleUnauthenticated(UnauthenticatedException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(EnrichmentFailedException.class)
    ResponseEntity<ApiError> handleEnrichmentFailed(EnrichmentFailedException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(LyricsWriteException.class)
    ResponseEntity<ApiError> handleLyricsWriteFailed(LyricsWriteException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }
    
    @ExceptionHandler(BatchEnrichmentException.class)
    ResponseEntity<ApiError> handleBatchEnrichmentFailed(BatchEnrichmentException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(FolderNotFoundException.class)
    ResponseEntity<ApiError> handleFolderNotFound(FolderNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(FolderOutsideUmbrellaException.class)
    ResponseEntity<ApiError> handleFolderOutsideUmbrella(FolderOutsideUmbrellaException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Catch-all: cualquier RuntimeException no cubierta por un handler
     * específico cae aquí. SIN esto, Spring hacía forward interno a /error
     * y en modo STATELESS el 401 de /error enmascaraba el 500 real — el
     * móvil veía "401" aunque el controller ya hubiera corrido con éxito.
     * Loguear la stacktrace para diagnosticar la causa raíz después.
     *
     * No capturamos Throwable para no tragar Error (OutOfMemory, etc).
     * RuntimeException cubre todo lo que normalmente sale de controllers.
     */
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiError> handleUnexpectedRuntime(RuntimeException ex) {
        log.error("500 hunt · excepción no manejada en controller: {}", ex.toString(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error inesperado: " + ex.getClass().getSimpleName() +
                        (ex.getMessage() != null ? " · " + ex.getMessage() : ""));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        ApiError error = new ApiError(status.value(), message, Instant.now());
        return ResponseEntity.status(status).body(error);
    }
}