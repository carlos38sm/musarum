package musarum.backend.scanner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import musarum.backend.album.Album;
import musarum.backend.album.AlbumRepository;
import musarum.backend.album.AlbumService;
import musarum.backend.artist.Artist;
import musarum.backend.artist.ArtistRepository;
import musarum.backend.artist.ArtistService;
import musarum.backend.genre.Genre;
import musarum.backend.genre.GenreRepository;
import musarum.backend.genre.GenreService;
import musarum.backend.shared.Normalizer;

/**
 * Catálogo pre-cargado al inicio de un scan para romper el N+1 que sufría
 * {@code importSingleSong}. Antes, cada canción llamaba a
 * {@code artistService.findOrCreate(name)} (SELECT + posible INSERT) por
 * cada artista, {@code albumService.findOrCreate(title, artist)} por el
 * álbum y {@code genreService.findOrCreate(name)} por el género. Multiplicado
 * por ~7000 canciones y una media de 2 artistas, son del orden de 20k queries
 * redundantes contra Postgres.
 *
 * <p>Esta clase carga en memoria los pares (nameKey → artistId),
 * (artistId:titleKey → albumId) y (nameLower → genreId) con proyecciones
 * ligeras (no entidades enteras) ANTES del bucle. {@code resolve*} consulta
 * primero el mapa — O(1) — y solo delega en el service cuando realmente
 * falta, ahí sí abre una tx corta para el INSERT. El mapa se actualiza tras
 * crear, de modo que la siguiente canción con el mismo artista lo encuentra
 * en memoria.
 *
 * <p>No es thread-safe para INSERT concurrente — solo para lectura. Hoy el
 * bucle de import es secuencial, así que basta con esto. Si se paraleliza
 * el import, {@link #resolveArtist} y familia necesitan sincronización
 * explícita en la rama "falta y hay que crear".
 */
public class ScannerCatalog {

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final GenreRepository genreRepository;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final GenreService genreService;

    private final Map<String, Long> artistIdByKey;
    private final Map<String, Long> albumIdByCombo;  // "artistId:titleKey"
    private final Map<String, Long> genreIdByLower;

    public ScannerCatalog(ArtistRepository artistRepository,
                          AlbumRepository albumRepository,
                          GenreRepository genreRepository,
                          ArtistService artistService,
                          AlbumService albumService,
                          GenreService genreService) {
        this.artistRepository = artistRepository;
        this.albumRepository = albumRepository;
        this.genreRepository = genreRepository;
        this.artistService = artistService;
        this.albumService = albumService;
        this.genreService = genreService;

        this.artistIdByKey = new ConcurrentHashMap<>();
        this.albumIdByCombo = new ConcurrentHashMap<>();
        this.genreIdByLower = new ConcurrentHashMap<>();

        for (Object[] row : artistRepository.findIdAndNameKey()) {
            artistIdByKey.put((String) row[1], (Long) row[0]);
        }
        for (Object[] row : albumRepository.findIdTitleKeyAndArtistId()) {
            Long albumId = (Long) row[0];
            String titleKey = (String) row[1];
            Long artistId = (Long) row[2];
            albumIdByCombo.put(artistId + ":" + titleKey, albumId);
        }
        for (Object[] row : genreRepository.findIdAndName()) {
            genreIdByLower.put(((String) row[1]).toLowerCase(), (Long) row[0]);
        }
    }

    /**
     * Devuelve el Artist (proxy managed si ya existe, entidad real si se
     * acaba de crear). Normaliza el nombre igual que {@code ArtistService}
     * para que la clave coincida con lo que está en DB.
     */
    public Artist resolveArtist(String name) {
        String key = Normalizer.key(name);
        Long cachedId = artistIdByKey.get(key);
        if (cachedId != null) {
            // getReferenceById devuelve un proxy sin SELECT. Hibernate solo
            // dispara la query si tocamos un campo que no sea el id. Como
            // en importSingleSong solo lo usamos como FK (song.artists.add),
            // la query de carga no sucede.
            return artistRepository.getReferenceById(cachedId);
        }
        Artist created = artistService.findOrCreate(name);
        artistIdByKey.put(key, created.getId());
        return created;
    }

    public Album resolveAlbum(String title, Artist artist) {
        String titleKey = Normalizer.key(title);
        String combo = artist.getId() + ":" + titleKey;
        Long cachedId = albumIdByCombo.get(combo);
        if (cachedId != null) {
            return albumRepository.getReferenceById(cachedId);
        }
        Album created = albumService.findOrCreate(title, artist);
        albumIdByCombo.put(combo, created.getId());
        return created;
    }

    public Genre resolveGenre(String name) {
        String lower = name.toLowerCase();
        Long cachedId = genreIdByLower.get(lower);
        if (cachedId != null) {
            return genreRepository.getReferenceById(cachedId);
        }
        Genre created = genreService.findOrCreate(name);
        genreIdByLower.put(lower, created.getId());
        return created;
    }
}
