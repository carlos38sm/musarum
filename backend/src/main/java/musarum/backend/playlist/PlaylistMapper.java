package musarum.backend.playlist;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import musarum.backend.album.Album;
import musarum.backend.playlist.dto.CollaboratorRef;
import musarum.backend.playlist.dto.PlaylistDetailsDto;
import musarum.backend.playlist.dto.PlaylistEntryDto;
import musarum.backend.playlist.dto.PlaylistSummaryDto;
import musarum.backend.song.Song;
import musarum.backend.song.dto.ArtistRef;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class PlaylistMapper {

    /**
     * Toma la portada de álbum de cada una de las primeras 4 entries (por
     * posición) para que el cliente la pinte en mosaico 2×2. Si una entry
     * no tiene cover, el slot queda vacío y el cliente dibuja placeholder.
     */
    private List<String> firstFourCovers(Playlist playlist) {
        return playlist.getEntries().stream()
                .sorted(Comparator.comparingInt(PlaylistEntry::getPosition))
                .limit(4)
                .map(e -> {
                    Album a = e.getSong().getAlbum();
                    return a != null ? a.getCoverUrl() : null;
                })
                .toList();
    }

    PlaylistSummaryDto toSummary(Playlist playlist) {
        return new PlaylistSummaryDto(
                playlist.getId(),
                playlist.getName(),
                playlist.isPublic(),
                playlist.getEntries().size(),
                firstFourCovers(playlist)
        );
    }

    PlaylistDetailsDto toDetails(Playlist playlist, Page<PlaylistEntry> entriesPage) {
        Page<PlaylistEntryDto> entryDtos = entriesPage.map(this::toEntryDto);

        List<CollaboratorRef> collaborators = playlist.getCollaborators().stream()
                .map(u -> new CollaboratorRef(u.getId(), u.getNick()))
                .toList();

        long totalMs = 0L;
        Map<String, Integer> genreCount = new HashMap<>();
        Map<String, Integer> artistCount = new HashMap<>();
        for (PlaylistEntry e : playlist.getEntries()) {
            Song song = e.getSong();
            if (song.getDurationMs() != null) totalMs += song.getDurationMs();
            if (song.getGenre() != null && song.getGenre().getName() != null) {
                genreCount.merge(song.getGenre().getName(), 1, Integer::sum);
            }
            song.getArtists().forEach(a -> {
                if (a.getName() != null) artistCount.merge(a.getName(), 1, Integer::sum);
            });
        }
        String topGenre = genreCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
        String topArtist = artistCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
        Long durationMs = totalMs > 0L ? totalMs : null;
        Instant createdAt = playlist.getCreatedAt();

        return new PlaylistDetailsDto(
                playlist.getId(),
                playlist.getName(),
                playlist.getDescription(),
                playlist.isPublic(),
                playlist.getOwner().getNick(),
                playlist.getOwner().getId(),
                entryDtos,
                collaborators,
                firstFourCovers(playlist),
                playlist.getEntries().size(),
                durationMs,
                topGenre,
                topArtist,
                createdAt
        );
    }

    PlaylistEntryDto toEntryDto(PlaylistEntry entry) {
        Song song = entry.getSong();
        Album album = song.getAlbum();

        List<ArtistRef> artists = song.getArtists().stream()
                .map(a -> new ArtistRef(a.getId(), a.getName()))
                .toList();

        return new PlaylistEntryDto(
                entry.getId(),
                entry.getPosition(),
                song.getId(),
                song.getTitle(),
                song.getDurationMs(),
                artists,
                album != null ? album.getTitle() : null,
                album != null ? album.getCoverUrl() : null,
                song.getMusicalKey()
        );
    }
}
