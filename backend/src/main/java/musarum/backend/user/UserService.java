package musarum.backend.user;

import java.util.List;
import musarum.backend.favorite.FavoriteRepository;
import musarum.backend.playlist.PlaylistEntryRepository;
import musarum.backend.reproduction.ReproductionRepository;
import musarum.backend.shared.AvatarStorage;
import musarum.backend.user.dto.UserCreateRequest;
import musarum.backend.user.dto.UserResponse;
import musarum.backend.user.exception.NickAlreadyExistsException;
import musarum.backend.user.exception.UserNotFoundException;
import musarum.backend.user.dto.UserAuthDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AvatarStorage avatarStorage;
    private final FavoriteRepository favoriteRepository;
    private final PlaylistEntryRepository playlistEntryRepository;
    private final ReproductionRepository reproductionRepository;

    UserService(UserRepository userRepository,
                UserMapper userMapper,
                PasswordEncoder passwordEncoder,
                AvatarStorage avatarStorage,
                FavoriteRepository favoriteRepository,
                PlaylistEntryRepository playlistEntryRepository,
                ReproductionRepository reproductionRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.avatarStorage = avatarStorage;
        this.favoriteRepository = favoriteRepository;
        this.playlistEntryRepository = playlistEntryRepository;
        this.reproductionRepository = reproductionRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> searchByNick(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        int capped = Math.max(1, Math.min(limit, 25));
        return userRepository.findByNickContainingIgnoreCaseOrderByNickAsc(
                query.trim(),
                org.springframework.data.domain.PageRequest.of(0, capped)
        ).stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public User findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public User findEntityByNick(String nick) {
        return userRepository.findByNick(nick)
                .orElseThrow(() -> new UserNotFoundException(nick));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByNick(request.nick())) {
            throw new NickAlreadyExistsException(request.nick());
        }

        User user = User.builder()
                .nick(request.nick())
                .password(passwordEncoder.encode(request.password()))
                // Si el caller no indica role → MEMBER por defecto. La
                // validación de "¿puedes crear OWNERs?" vive en el controller,
                // no aquí; aquí solo persistimos lo que llega.
                .userRole(request.role() != null ? request.role() : UserRole.MEMBER)
                .build();

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserAuthDetails findAuthDetailsByNick(String nick) {
        User user = userRepository.findByNick(nick)
                .orElseThrow(() -> new UserNotFoundException(nick));
        return new UserAuthDetails(
                user.getId(),
                user.getNick(),
                user.getPassword(),
                user.getUserRole(),
                user.isBlocked()
        );
    }

    @Transactional
    public UserResponse updateNick(Long userId, String newNick) {
        String trimmed = newNick.trim();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (!user.getNick().equalsIgnoreCase(trimmed)
                && userRepository.existsByNick(trimmed)) {
            throw new NickAlreadyExistsException(trimmed);
        }
        user.setNick(trimmed);
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Cambia la contraseña del usuario. Falla con IllegalArgumentException si
     * la actual no coincide. El GlobalExceptionHandler la mapea a 400.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void createAdminIfNoneExists(String nick, String rawPassword) {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = User.builder()
                .nick(nick)
                .password(passwordEncoder.encode(rawPassword))
                .userRole(UserRole.OWNER)
                .build();
        userRepository.save(admin);
    }

    // ── Avatar ──────────────────────────────────────────────────────────────
    @Transactional
    public UserResponse setAvatar(Long userId, byte[] bytes, String mimeType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        String url = avatarStorage.saveAvatar(userId, bytes, mimeType);
        if (url == null) throw new IllegalArgumentException("No se pudo guardar la imagen");
        user.setAvatarUrl(url);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse clearAvatar(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        avatarStorage.deleteAvatar(userId);
        user.setAvatarUrl(null);
        return userMapper.toResponse(user);
    }

    // ── Admin: listado, bloqueo, borrado ────────────────────────────────────
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .sorted(java.util.Comparator.comparing(UserResponse::nick, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public UserResponse setBlocked(Long userId, boolean blocked, Long requesterId) {
        if (userId.equals(requesterId)) {
            throw new IllegalArgumentException("No puedes bloquearte a ti mismo");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setBlocked(blocked);
        return userMapper.toResponse(user);
    }

    /**
     * Borra el usuario y sus referencias (favoritos, entradas de playlist
     * ajenas, reproducciones) para no romper las FK. Las playlists donde el
     * usuario sea owner las dejamos huérfanas de borrar — una limpieza más
     * agresiva vendría cuando toque "borrar playlists ajenas". Protecciones:
     *   - No puedes borrarte a ti mismo.
     *   - No puedes borrar al último OWNER (siempre debe quedar al menos uno).
     */
    @Transactional
    public void delete(Long userId, Long requesterId) {
        if (userId.equals(requesterId)) {
            throw new IllegalArgumentException("No puedes borrarte a ti mismo");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getUserRole() == UserRole.OWNER) {
            long owners = userRepository.findAll().stream()
                    .filter(u -> u.getUserRole() == UserRole.OWNER)
                    .count();
            if (owners <= 1) {
                throw new IllegalArgumentException("No puedes borrar al último OWNER");
            }
        }

        favoriteRepository.deleteAll(
                favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId));
        // reproducciones: no tenemos deleteByUserId directo, pero el stats
        // service solo lee; para FK, usamos query derivada por JPA. Si no
        // existe, se puede recorrer y borrar manual. Añadimos el shortcut:
        reproductionRepository.deleteAll(
                reproductionRepository.findAll().stream()
                        .filter(r -> java.util.Objects.equals(r.getUserId(), userId))
                        .toList()
        );
        avatarStorage.deleteAvatar(userId);
        userRepository.delete(user);
    }
}