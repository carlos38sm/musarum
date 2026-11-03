package emendatium.backend.shared;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;

@Slf4j
public final class FilenameParser {

    private FilenameParser() {}

    public record Parsed(List<String> artists, String title) {
        public boolean hasArtist() { return !artists.isEmpty(); }
    }

    public static Parsed parse(Path audio) {
        if (audio == null) {
            log.warn("FilenameParser: audio == null");
            return new Parsed(List.of(), null);
        }
        String name = audio.getFileName().toString();
        log.debug("FilenameParser: raw name = '{}'", name);

        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);

        int sep = name.indexOf(" - ");
        if (sep < 0) {
            return new Parsed(List.of(), name.trim());
        }

        String artistPart = name.substring(0, sep).trim();
        String titlePart  = name.substring(sep + 3).trim();

        List<String> artists = List.of(artistPart.split("\\s*,\\s*"))
                .stream()
                .filter(s -> !s.isBlank())
                .toList();

        return new Parsed(artists, titlePart);
    }

    public static boolean artistMatches(String fromFile, String fromApi) {
        if (fromFile == null || fromApi == null) return false;
        String a = normalize(fromFile);
        String b = normalize(fromApi);
        if (a.equals(b)) return true;
        return a.contains(b) || b.contains(a);
    }

    public static boolean anyArtistMatches(List<String> fileArtists, String apiArtist) {
        if (apiArtist == null || fileArtists.isEmpty()) return false;
        return fileArtists.stream().anyMatch(fa -> artistMatches(fa, apiArtist));
    }

    /**
     * Normaliza para comparación tolerante:
     *   - trim + lowercase
     *   - sin diacríticos (JAÝ-Z ≡ JAY-Z)
     *   - sin "the " inicial (The Beatles ≡ Beatles)
     *   - puntuación/separadores (- _ . · · /) convertidos a espacio
     *     (Jay-Z ≡ Jay Z, AC/DC ≡ AC DC)
     *   - espacios múltiples colapsados
     */
    private static String normalize(String s) {
        String n = Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (n.startsWith("the ")) n = n.substring(4);
        n = n.replaceAll("[\\-_·./]+", " ");
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }
}
