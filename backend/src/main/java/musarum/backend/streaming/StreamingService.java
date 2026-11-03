package musarum.backend.streaming;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import musarum.backend.song.SongService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class StreamingService {

    private final SongService songService;

    StreamingService(SongService songService) {
        this.songService = songService;
    }

    public Resource loadAudio(Long songId) {
        String audioPath = songService.getAudioPath(songId);
        Path path = Paths.get(audioPath);
        if (!Files.exists(path)) throw new AudioFileNotFoundException(songId);
        
        return new FileSystemResource(path);
    }
}