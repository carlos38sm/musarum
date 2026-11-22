package musarum.backend.playlist.dto;

// avatarUrl puede ser null si el colaborador no ha subido foto. El móvil
// cae a la inicial del nick con fondo coloreado cuando falta.
public record CollaboratorRef(Long id, String nick, String avatarUrl) {}
