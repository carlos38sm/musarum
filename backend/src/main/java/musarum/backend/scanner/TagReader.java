package musarum.backend.scanner;

import java.io.File;
import java.util.Optional;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import musarum.backend.scanner.exception.TagReadException;

@Component
class TagReader {

    private static final Logger log = LoggerFactory.getLogger(TagReader.class);

    AudioMetadata read(String audioPath) {
        try {
            File file = new File(audioPath);
            AudioFile audioFile = AudioFileIO.read(file);

            Tag tag = audioFile.getTag();
            AudioHeader header = audioFile.getAudioHeader();

            String title = readField(tag, FieldKey.TITLE);
            String artist = readField(tag, FieldKey.ARTIST);
            String album = readField(tag, FieldKey.ALBUM);
            String genre = readField(tag, FieldKey.GENRE);
            Integer trackNumber = parseTrack(readField(tag, FieldKey.TRACK));
            Integer durationMs = header.getTrackLength() * 1000;

            // ── Extras técnicos del AudioHeader ──
            Integer bitrate = parseInt(header.getBitRate());
            Integer sampleRate = parseInt(header.getSampleRate());
            Integer channels = parseChannels(header.getChannels());
            String format = header.getFormat();
            Long fileSize = file.length();

            // ── Extras del tag ──
            Integer year = parseYear(readField(tag, FieldKey.YEAR));
            Integer discNumber = parseTrack(readField(tag, FieldKey.DISC_NO));
            String composer = readField(tag, FieldKey.COMPOSER);

            return new AudioMetadata(
                    title, artist, album, genre, trackNumber, durationMs,
                    year, bitrate, sampleRate, channels, format, fileSize,
                    discNumber, composer
            );

        } catch (Exception e) {
            throw new TagReadException(audioPath, e);
        }
    }

    private String readField(Tag tag, FieldKey key) {
        if (tag == null) return null;
        String value = tag.getFirst(key);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Integer parseTrack(String track) {
        if (track == null) return null;
        String digits = track.trim().split("/")[0]; // "3/12" → "3"
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** El AudioHeader devuelve bitrate/sampleRate como String; "~128" en VBR. */
    private Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** jaudiotagger devuelve "Stereo"/"Mono"/"2"/"1"; normalizamos a Integer. */
    private Integer parseChannels(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "mono", "1" -> 1;
            case "stereo", "2" -> 2;
            default -> parseInt(raw);
        };
    }

    /** El tag YEAR puede venir como "2016" o "2016-05-12". Nos quedamos con los 4 dígitos. */
    private Integer parseYear(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.trim();
        if (digits.length() >= 4) digits = digits.substring(0, 4);
        try {
            int y = Integer.parseInt(digits);
            return (y >= 1000 && y <= 9999) ? y : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extrae la letra embebida (USLT en ID3 / LYRICS en Vorbis). Muchos MP3
     * traen letra pegada por su extractor original aunque LrcLib no la tenga
     * indexada (típico de hits latinos como "Inna - Caliente").
     */
    Optional<String> extractEmbeddedLyrics(String audioPath) {
        try {
            AudioFile audio = AudioFileIO.read(new File(audioPath));
            Tag tag = audio.getTag();
            if (tag == null) return Optional.empty();

            String lyrics = tag.getFirst(FieldKey.LYRICS);
            if (lyrics == null || lyrics.isBlank()) return Optional.empty();
            return Optional.of(lyrics);
        } catch (Exception e) {
            log.debug("Sin letras embebidas en {}: {}", audioPath, e.getMessage());
            return Optional.empty();
        }
    }
}