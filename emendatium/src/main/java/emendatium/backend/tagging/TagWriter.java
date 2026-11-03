package emendatium.backend.tagging;

import emendatium.backend.shared.TrackMetadata;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldDataInvalidException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Frames;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.id3.framebody.FrameBodyUSLT;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Escribe metadatos sobre el fichero de audio. Usa la API genérica de
 * jaudiotagger (FieldKey.*) para que funcione sobre MP3, FLAC, M4A y OGG.
 *
 * Los identificadores propios de Emendatium (MBIDs, AcoustID, ISRC) se
 * escriben como TXXX solo cuando el tag es ID3v24; en otros formatos se
 * omiten salvo que jaudiotagger ofrezca un equivalente estándar.
 *
 * Trabajamos sobre una copia temporal y reemplazamos el original de forma
 * atómica para que un fallo a mitad de escritura no corrompa el archivo.
 */
@Slf4j
@Component
public class TagWriter {

    public void write(Path audio, TrackMetadata metadata) {
        if (metadata == null) return;
        Path temp = null;
        try {
            temp = Files.createTempFile("emendatium_", suffixOf(audio));
            Files.copy(audio, temp, StandardCopyOption.REPLACE_EXISTING);

            AudioFile af = AudioFileIO.read(temp.toFile());
            Tag tag = af.getTagOrCreateAndSetDefault();

            setField(tag, FieldKey.TITLE,        metadata.title());
            setField(tag, FieldKey.ARTIST,       metadata.artist());
            setField(tag, FieldKey.ALBUM,        metadata.album());
            setField(tag, FieldKey.ALBUM_ARTIST, metadata.albumArtist());
            setField(tag, FieldKey.ISRC,         metadata.isrc());
            setField(tag, FieldKey.BARCODE,      metadata.barcode());
            setField(tag, FieldKey.CATALOG_NO,   metadata.catalogNumber());
            setField(tag, FieldKey.RECORD_LABEL, metadata.label());
            setField(tag, FieldKey.COMPOSER,     metadata.composer());

            if (metadata.year()        != null) setField(tag, FieldKey.YEAR,  metadata.year().toString());
            if (metadata.trackNumber() != null) setField(tag, FieldKey.TRACK, metadata.trackNumber().toString());
            if (metadata.trackTotal()  != null) setField(tag, FieldKey.TRACK_TOTAL, metadata.trackTotal().toString());
            if (metadata.discNumber()  != null) setField(tag, FieldKey.DISC_NO, metadata.discNumber().toString());
            if (metadata.discTotal()   != null) setField(tag, FieldKey.DISC_TOTAL, metadata.discTotal().toString());
            if (metadata.bpm()         != null) setField(tag, FieldKey.BPM, metadata.bpm().toString());
            if (metadata.musicalKey()  != null) setField(tag, FieldKey.KEY, metadata.musicalKey());

            if (metadata.genres() != null && !metadata.genres().isEmpty()) {
                setField(tag, FieldKey.GENRE, String.join("; ", metadata.genres()));
            }

            if (metadata.syncedLyrics() != null && !metadata.syncedLyrics().isBlank()) {
                if (tag instanceof ID3v24Tag id3) {
                    setUSLT(id3, metadata.syncedLyrics());
                } else {
                    setField(tag, FieldKey.LYRICS, metadata.syncedLyrics());
                }
            }

            if (tag instanceof ID3v24Tag id3) {
                setTXXX(id3, "EMENDATIUM_RECORDING_MBID", metadata.mbRecordingId());
                setTXXX(id3, "EMENDATIUM_RELEASE_MBID",   metadata.mbReleaseId());
                setTXXX(id3, "EMENDATIUM_ARTIST_MBID",    metadata.mbArtistId());
                setTXXX(id3, "EMENDATIUM_ACOUSTID",       metadata.acoustId());
            } else {
                setField(tag, FieldKey.MUSICBRAINZ_TRACK_ID,  metadata.mbRecordingId());
                setField(tag, FieldKey.MUSICBRAINZ_RELEASEID, metadata.mbReleaseId());
                setField(tag, FieldKey.MUSICBRAINZ_ARTISTID,  metadata.mbArtistId());
                setField(tag, FieldKey.ACOUSTID_ID,           metadata.acoustId());
            }

            af.setTag(tag);
            AudioFileIO.write(af);

            long tamano = Files.size(temp);
            if (tamano < 1024) {
                Files.delete(temp);
                log.warn("Archivo temporal sospechosamente pequeño (<1KB), abortado");
                return;
            }

            Files.move(temp, audio, StandardCopyOption.REPLACE_EXISTING);
            log.info("✅ Tags escritos en: {}", audio.getFileName());

        } catch (Exception e) {
            log.error("Error escribiendo tags en '{}': {}", audio.getFileName(), e.getMessage());
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (Exception ignored) {}
            }
        }
    }

    private String suffixOf(Path audio) {
        String name = audio.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(dot) : ".tmp";
    }

    private void setField(Tag tag, FieldKey key, String value) {
        if (value == null || value.isBlank()) return;
        try {
            tag.setField(key, value);
        } catch (FieldDataInvalidException | UnsupportedOperationException e) {
            log.debug("Campo {} no soportado en este formato: {}", key, e.getMessage());
        }
    }

    private void setUSLT(ID3v24Tag tag, String syncedLyrics) {
        try {
            ID3v24Frame frame = new ID3v24Frame(ID3v24Frames.FRAME_ID_UNSYNC_LYRICS);
            FrameBodyUSLT body = new FrameBodyUSLT((byte) 0, "eng", "", syncedLyrics);
            frame.setBody(body);
            tag.setFrame(frame);
        } catch (Exception e) {
            log.warn("No se pudo escribir USLT: {}", e.getMessage());
        }
    }

    private void setTXXX(ID3v24Tag tag, String description, String value) {
        if (value == null || value.isBlank()) return;
        try {
            FrameBodyTXXX body = new FrameBodyTXXX((byte) 0, description, value);
            ID3v24Frame frame = new ID3v24Frame(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO);
            frame.setBody(body);
            tag.setFrame(frame);
        } catch (Exception e) {
            log.warn("No se pudo escribir TXXX '{}': {}", description, e.getMessage());
        }
    }
}
