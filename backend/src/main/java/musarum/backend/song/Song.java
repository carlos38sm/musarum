package musarum.backend.song;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import musarum.backend.album.Album;
import musarum.backend.artist.Artist;
import musarum.backend.genre.Genre;
import musarum.backend.shared.BaseEntity;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@Table(name = "songs")
public class Song extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(name = "audio_path", nullable = false)
    private String audioPath;

    @Column(name = "content_hash", nullable = false, unique = true)
    private String contentHash;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "track_number")
    private Integer trackNumber;

    @Column(name = "lyrics_path")
    private String lyricsPath;

    @Column(name = "mb_id")
    private String mbId;

    private String isrc;

    private Integer bpm;

    @Column(name = "musical_key")
    private String musicalKey;

    @Column(name = "replay_gain")
    private Double replayGain;

    private Integer year;

    /** kbps. */
    private Integer bitrate;

    /** Hz. */
    @Column(name = "sample_rate")
    private Integer sampleRate;

    /** 1 = mono, 2 = estéreo, etc. */
    private Integer channels;

    /** Formato humano: "MP3", "FLAC", "M4A", "OGG". */
    private String format;

    /** Tamaño del archivo en bytes. */
    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "disc_number")
    private Integer discNumber;

    private String composer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "song_artists",
            joinColumns = @JoinColumn(name = "song_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    @Builder.Default
    private Set<Artist> artists = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "genre_id")
    private Genre genre;

    public void applyEnrichment(Integer bpm, String musicalKey, Double replayGain,
                                String lyricsPath, String mbId, String isrc) {
        if (bpm != null) this.bpm = bpm;
        if (musicalKey != null) this.musicalKey = musicalKey;
        if (replayGain != null) this.replayGain = replayGain;
        if (lyricsPath != null) this.lyricsPath = lyricsPath;
        if (mbId != null) this.mbId = mbId;
        if (isrc != null) this.isrc = isrc;
    }

    public void updateAudioPath(String newPath) {
        this.audioPath = newPath;
    }

    public void updateContentHash(String newHash) {
        this.contentHash = newHash;
    }

    public void setLyricsPath(String path) {
        this.lyricsPath = path;
    }
}