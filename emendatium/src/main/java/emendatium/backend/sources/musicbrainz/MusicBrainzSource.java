package emendatium.backend.sources.musicbrainz;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.shared.Values;
import emendatium.backend.sources.EnrichmentContext;
import emendatium.backend.sources.MetadataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@Order(1)
@RequiredArgsConstructor
public class MusicBrainzSource implements MetadataSource {

    private static final List<String> TYPE_PRIORITY =
            List.of("Album", "EP", "Single", "Broadcast", "Other");

    private static final Set<String> SECUNDARIOS_PENALIZADOS = Set.of(
            "compilation", "live", "remix", "dj-mix", "mixtape/street",
            "soundtrack", "demo", "interview", "audiobook", "audio drama", "spokenword");

    /** Palabras en el título del álbum que sugieren recopilatorio disfrazado. */
    private static final List<String> COMPILATION_HINTS = List.of(
            "power hits", "top 40", "100% ", "now that's what", "hits of",
            "greatest hits", "the very best", "essential", "biggest hits",
            "chart hits", "party hits", "summer hits", "hits collection");

    private final MusicBrainzClient client;
    private final EnricherProperties props;

    @Override
    public String name() { return "MusicBrainz"; }

    @Override
    public Optional<TrackMetadata> enrich(EnrichmentContext context) {
        if (context.recordingMbid() == null) return Optional.empty();
        return client.lookupRecording(context.recordingMbid()).map(this::toMetadata);
    }

    private TrackMetadata toMetadata(MusicBrainzResponse r) {
        String artist = null, artistMbid = null;
        if (r.artistCredit() != null && !r.artistCredit().isEmpty()) {
            var primero = r.artistCredit().get(0).artist();
            if (primero != null) {
                artist = Values.clean(primero.name());
                artistMbid = primero.id();
            }
        }

        MusicBrainzResponse.Release release = mejorRelease(r.releases(), r.isrcs());

        String album = null, releaseType = null, releaseId = null, releaseGroupId = null;
        String barcode = null, catalogNumber = null, label = null;
        String albumArtist = null;
        LocalDate releaseDate = null;
        Integer year = null, originalYear = null, trackNumber = null, trackTotal = null;
        Integer discNumber = null, discTotal = null;

        if (release != null) {
            album = Values.clean(release.title());
            releaseId = release.id();
            barcode = Values.clean(release.barcode());
            if (release.releaseGroup() != null) {
                releaseGroupId = release.releaseGroup().id();
                releaseType = release.releaseGroup().primaryType();
                originalYear = parseYear(release.releaseGroup().firstReleaseDate());
            }
            releaseDate = parseFecha(release.date());
            if (releaseDate != null) year = releaseDate.getYear();
            else year = parseYear(release.date());

            // Pista + disco + totales
            PistaInfo info = buscarInfoPista(release, r.id());
            trackNumber = info.trackNumber();
            trackTotal  = info.trackTotal();
            discNumber  = info.discNumber();
            discTotal   = info.discTotal();

            // albumArtist desde el release si lo trae; si no, el artista de la grabación
            albumArtist = (release.artistCredit() != null && !release.artistCredit().isEmpty()
                    && release.artistCredit().get(0).artist() != null)
                    ? Values.clean(release.artistCredit().get(0).artist().name())
                    : artist;

            var labelInfo = client.lookupReleaseLabels(releaseId)
                    .map(MusicBrainzResponse.Release::labelInfo)
                    .filter(list -> list != null && !list.isEmpty())
                    .map(list -> list.get(0));
            if (labelInfo.isPresent()) {
                catalogNumber = Values.clean(labelInfo.get().catalogNumber());
                if (labelInfo.get().label() != null) label = Values.clean(labelInfo.get().label().name());
            }
        }

        String isrc = (r.isrcs() != null && !r.isrcs().isEmpty()) ? Values.clean(r.isrcs().get(0)) : null;

        return TrackMetadata.builder()
                .title(Values.clean(r.title()))
                .artist(artist)
                .albumArtist(albumArtist)
                .mbArtistId(artistMbid)
                .mbRecordingId(r.id())
                .mbReleaseId(releaseId)
                .mbReleaseGroupId(releaseGroupId)
                .album(album)
                .releaseType(releaseType)
                .barcode(barcode)
                .catalogNumber(catalogNumber)
                .label(label)
                .releaseDate(releaseDate)
                .year(year)
                .originalYear(originalYear)
                .trackNumber(trackNumber)
                .trackTotal(trackTotal)
                .discNumber(discNumber)
                .discTotal(discTotal)
                .isrc(isrc)
                .genres(mejoresGeneros(r))
                .build();
    }

    private record PistaInfo(Integer trackNumber, Integer trackTotal,
                             Integer discNumber, Integer discTotal) {}

    /**
     * Elige el release más canónico. Prioriza:
     *   1. NO ser compilación (ni por tipo secundario, ni por título sospechoso)
     *   2. Álbum > EP > Single
     *   3. TENER metadatos ricos (barcode, ISRC de la grabación disponible)
     *   4. Ser edición de la región preferida del usuario (si está configurada)
     *   5. Ser oficial
     *   6. El más antiguo
     */
    private MusicBrainzResponse.Release mejorRelease(
            List<MusicBrainzResponse.Release> releases,
            List<String> recordingIsrcs) {
        if (releases == null || releases.isEmpty()) return null;

        boolean hayIsrc = recordingIsrcs != null && !recordingIsrcs.isEmpty();

        return releases.stream()
                .min(Comparator
                        .comparingInt(this::penalizacionSecundaria)
                        .thenComparingInt(this::penalizacionTituloSospechoso)
                        .thenComparingInt(this::rankTipo)
                        .thenComparingInt(rel -> puntuacionMetadatos(rel, hayIsrc))
                        .thenComparingInt(this::rankRegion)
                        .thenComparingInt(this::rankEstado)
                        .thenComparing(rel -> (rel.date() == null || rel.date().isBlank())
                                ? "9999-99-99" : rel.date()))
                .orElse(releases.get(0));
    }

    /** 0 si el release es del país preferido, 1 en caso contrario. */
    private int rankRegion(MusicBrainzResponse.Release rel) {
        String preferido = props.sources().musicbrainz().preferredCountry();
        if (preferido == null || preferido.isBlank()) return 0;
        return preferido.equalsIgnoreCase(rel.country()) ? 0 : 1;
    }

    private int penalizacionSecundaria(MusicBrainzResponse.Release rel) {
        if (rel.releaseGroup() == null || rel.releaseGroup().secondaryTypes() == null) return 0;
        for (String s : rel.releaseGroup().secondaryTypes()) {
            if (s != null && SECUNDARIOS_PENALIZADOS.contains(s.toLowerCase())) return 1;
        }
        return 0;
    }

    /** Detecta títulos de release que huelen a recopilatorio comercial. */
    private int penalizacionTituloSospechoso(MusicBrainzResponse.Release rel) {
        String t = rel.title();
        if (t == null) return 0;
        String tl = t.toLowerCase();
        for (String hint : COMPILATION_HINTS) {
            if (tl.contains(hint)) return 1;
        }
        return 0;
    }

    private int rankTipo(MusicBrainzResponse.Release rel) {
        String tipo = (rel.releaseGroup() != null) ? rel.releaseGroup().primaryType() : null;
        int idx = (tipo != null) ? TYPE_PRIORITY.indexOf(tipo) : -1;
        return (idx >= 0) ? idx : TYPE_PRIORITY.size();
    }

    /**
     * Menos puntuación = mejor. Premiamos releases con barcode
     * (indicador fuerte de que es una edición comercial "oficial").
     */
    private int puntuacionMetadatos(MusicBrainzResponse.Release rel, boolean hayIsrc) {
        int score = 0;
        if (Values.clean(rel.barcode()) == null) score += 2;
        if (Values.clean(rel.country()) == null) score += 1;
        return score;
    }

    private int rankEstado(MusicBrainzResponse.Release rel) {
        return "Official".equalsIgnoreCase(rel.status()) ? 0 : 1;
    }

    private LocalDate parseFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) return null;
        try {
            if (fecha.length() == 4) return LocalDate.of(Integer.parseInt(fecha), 1, 1);
            if (fecha.length() == 7) return LocalDate.parse(fecha + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
            return LocalDate.parse(fecha, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Integer parseYear(String fecha) {
        if (fecha != null && fecha.length() >= 4 && fecha.substring(0, 4).matches("\\d{4}")) {
            return Integer.parseInt(fecha.substring(0, 4));
        }
        return null;
    }

    /**
     * Localiza la pista en el release y devuelve número de pista, total de
     * pistas en su disco, número de disco (1-based) y total de discos.
     */
    private PistaInfo buscarInfoPista(MusicBrainzResponse.Release release, String recordingId) {
        if (release.media() == null || release.media().isEmpty()) {
            return new PistaInfo(null, null, null, null);
        }
        int discTotal = release.media().size();

        for (int i = 0; i < release.media().size(); i++) {
            MusicBrainzResponse.Medium medium = release.media().get(i);
            if (medium.tracks() == null) continue;

            Integer trackTotal = medium.tracks().size();
            for (MusicBrainzResponse.Track t : medium.tracks()) {
                if (t.recording() != null && recordingId.equals(t.recording().id())) {
                    return new PistaInfo(t.position(), trackTotal, i + 1, discTotal);
                }
            }
        }
        return new PistaInfo(null, null, null, discTotal);
    }

    private List<String> mejoresGeneros(MusicBrainzResponse r) {
        if (r.genres() != null && !r.genres().isEmpty()) {
            return r.genres().stream()
                    .sorted(Comparator.comparing(MusicBrainzResponse.Genre::count).reversed())
                    .map(MusicBrainzResponse.Genre::name).limit(3).toList();
        }
        if (r.tags() != null && !r.tags().isEmpty()) {
            return r.tags().stream()
                    .sorted(Comparator.comparing(MusicBrainzResponse.Tag::count).reversed())
                    .map(MusicBrainzResponse.Tag::name).limit(3).toList();
        }
        return List.of();
    }
}