# Cómo publicar una nueva versión de Musarum

Pasos de ida y vuelta — hazlos en este orden la primera vez; en sucesivas
solo necesitas 2 y 4.

## 0. Pre-requisitos (una vez)

### 0.1. Generar el keystore Android

```bash
mkdir -p ~/.musarum
keytool -genkey -v -keystore ~/.musarum/release.jks \
  -alias musarum -keyalg RSA -keysize 2048 -validity 10950
```

Guarda **copias de seguridad del `.jks` y de la contraseña** fuera del
ordenador (nube cifrada, pendrive, lo que sea). Si lo pierdes, Google /
los usuarios ya no pueden actualizar la APK a futuro — tienen que
desinstalar y reinstalar.

### 0.2. Config local para compilar APK release en tu máquina

```bash
cp mobile/keystore.properties.example mobile/keystore.properties
# edita mobile/keystore.properties con tus contraseñas
```

### 0.3. Secrets en GitHub

Repo → *Settings → Secrets and variables → Actions* → **New repository
secret** por cada uno:

| Nombre                        | Valor                                     |
|-------------------------------|-------------------------------------------|
| `ANDROID_KEYSTORE_BASE64`     | `base64 -w 0 ~/.musarum/release.jks`      |
| `ANDROID_KEYSTORE_PASSWORD`   | la contraseña del `.jks`                  |
| `ANDROID_KEY_ALIAS`           | `musarum` (o el alias que pusiste)        |
| `ANDROID_KEY_PASSWORD`        | la contraseña de la clave (igual al store)|
| `MUSARUM_BASE_URL` (opcional) | `http://100.x.x.x:8080/api/v1/` (Tailscale) |

## 1. Publicar backend / emendatium

```bash
# Edita backend/pom.xml y súbele el <version>. Commit.
git tag backend-v1.2.0
git push origin backend-v1.2.0
```

El workflow `backend-release.yml` construye y publica
`ghcr.io/<owner>/musarum-backend:1.2.0` y `latest`. Watchtower, en el
portátil, actualiza el contenedor en los 30 min siguientes.

Para Emendatium: `emendatium-v1.2.0`.

## 2. Publicar APK Android

```bash
# Edita versionName/versionCode en mobile/app/build.gradle.kts.
# Commit.
git tag android-v1.0.3
git push origin android-v1.0.3
```

El workflow `android-release.yml`:
1. Decodifica el keystore desde el secret.
2. Compila el APK release firmado.
3. Lo adjunta a una nueva GitHub Release con changelog auto-generado.

## 3. Avisar a los 4 users (opcional)

En el `.env` del backend, actualiza:

```ini
MUSARUM_APK_LATEST=1.0.3
MUSARUM_APK_URL=https://github.com/<owner>/musarum/releases/download/android-v1.0.3/musarum-1.0.3.apk
MUSARUM_APK_MANDATORY=false
```

Reinicia solo el backend: `docker compose restart backend`. La próxima
vez que un usuario abra la app, verá el diálogo "Nueva versión
disponible".

## 4. Primera vez: levantar el stack entero

```bash
cp .env.example .env
# edita .env con tus datos (Postgres, OWNER, music path, JWT secret, etc.)
docker compose up -d
```

Deberías ver:
- `musarum-postgres` healthy
- `musarum-backend` healthy (log inicial del OwnerBootstrap)
- `musarum-emendatium` running
- `musarum-watchtower` running (polling cada 30 min)

Comprueba con `curl http://localhost:8080/api/v1/version`.

## 5. Mantenimiento

- **Ver logs**: `docker compose logs -f backend emendatium`.
- **Forzar scan**: desde la app, Perfil → "Rescanear biblioteca".
- **Limpiar letras huérfanas**: Análisis del servidor → botón.
- **Backup Postgres**: `docker exec musarum-postgres pg_dump -U $USER $DB > backup.sql`.
  (No se automatiza: historial y favoritos son prescindibles, la música vive en disco.)
