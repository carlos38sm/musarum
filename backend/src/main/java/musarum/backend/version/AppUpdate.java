package musarum.backend.version;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Singleton persistente con la última versión del APK anunciada por el OWNER.
 * Siempre hay exactamente una fila con id = 1 (creada por la migración V5).
 * El móvil la consulta vía {@code GET /version} y, si {@code latestApk} supera
 * su {@code BuildConfig.VERSION_NAME}, pide al usuario que actualice.
 */
@Entity
@Table(name = "app_updates")
class AppUpdate {

    static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "latest_apk")
    private String latestApk;

    @Column(name = "apk_url")
    private String apkUrl;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    protected AppUpdate() {}

    Long getId() { return id; }
    String getLatestApk() { return latestApk; }
    String getApkUrl() { return apkUrl; }
    boolean isMandatory() { return mandatory; }
    Instant getUpdatedAt() { return updatedAt; }
    Long getUpdatedBy() { return updatedBy; }

    void update(String latestApk, String apkUrl, boolean mandatory, Long updatedBy) {
        this.latestApk = latestApk;
        this.apkUrl = apkUrl;
        this.mandatory = mandatory;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
