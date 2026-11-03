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

import java.util.List;

@Component
class PlaylistMapper {

    PlaylistSummaryDto toSummary(Playlist playlist) {
        return new PlaylistSummaryDto(
                playlist.getId(),
                playlist.getName(),
                playlist.isPublic(),
                playlist.getEntries().size()
        );
    }

    PlaylistDetailsDto toDetails(Playlist playlist, Page<PlaylistEntry> entriesPage) {
        Page<PlaylistEntryDto> entryDtos = entriesPage.map(this::toEntryDto);

        List<CollaboratorRef> collaborators = playlist.getCollaborators().stream()
                .map(u -> new CollaboratorRef(u.getId(), u.getNick()))
                .toList();

        return new PlaylistDetailsDto(
                playlist.getId(),
                playlist.getName(),
                playlist.getDescription(),
                playlist.isPublic(),
                playlist.getOwner().getNick(),
                playlist.getOwner().getId(),
                entryDtos,
                collaborators
        );
    }

    private PlaylistEntryDto toEntryDto(PlaylistEntry entry) {
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