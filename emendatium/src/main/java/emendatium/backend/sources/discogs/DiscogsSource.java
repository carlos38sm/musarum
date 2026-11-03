package emendatium.backend.sources.discogs;

import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.shared.Values;
import emendatium.backend.sources.EnrichmentContext;
import emendatium.backend.sources.MetadataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Order(2)
@RequiredArgsConstructor
public class DiscogsSource implements MetadataSource {

    private final DiscogsClient client;

    @Override
    public String name() {
        return "Discogs";
    }

    @Override
    public Optional<TrackMetadata> enrich(EnrichmentContext context) {
        String artist = firstNonBlank(context.soFar().artist(), context.fallbackArtist());
        String album = firstNonBlank(context.soFar().album(), context.fallbackTitle());
        if (isBlank(artist) || isBlank(album)) return Optional.empty();
        return client.searchRelease(artist, album).map(this::toMetadata);
    }

    private TrackMetadata toMetadata(DiscogsResponse.Result r) {
        List<String> genres = (r.style() != null && !r.style().isEmpty())
                ? r.style() : (r.genre() != null ? r.genre() : List.of());

        return TrackMetadata.builder()
                .discogsReleaseId(r.id())
                .label(Values.clean(first(r.label())))
                .catalogNumber(Values.clean(r.catno()))
                .barcode(Values.clean(first(r.barcode())))
                .year(parseYear(r.year()))
                .genres(genres)
                .build();
    }

    private Integer parseYear(String year) {
        if (year != null && year.matches("\\d{4}")) return Integer.parseInt(year);
        return null;
    }

    private String first(List<String> list) {
        return (list != null && !list.isEmpty()) ? list.get(0) : null;
    }

    private String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : (!isBlank(b) ? b : null);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}