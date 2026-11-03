package emendatium.backend.enrichment;

import emendatium.backend.shared.TrackMetadata;
import org.springframework.stereotype.Component;

/**
 * Combina dos {@link TrackMetadata} con política <b>first-wins</b>:
 * cada campo del resultado es el valor de {@code existing} si NO es {@code null},
 * y el de {@code partial} en caso contrario. Es decir, los peritos solo rellenan
 * huecos — nunca pisan un valor que ya existía.
 *
 * <p>Consecuencias prácticas:
 * <ul>
 *   <li>El orden de los peritos manda. Los que tienen datos más autoritativos
 *       (MusicBrainz, AcoustID, Deezer) deben ejecutarse ANTES que los
 *       heurísticos (análisis local, biografías de respaldo).</li>
 *   <li>Un dato "equivocado" temprano no se corrige con otro perito posterior.
 *       Si un campo necesita ser recalculado, hay que limpiarlo primero.</li>
 *   <li>Para listas y records anidados (genres, artistInfo, coverArt) también
 *       se aplica first-wins sobre el objeto entero, no por sub-campos.</li>
 * </ul>
 *
 * <p>Esto es intencionado: evita sobreescribir datos de fuentes fiables con
 * ruido de fuentes abiertas (TheAudioDB, Last.fm) o estimaciones locales
 * (BPM/key cuando Deezer ya aportó el real).
 */
@Component
public class MetadataMerger {

    public TrackMetadata merge(TrackMetadata existing, TrackMetadata partial) {
        return existing.toBuilder()
                .title(coalesce(existing.title(), partial.title()))
                .artist(coalesce(existing.artist(), partial.artist()))
                .album(coalesce(existing.album(), partial.album()))
                .albumArtist(coalesce(existing.albumArtist(), partial.albumArtist()))
                .releaseType(coalesce(existing.releaseType(), partial.releaseType()))
                .trackNumber(coalesce(existing.trackNumber(), partial.trackNumber()))
                .trackTotal(coalesce(existing.trackTotal(), partial.trackTotal()))
                .discNumber(coalesce(existing.discNumber(), partial.discNumber()))
                .discTotal(coalesce(existing.discTotal(), partial.discTotal()))
                .releaseDate(coalesce(existing.releaseDate(), partial.releaseDate()))
                .year(coalesce(existing.year(), partial.year()))
                .originalYear(coalesce(existing.originalYear(), partial.originalYear()))
                .genres(coalesce(existing.genres(), partial.genres()))
                .composer(coalesce(existing.composer(), partial.composer()))
                .label(coalesce(existing.label(), partial.label()))
                .acoustId(coalesce(existing.acoustId(), partial.acoustId()))
                .mbRecordingId(coalesce(existing.mbRecordingId(), partial.mbRecordingId()))
                .mbReleaseId(coalesce(existing.mbReleaseId(), partial.mbReleaseId()))
                .mbReleaseGroupId(coalesce(existing.mbReleaseGroupId(), partial.mbReleaseGroupId()))
                .mbArtistId(coalesce(existing.mbArtistId(), partial.mbArtistId()))
                .isrc(coalesce(existing.isrc(), partial.isrc()))
                .barcode(coalesce(existing.barcode(), partial.barcode()))
                .catalogNumber(coalesce(existing.catalogNumber(), partial.catalogNumber()))
                .discogsReleaseId(coalesce(existing.discogsReleaseId(), partial.discogsReleaseId()))
                .durationMs(coalesce(existing.durationMs(), partial.durationMs()))
                .bitrateKbps(coalesce(existing.bitrateKbps(), partial.bitrateKbps()))
                .sampleRateHz(coalesce(existing.sampleRateHz(), partial.sampleRateHz()))
                .vbr(coalesce(existing.vbr(), partial.vbr()))
                .bpm(coalesce(existing.bpm(), partial.bpm()))
                .musicalKey(coalesce(existing.musicalKey(), partial.musicalKey()))
                .mood(coalesce(existing.mood(), partial.mood()))
                .replayGainTrackGain(coalesce(existing.replayGainTrackGain(), partial.replayGainTrackGain()))
                .replayGainTrackPeak(coalesce(existing.replayGainTrackPeak(), partial.replayGainTrackPeak()))
                .artistInfo(coalesce(existing.artistInfo(), partial.artistInfo()))
                .coverArt(coalesce(existing.coverArt(), partial.coverArt()))
                .syncedLyrics(coalesce(existing.syncedLyrics(), partial.syncedLyrics()))
                .build();
    }

    private <T> T coalesce(T a, T b) {
        return a != null ? a : b;
    }
}